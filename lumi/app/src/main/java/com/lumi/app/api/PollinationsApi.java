package com.lumi.app.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pollinations.ai API를 통한 이미지 생성
 */
public class PollinationsApi {

    private static final String API_URL = "https://image.pollinations.ai/prompt/";

    /**
     * 텍스트 프롬프트로부터 이미지를 생성합니다
     * @param prompt 이미지 생성 프롬프트
     * @return 생성된 이미지의 URL
     */
    public static String generateImage(String prompt) throws Exception {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        // Pollinations.ai는 프롬프트를 URL 매개변수로 받습니다
        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8.name());
        String imageUrl = API_URL + encodedPrompt;

        // 실제로는 URL을 직접 반환합니다
        // Pollinations.ai는 이미지를 자동으로 캐시하거나 생성합니다
        return imageUrl;
    }
}
