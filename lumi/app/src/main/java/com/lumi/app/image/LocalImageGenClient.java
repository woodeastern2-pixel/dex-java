package com.lumi.app.image;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class LocalImageGenClient {

    @Keep
    public interface ProgressListener {
        int PHASE_LOADING = 1;
        int PHASE_DRAWING = 2;

        @Keep
        void onProgress(int phase, int percent, int step, int steps);
    }

    private static final String DEMO_MODEL = "local-demo";
    private static final String NATIVE_DEMO_MODEL = "native-demo";
    private static final int DEFAULT_SIZE = 384;
    private static final int DEFAULT_STEPS = 8;
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("lumi_local_image");
            loaded = true;
        } catch (Throwable ignored) {
        }
        NATIVE_AVAILABLE = loaded;
    }

    private final Context appContext;

    public LocalImageGenClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public String generateAndSave(String prompt, String model, String modelPath) throws IOException {
        return generateAndSave(prompt, model, modelPath, null);
    }

    public String generateAndSave(String prompt,
                                  String model,
                                  String modelPath,
                                  ProgressListener progressListener) throws IOException {
        String safePrompt = prompt == null ? "" : prompt.trim();
        if (safePrompt.isEmpty()) {
            safePrompt = "soft cinematic illustration";
        }

        String modelName = model == null ? "" : model.trim();
        String path = modelPath == null ? "" : modelPath.trim();
        if (modelName.isEmpty()) {
            modelName = DEMO_MODEL;
        }

        File outputFile = createOutputFile();
        if (isJavaDemoModel(modelName) && path.isEmpty()) {
            notifyProgress(progressListener, ProgressListener.PHASE_DRAWING, 0, 0, 1);
            writeDemoImage(safePrompt, outputFile);
            notifyProgress(progressListener, ProgressListener.PHASE_DRAWING, 100, 1, 1);
            return outputFile.getAbsolutePath();
        }

        String nativeModelPath = path.isEmpty() ? modelName : path;
        if (!NATIVE_AVAILABLE) {
            throw new IOException("기기 내장 AI native 엔진을 이 기기에서 불러올 수 없어요. 현재는 local-demo 파이프라인 테스트만 사용할 수 있어요.");
        }

        boolean nativeDemo = path.isEmpty() && isNativeDemoModel(modelName);
        File modelFile = new File(nativeModelPath);
        if (!nativeDemo && (!modelFile.exists() || !modelFile.isFile())) {
            throw new IOException("로컬 이미지 모델 파일을 찾을 수 없어요: " + nativeModelPath);
        }
        if (!nativeDemo) {
            ensureModelFitsDevice(modelFile);
        }

        int result;
        try {
            result = generateImageNative(
                    nativeDemo ? NATIVE_DEMO_MODEL : modelFile.getAbsolutePath(),
                    safePrompt,
                    outputFile.getAbsolutePath(),
                    DEFAULT_SIZE,
                    DEFAULT_SIZE,
                    DEFAULT_STEPS,
                    System.currentTimeMillis(),
                    progressListener);
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("기기 내장 AI native 함수가 아직 연결되지 않았어요", e);
        }
        if (result != 0) {
            throw new IOException(nativeErrorMessage(result));
        }
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new IOException("기기 내장 AI가 이미지 파일을 만들지 못했어요");
        }
        return outputFile.getAbsolutePath();
    }

    public String testConnection(String model, String modelPath) throws IOException {
        String modelName = model == null || model.trim().isEmpty() ? DEMO_MODEL : model.trim();
        String path = modelPath == null ? "" : modelPath.trim();
        if ((isJavaDemoModel(modelName) || isNativeDemoModel(modelName)) && path.isEmpty()) {
            String filePath = generateAndSave("simple circle icon", modelName, path);
            File file = new File(filePath);
            String engine = isJavaDemoModel(modelName) ? "local-demo" : "local-native-demo";
            return engine + "(" + readableSize(file.length()) + ")";
        }
        if (!NATIVE_AVAILABLE) {
            throw new IOException("기기 내장 AI native 엔진을 이 기기에서 불러올 수 없어요.");
        }
        String nativeModelPath = path.isEmpty() ? modelName : path;
        File modelFile = new File(nativeModelPath);
        if (!modelFile.exists() || !modelFile.isFile()) {
            throw new IOException("로컬 이미지 모델 파일을 찾을 수 없어요: " + nativeModelPath);
        }
        return "local-native-ready(" + readableSize(modelFile.length()) + ")";
    }

    private native int generateImageNative(String modelPath,
                                           String prompt,
                                           String outputPath,
                                           int width,
                                           int height,
                                           int steps,
                                           long seed,
                                           ProgressListener progressListener);

    private void notifyProgress(ProgressListener listener, int phase, int percent, int step, int steps) {
        if (listener == null) return;
        listener.onProgress(phase, Math.max(0, Math.min(100, percent)), step, steps);
    }

    private boolean isJavaDemoModel(String modelName) {
        return DEMO_MODEL.equalsIgnoreCase(modelName)
                || "demo".equalsIgnoreCase(modelName)
                || "prototype".equalsIgnoreCase(modelName);
    }

    private boolean isNativeDemoModel(String modelName) {
        return NATIVE_DEMO_MODEL.equalsIgnoreCase(modelName);
    }

    private String nativeErrorMessage(int result) {
        if (result == -100) {
            return "기기 내장 AI 엔진이 아직 연결되지 않았어요.";
        }
        if (result == -101) {
            return "기기 내장 AI 데모 이미지 저장에 실패했어요.";
        }
        if (result == -200) {
            return "기기 내장 Stable Diffusion 생성에 실패했어요. 모델 파일 형식, 기기 메모리, 모델 경로를 확인해 주세요.";
        }
        return "기기 내장 AI 이미지 생성 실패: " + result;
    }

    private void ensureModelFitsDevice(File modelFile) throws IOException {
        long modelBytes = modelFile.length();
        if (modelBytes < 5L * GIB) {
            return;
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return;
        }
        activityManager.getMemoryInfo(memoryInfo);
        if (memoryInfo.totalMem > 0 && memoryInfo.totalMem < 10L * GIB) {
            throw new IOException("이 기기 메모리로는 SDXL급 대형 모델을 안정적으로 실행하기 어려워요. SD 1.5 계열이나 quantized GGUF 모델을 먼저 사용해 주세요.");
        }
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private File createOutputFile() throws IOException {
        File dir = new File(appContext.getCacheDir(), "lumi_images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("이미지 캐시 폴더를 만들 수 없어요");
        }
        pruneOld(dir, 7L * 24 * 60 * 60 * 1000);
        return new File(dir, "local_img_" + System.currentTimeMillis() + ".png");
    }

    private void writeDemoImage(String prompt, File outputFile) throws IOException {
        int[] palette = paletteFor(prompt);
        Bitmap bitmap = Bitmap.createBitmap(DEFAULT_SIZE, DEFAULT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LinearGradient gradient = new LinearGradient(
                0,
                0,
                DEFAULT_SIZE,
                DEFAULT_SIZE,
                palette[0],
                palette[1],
                Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, DEFAULT_SIZE, DEFAULT_SIZE, paint);
        paint.setShader(null);

        for (int i = 0; i < 9; i++) {
            paint.setColor(withAlpha(palette[(i + 2) % palette.length], 72 + (i * 12) % 96));
            float left = (palette[i % palette.length] & 0xff) * 1.6f % DEFAULT_SIZE;
            float top = ((palette[(i + 1) % palette.length] >> 8) & 0xff) * 1.6f % DEFAULT_SIZE;
            float size = 90 + ((palette[(i + 2) % palette.length] >> 16) & 0xff) % 170;
            canvas.drawOval(new RectF(left - size / 2, top - size / 2, left + size, top + size), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(withAlpha(Color.WHITE, 180));
        canvas.drawRoundRect(new RectF(28, 28, DEFAULT_SIZE - 28, DEFAULT_SIZE - 28), 42, 42, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextSize(30f);
        paint.setColor(Color.WHITE);
        canvas.drawText("Lumi local-demo", 46, DEFAULT_SIZE - 84, paint);

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setTextSize(18f);
        paint.setColor(withAlpha(Color.WHITE, 210));
        canvas.drawText(shorten(prompt, 42), 46, DEFAULT_SIZE - 50, paint);

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("로컬 데모 이미지를 저장할 수 없어요");
            }
        } finally {
            bitmap.recycle();
        }
    }

    private int[] paletteFor(String prompt) throws IOException {
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            digest = md.digest(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("로컬 팔레트 생성 실패", e);
        }
        int[] colors = new int[6];
        for (int i = 0; i < colors.length; i++) {
            int offset = i * 3;
            int red = 48 + (digest[offset] & 0xff) % 170;
            int green = 48 + (digest[offset + 1] & 0xff) % 170;
            int blue = 48 + (digest[offset + 2] & 0xff) % 170;
            colors[i] = Color.rgb(red, green, blue);
        }
        return colors;
    }

    private int withAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00ffffff) | (safeAlpha << 24);
    }

    private String shorten(String text, int maxChars) {
        String normalized = text == null ? "" : text.trim().replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= maxChars) return normalized;
        return normalized.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private void pruneOld(File dir, long maxAgeMs) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - maxAgeMs;
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.US);
                if (file.isFile() && name.startsWith("local_img_") && file.lastModified() < cutoff) {
                    file.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}