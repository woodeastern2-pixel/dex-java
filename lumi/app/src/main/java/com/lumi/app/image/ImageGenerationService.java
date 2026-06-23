package com.lumi.app.image;

import android.content.Context;

import com.lumi.app.data.LumiSettings;

public class ImageGenerationService {

    private final ImagePromptBuilder promptBuilder;
    private final ImagePromptTranslator promptTranslator;
    private final ImageSafetyChecker safetyChecker;
    private final ImageGenClient imageGenClient;
    private final LumiSettings settings;

    public ImageGenerationService(Context context, LumiSettings settings) {
        this.promptBuilder = new ImagePromptBuilder();
        this.promptTranslator = new ImagePromptTranslator();
        this.safetyChecker = new ImageSafetyChecker();
        this.imageGenClient = new ImageGenClient(context.getApplicationContext(), settings);
        this.settings = settings;
    }

    public ImageGenerationResult generate(String userText) {
        if (settings == null || !settings.isImageGenEnabled()) {
            return ImageGenerationResult.fail(
                    "이미지 생성 기능이 꺼져 있어요. 설정에서 유료 이미지 API를 활성화해 주세요.",
                    "image_gen_disabled",
                    userText == null ? "" : userText);
        }

        String prompt = promptBuilder.buildPrompt(userText);
        String provider = settings.getImageGenProvider();
        String apiKey = settings.getImageGenApiKey();
        String model = settings.getImageGenModel();
        String baseUrl = settings.getImageGenBaseUrl();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ImageGenerationResult.fail(
                    "이미지 생성 API 키가 없어요. 설정에서 유료 이미지 API를 먼저 입력해 주세요.",
                    "missing_image_api_key",
                    prompt);
        }

        if (LumiSettings.IMAGE_GEN_PROVIDER_OPENAI_COMPATIBLE.equals(provider)
                && (baseUrl == null || baseUrl.trim().isEmpty())) {
            return ImageGenerationResult.fail(
                    "OpenAI 호환 이미지 API는 엔드포인트 URL이 필요해요.",
                    "missing_image_base_url",
                    prompt);
        }

        boolean nsfwAllowed = settings != null && settings.isImageGenNsfwEnabled();
        if (!safetyChecker.canProceed(prompt, nsfwAllowed)) {
            return ImageGenerationResult.fail(
                    "NSFW 설정이 꺼져 있어 요청한 이미지를 생성할 수 없어요.",
                    "blocked_by_nsfw_policy",
                    prompt);
        }

        String generationPrompt = promptTranslator.translateToEnglishIfNeeded(prompt, settings);

        try {
            String filePath = imageGenClient.generateAndSave(generationPrompt, provider, apiKey, model, baseUrl);
            return ImageGenerationResult.ok(filePath, "완성했어요. 마음에 들면 좋겠어요.", generationPrompt);
        } catch (Throwable t) {
            return ImageGenerationResult.fail(
                    userFacingError(t),
                    t.getMessage(),
                    generationPrompt);
        }
    }

    private String userFacingError(Throwable t) {
        String message = t == null ? "" : t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "이미지를 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
        }
        String normalized = message.trim();
        if (normalized.startsWith("FAL ") || normalized.startsWith("FAL이") || normalized.startsWith("FAL 계정")) {
            return normalized;
        }
        if (normalized.contains("API 키") || normalized.contains("엔드포인트 URL")) {
            return normalized;
        }
        if (normalized.startsWith("이미지 ")
                || normalized.startsWith("JSON ")
                || normalized.startsWith("응답")) {
            return normalized;
        }
        return "이미지를 만드는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
    }
}