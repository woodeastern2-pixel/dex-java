#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdlib>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "stable-diffusion.h"
#include <zlib.h>

#define LUMI_LOG_TAG "LumiLocalImage"

namespace {

constexpr uint32_t kPngWidthMax = 1024;
constexpr uint32_t kPngHeightMax = 1024;
constexpr int kSdMaxMobileSize = 768;
constexpr int kProgressPhaseLoading = 1;
constexpr int kProgressPhaseDrawing = 2;

struct SdProgressBridge {
    JavaVM *vm = nullptr;
    jobject listener = nullptr;
    jmethodID on_progress = nullptr;
    int phase = kProgressPhaseLoading;
    int last_percent = -1;
};

uint32_t fnv1a(const char *text) {
    uint32_t hash = 2166136261u;
    if (text == nullptr) {
        return hash;
    }
    while (*text != '\0') {
        hash ^= static_cast<uint8_t>(*text++);
        hash *= 16777619u;
    }
    return hash;
}

void append_u32(std::vector<uint8_t> &out, uint32_t value) {
    out.push_back(static_cast<uint8_t>((value >> 24) & 0xff));
    out.push_back(static_cast<uint8_t>((value >> 16) & 0xff));
    out.push_back(static_cast<uint8_t>((value >> 8) & 0xff));
    out.push_back(static_cast<uint8_t>(value & 0xff));
}

void append_chunk(std::vector<uint8_t> &png, const char type[4], const std::vector<uint8_t> &data) {
    append_u32(png, static_cast<uint32_t>(data.size()));
    size_t type_offset = png.size();
    png.push_back(static_cast<uint8_t>(type[0]));
    png.push_back(static_cast<uint8_t>(type[1]));
    png.push_back(static_cast<uint8_t>(type[2]));
    png.push_back(static_cast<uint8_t>(type[3]));
    png.insert(png.end(), data.begin(), data.end());

    uLong crc = crc32(0L, Z_NULL, 0);
    crc = crc32(crc, png.data() + type_offset, static_cast<uInt>(4 + data.size()));
    append_u32(png, static_cast<uint32_t>(crc));
}

bool write_native_demo_png(const char *prompt, const char *output_path, int requested_width, int requested_height) {
    if (output_path == nullptr || output_path[0] == '\0') {
        return false;
    }

    uint32_t width = requested_width > 0 ? static_cast<uint32_t>(requested_width) : 512u;
    uint32_t height = requested_height > 0 ? static_cast<uint32_t>(requested_height) : 512u;
    if (width > kPngWidthMax) width = kPngWidthMax;
    if (height > kPngHeightMax) height = kPngHeightMax;

    uint32_t seed = fnv1a(prompt);
    uint8_t base_r = static_cast<uint8_t>(64 + (seed & 0x7f));
    uint8_t base_g = static_cast<uint8_t>(64 + ((seed >> 8) & 0x7f));
    uint8_t base_b = static_cast<uint8_t>(64 + ((seed >> 16) & 0x7f));
    uint8_t accent_r = static_cast<uint8_t>(96 + ((seed >> 3) & 0x7f));
    uint8_t accent_g = static_cast<uint8_t>(96 + ((seed >> 11) & 0x7f));
    uint8_t accent_b = static_cast<uint8_t>(96 + ((seed >> 19) & 0x7f));

    std::vector<uint8_t> raw;
    raw.reserve((width * 4 + 1) * height);
    for (uint32_t y = 0; y < height; ++y) {
        raw.push_back(0);
        for (uint32_t x = 0; x < width; ++x) {
            float fx = width <= 1 ? 0.0f : static_cast<float>(x) / static_cast<float>(width - 1);
            float fy = height <= 1 ? 0.0f : static_cast<float>(y) / static_cast<float>(height - 1);
            uint8_t wave = static_cast<uint8_t>((x * 13u + y * 7u + seed) & 0x3f);
            uint8_t r = static_cast<uint8_t>(base_r * (1.0f - fx) + accent_r * fx);
            uint8_t g = static_cast<uint8_t>(base_g * (1.0f - fy) + accent_g * fy);
            uint8_t b = static_cast<uint8_t>((base_b + accent_b) / 2 + wave);
            raw.push_back(r);
            raw.push_back(g);
            raw.push_back(b);
            raw.push_back(255);
        }
    }

    uLongf compressed_size = compressBound(static_cast<uLong>(raw.size()));
    std::vector<uint8_t> compressed(compressed_size);
    int z_result = compress2(
            compressed.data(),
            &compressed_size,
            raw.data(),
            static_cast<uLong>(raw.size()),
            Z_BEST_SPEED);
    if (z_result != Z_OK) {
        return false;
    }
    compressed.resize(compressed_size);

    std::vector<uint8_t> png;
    const uint8_t signature[] = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    png.insert(png.end(), signature, signature + sizeof(signature));

    std::vector<uint8_t> ihdr;
    append_u32(ihdr, width);
    append_u32(ihdr, height);
    ihdr.push_back(8);
    ihdr.push_back(6);
    ihdr.push_back(0);
    ihdr.push_back(0);
    ihdr.push_back(0);
    append_chunk(png, "IHDR", ihdr);
    append_chunk(png, "IDAT", compressed);
    append_chunk(png, "IEND", std::vector<uint8_t>());

    FILE *file = std::fopen(output_path, "wb");
    if (file == nullptr) {
        return false;
    }
    size_t written = std::fwrite(png.data(), 1, png.size(), file);
    std::fclose(file);
    return written == png.size();
}

bool write_rgba_png(const std::vector<uint8_t> &rgba, uint32_t width, uint32_t height, const char *output_path) {
    if (output_path == nullptr || output_path[0] == '\0' || rgba.empty() || width == 0 || height == 0) {
        return false;
    }

    std::vector<uint8_t> raw;
    raw.reserve((width * 4 + 1) * height);
    for (uint32_t y = 0; y < height; ++y) {
        raw.push_back(0);
        const size_t row_offset = static_cast<size_t>(y) * width * 4;
        raw.insert(raw.end(), rgba.begin() + row_offset, rgba.begin() + row_offset + width * 4);
    }

    uLongf compressed_size = compressBound(static_cast<uLong>(raw.size()));
    std::vector<uint8_t> compressed(compressed_size);
    int z_result = compress2(
            compressed.data(),
            &compressed_size,
            raw.data(),
            static_cast<uLong>(raw.size()),
            Z_BEST_SPEED);
    if (z_result != Z_OK) {
        return false;
    }
    compressed.resize(compressed_size);

    std::vector<uint8_t> png;
    const uint8_t signature[] = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    png.insert(png.end(), signature, signature + sizeof(signature));

    std::vector<uint8_t> ihdr;
    append_u32(ihdr, width);
    append_u32(ihdr, height);
    ihdr.push_back(8);
    ihdr.push_back(6);
    ihdr.push_back(0);
    ihdr.push_back(0);
    ihdr.push_back(0);
    append_chunk(png, "IHDR", ihdr);
    append_chunk(png, "IDAT", compressed);
    append_chunk(png, "IEND", std::vector<uint8_t>());

    FILE *file = std::fopen(output_path, "wb");
    if (file == nullptr) {
        return false;
    }
    size_t written = std::fwrite(png.data(), 1, png.size(), file);
    std::fclose(file);
    return written == png.size();
}

bool write_sd_image_png(const sd_image_t &image, const char *output_path) {
    if (image.data == nullptr || image.width == 0 || image.height == 0 || image.channel == 0) {
        return false;
    }
    std::vector<uint8_t> rgba;
    rgba.reserve(static_cast<size_t>(image.width) * image.height * 4);
    for (uint32_t i = 0; i < image.width * image.height; ++i) {
        const uint8_t *pixel = image.data + static_cast<size_t>(i) * image.channel;
        if (image.channel == 1) {
            rgba.push_back(pixel[0]);
            rgba.push_back(pixel[0]);
            rgba.push_back(pixel[0]);
            rgba.push_back(255);
        } else if (image.channel == 3) {
            rgba.push_back(pixel[0]);
            rgba.push_back(pixel[1]);
            rgba.push_back(pixel[2]);
            rgba.push_back(255);
        } else {
            rgba.push_back(pixel[0]);
            rgba.push_back(pixel[1]);
            rgba.push_back(pixel[2]);
            rgba.push_back(pixel[3]);
        }
    }
    return write_rgba_png(rgba, image.width, image.height, output_path);
}

void sd_log_callback(enum sd_log_level_t level, const char *text, void *) {
    int priority = ANDROID_LOG_INFO;
    if (level == SD_LOG_ERROR) {
        priority = ANDROID_LOG_ERROR;
    } else if (level == SD_LOG_WARN) {
        priority = ANDROID_LOG_WARN;
    } else if (level == SD_LOG_DEBUG) {
        priority = ANDROID_LOG_DEBUG;
    }
    __android_log_print(priority, LUMI_LOG_TAG, "%s", text == nullptr ? "" : text);
}

void notify_java_progress(SdProgressBridge *bridge, int phase, int percent, int step, int steps) {
    if (bridge == nullptr || bridge->vm == nullptr || bridge->listener == nullptr || bridge->on_progress == nullptr) {
        return;
    }

    JNIEnv *env = nullptr;
    bool detach = false;
    jint env_result = bridge->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (env_result == JNI_EDETACHED) {
        if (bridge->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        detach = true;
    } else if (env_result != JNI_OK || env == nullptr) {
        return;
    }

    env->CallVoidMethod(bridge->listener, bridge->on_progress, phase, percent, step, steps);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        bridge->on_progress = nullptr;
    }
    if (detach) {
        bridge->vm->DetachCurrentThread();
    }
}

void sd_progress_callback(int step, int steps, float time, void *data) {
    __android_log_print(
            ANDROID_LOG_INFO,
            LUMI_LOG_TAG,
            "stable-diffusion progress: %d/%d %.2fs",
            step,
            steps,
            time);
    auto *bridge = static_cast<SdProgressBridge *>(data);
    if (bridge == nullptr) {
        return;
    }
    int safe_steps = std::max(steps, 1);
    int safe_step = std::max(0, std::min(step, safe_steps));
    int percent = static_cast<int>((static_cast<int64_t>(safe_step) * 100) / safe_steps);
    percent = std::max(0, std::min(percent, 100));
    if (bridge->last_percent == percent) {
        return;
    }
    bridge->last_percent = percent;
    notify_java_progress(bridge, bridge->phase, percent, safe_step, safe_steps);
}

int clamp_generation_size(int value) {
    int safe = value > 0 ? value : 512;
    safe = std::min(safe, kSdMaxMobileSize);
    safe = std::max(256, safe);
    safe = (safe / 64) * 64;
    return safe <= 0 ? 512 : safe;
}

bool generate_stable_diffusion_image(
        const char *model_path,
        const char *prompt,
        const char *output_path,
        int requested_width,
        int requested_height,
        int requested_steps,
        int64_t seed,
        SdProgressBridge *progress_bridge) {
    if (model_path == nullptr || model_path[0] == '\0' || output_path == nullptr || output_path[0] == '\0') {
        return false;
    }

    sd_set_log_callback(sd_log_callback, nullptr);
    sd_set_progress_callback(sd_progress_callback, progress_bridge);
    if (progress_bridge != nullptr) {
        progress_bridge->phase = kProgressPhaseLoading;
        progress_bridge->last_percent = -1;
        notify_java_progress(progress_bridge, kProgressPhaseLoading, 0, 0, 1);
    }

    sd_ctx_params_t ctx_params;
    sd_ctx_params_init(&ctx_params);
    ctx_params.model_path = model_path;
    ctx_params.vae_decode_only = false;
    ctx_params.free_params_immediately = true;
    ctx_params.n_threads = std::min(std::max(sd_get_num_physical_cores(), 1), 2);
    ctx_params.rng_type = CPU_RNG;
    ctx_params.sampler_rng_type = CPU_RNG;
    ctx_params.enable_mmap = true;
    ctx_params.keep_clip_on_cpu = true;
    ctx_params.keep_vae_on_cpu = true;

    sd_ctx_t *ctx = new_sd_ctx(&ctx_params);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LUMI_LOG_TAG, "new_sd_ctx failed for %s", model_path);
        sd_set_progress_callback(sd_progress_callback, nullptr);
        return false;
    }

    if (progress_bridge != nullptr) {
        progress_bridge->phase = kProgressPhaseDrawing;
        progress_bridge->last_percent = -1;
        notify_java_progress(progress_bridge, kProgressPhaseDrawing, 0, 0, 1);
    }

    bool ok = false;
    sd_image_t *images = nullptr;
    do {
        if (!sd_ctx_supports_image_generation(ctx)) {
            __android_log_print(ANDROID_LOG_ERROR, LUMI_LOG_TAG, "model does not support image generation");
            break;
        }

        sd_img_gen_params_t params;
        sd_img_gen_params_init(&params);
        params.prompt = prompt == nullptr || prompt[0] == '\0' ? "soft cinematic illustration" : prompt;
        params.negative_prompt = "low quality, blurry, distorted, watermark, text, extra limbs";
        params.width = clamp_generation_size(requested_width);
        params.height = clamp_generation_size(requested_height);
        params.seed = seed;
        params.batch_count = 1;
        params.sample_params.sample_steps = requested_steps > 0 ? std::min(requested_steps, 16) : 8;
        params.sample_params.sample_method = sd_get_default_sample_method(ctx);
        params.sample_params.scheduler = sd_get_default_scheduler(ctx, params.sample_params.sample_method);

        images = generate_image(ctx, &params);
        if (images == nullptr || images[0].data == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, LUMI_LOG_TAG, "generate_image returned no image");
            break;
        }
        ok = write_sd_image_png(images[0], output_path);
    } while (false);

    if (images != nullptr) {
        if (images[0].data != nullptr) {
            std::free(images[0].data);
        }
        std::free(images);
    }
    free_sd_ctx(ctx);
    if (ok && progress_bridge != nullptr) {
        notify_java_progress(progress_bridge, kProgressPhaseDrawing, 100, 1, 1);
    }
    sd_set_progress_callback(sd_progress_callback, nullptr);
    return ok;
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_lumi_app_image_LocalImageGenClient_generateImageNative(
        JNIEnv *env,
        jobject /* thiz */,
        jstring modelPath,
        jstring prompt,
        jstring outputPath,
        jint width,
        jint height,
        jint steps,
        jlong seed,
        jobject progressListener) {
    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char *prompt_text = env->GetStringUTFChars(prompt, nullptr);
    const char *output_path = env->GetStringUTFChars(outputPath, nullptr);

    SdProgressBridge progress_bridge;
    SdProgressBridge *progress_bridge_ptr = nullptr;
    if (progressListener != nullptr) {
        env->GetJavaVM(&progress_bridge.vm);
        progress_bridge.listener = env->NewGlobalRef(progressListener);
        jclass listener_class = env->GetObjectClass(progressListener);
        if (listener_class != nullptr) {
            progress_bridge.on_progress = env->GetMethodID(listener_class, "onProgress", "(IIII)V");
            env->DeleteLocalRef(listener_class);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        if (progress_bridge.vm != nullptr && progress_bridge.listener != nullptr && progress_bridge.on_progress != nullptr) {
            progress_bridge_ptr = &progress_bridge;
        }
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            LUMI_LOG_TAG,
            "native image bridge called: model=%s output=%s size=%dx%d steps=%d seed=%lld prompt=%s",
            model_path == nullptr ? "" : model_path,
            output_path == nullptr ? "" : output_path,
            width,
            height,
            steps,
            static_cast<long long>(seed),
            prompt_text == nullptr ? "" : prompt_text);

    bool is_native_demo = model_path != nullptr && std::strcmp(model_path, "native-demo") == 0;
    bool wrote_image = false;
    if (is_native_demo) {
        if (progress_bridge_ptr != nullptr) {
            notify_java_progress(progress_bridge_ptr, kProgressPhaseDrawing, 0, 0, 1);
        }
        wrote_image = write_native_demo_png(prompt_text, output_path, width, height);
        if (wrote_image && progress_bridge_ptr != nullptr) {
            notify_java_progress(progress_bridge_ptr, kProgressPhaseDrawing, 100, 1, 1);
        }
    } else {
        wrote_image = generate_stable_diffusion_image(
                model_path,
                prompt_text,
                output_path,
                width,
                height,
                steps,
                static_cast<int64_t>(seed),
                progress_bridge_ptr);
    }

    if (model_path != nullptr) {
        env->ReleaseStringUTFChars(modelPath, model_path);
    }
    if (prompt_text != nullptr) {
        env->ReleaseStringUTFChars(prompt, prompt_text);
    }
    if (output_path != nullptr) {
        env->ReleaseStringUTFChars(outputPath, output_path);
    }
    if (progress_bridge.listener != nullptr) {
        env->DeleteGlobalRef(progress_bridge.listener);
    }

    if (is_native_demo) {
        return wrote_image ? 0 : -101;
    }
    return wrote_image ? 0 : -200;
}