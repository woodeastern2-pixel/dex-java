package com.lumi.app.image;

import android.util.Log;

import com.lumi.app.data.LumiSettings;
import com.lumi.app.engine.LlmClient;
import com.lumi.app.engine.PromptBuilder;

import java.util.ArrayList;
import java.util.List;

public class ImagePromptTranslator {
    private static final String TAG = "ImagePromptTranslator";
    private static final int MAX_PROMPT_CHARS = 900;
    private static final int MAX_TRANSLATION_TOKENS = 500;
    private static final double TRANSLATION_TEMPERATURE = 0.2;
    private static final String FALLBACK_TRANSLATION_MODEL = "gpt-4o-mini";

    private final LlmClient llmClient = new LlmClient();

    public String translateToEnglishIfNeeded(String prompt, LumiSettings settings) {
        String original = prompt == null ? "" : prompt.trim();
        if (original.isEmpty() || !containsKorean(original)) {
            return original;
        }

        try {
            String translated = requestTranslation(settings, buildSystemPrompt(), original);
            String sanitized = sanitize(translated);
            if (sanitized.isEmpty()) {
                return original;
            }
            if (containsKoreanOutsideQuotedText(sanitized)) {
                String retried = requestTranslation(settings, buildRetrySystemPrompt(), original + "\n\nPrevious output:\n" + sanitized);
                String retrySanitized = sanitize(retried);
                if (!retrySanitized.isEmpty()) {
                    return retrySanitized;
                }
            }
            return sanitized;
        } catch (Throwable throwable) {
            Log.w(TAG, "Image prompt translation failed: " + throwable.getMessage());
            return original;
        }
    }

    private String requestTranslation(LumiSettings settings, String systemPrompt, String userContent)
            throws LlmClient.LlmException {
        List<PromptBuilder.Turn> turns = new ArrayList<>();
        turns.add(new PromptBuilder.Turn("user", userContent));
        if (canUseLlm(settings)) {
            return llmClient.complete(settings, systemPrompt, turns, TRANSLATION_TEMPERATURE, MAX_TRANSLATION_TOKENS);
        }
        if (canUseOpenAiImageKeyForTranslation(settings)) {
            return llmClient.completeOpenAi(
                    settings.getImageGenApiKey(),
                    FALLBACK_TRANSLATION_MODEL,
                    systemPrompt,
                    turns,
                    TRANSLATION_TEMPERATURE,
                    MAX_TRANSLATION_TOKENS);
        }
        return "";
    }

    private boolean canUseLlm(LumiSettings settings) {
        if (settings == null || !settings.isRemoteEnabled()) {
            return false;
        }
        String apiKey = settings.getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private boolean canUseOpenAiImageKeyForTranslation(LumiSettings settings) {
        if (settings == null || !settings.isImageGenEnabled()) {
            return false;
        }
        if (!LumiSettings.IMAGE_GEN_PROVIDER_OPENAI.equals(settings.getImageGenProvider())) {
            return false;
        }
        String apiKey = settings.getImageGenApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private boolean containsKorean(String text) {
        if (text == null) return false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= '\uAC00' && character <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }

    private String buildSystemPrompt() {
        return "Translate the user's Korean image generation request into one polished English image prompt. "
            + "Output only the final prompt, with no label, no explanation, no markdown, and no alternatives. "
            + "Do not wrap the whole prompt in quotes. "
            + "Translate every Korean word into natural English except text the user explicitly wants visible in the image. "
            + "Keep visible text exactly as written inside quotes. "
            + "Preserve the user's subject, setting, composition, colors, mood, style, camera angle, and constraints. "
            + "If the user asks for visible text, preserve that exact text in its original language inside quotes. "
            + "do not make adult content."
                + "Keep it concise and useful for Stable Diffusion, Flux, DALL-E, or OpenAI-compatible image models.";
                
    }

    private String buildRetrySystemPrompt() {
        return "Rewrite the user's image generation request as one English image prompt. "
                + "The previous output may still contain Korean; translate all remaining Korean words to English. "
                + "Output only the corrected prompt, with no label, no explanation, no markdown, and no alternatives. "
                + "Keep text that must be visibly rendered in the image exactly as written inside quotes. "
                + "Do not add details the user did not request.";
    }

    private String sanitize(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        text = text.replace("```", "").trim();
        text = text.replace('\r', '\n');
        text = text.replaceAll("\\n+", ", ").trim();
        text = text.replaceFirst("(?i)^here\\s+is\\s+(an?\\s+)?(english\\s+)?(image\\s+)?prompt\\s*[:：-]\\s*", "").trim();
        text = text.replaceFirst("(?i)^(english\\s+)?(image\\s+)?prompt\\s*[:：-]\\s*", "").trim();
        text = text.replaceFirst("(?i)^translation\\s*[:：-]\\s*", "").trim();
        text = text.replaceFirst("^[-*]\\s+", "").trim();
        text = text.replaceFirst("^\\d+[.)]\\s+", "").trim();
        text = stripMatchingQuotes(text);
        if (text.length() > MAX_PROMPT_CHARS) {
            text = text.substring(0, MAX_PROMPT_CHARS).trim();
        }
        return text;
    }

    private String stripMatchingQuotes(String text) {
        String trimmed = text == null ? "" : text.trim();
        while (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }

    private boolean containsKoreanOutsideQuotedText(String text) {
        if (text == null) return false;
        boolean insideQuote = false;
        char quoteCharacter = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character == '"' || character == '\'') && (index == 0 || text.charAt(index - 1) != '\\')) {
                if (!insideQuote) {
                    insideQuote = true;
                    quoteCharacter = character;
                } else if (quoteCharacter == character) {
                    insideQuote = false;
                    quoteCharacter = 0;
                }
                continue;
            }
            if (!insideQuote && character >= '\uAC00' && character <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }
}