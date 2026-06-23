package com.lumi.app.image;

public class ImagePromptBuilder {

    public String buildPrompt(String userText) {
        String raw = userText == null ? "" : userText.trim();
        if (raw.isEmpty()) return "soft cinematic illustration";

        String cleaned = raw
                .replace("이미지를 만들어줘", "")
                .replace("이미지 만들어줘", "")
                .replace("그림 그려줘", "")
                .replace("그림 그려주라", "")
                .replace("그림 그려주세요", "")
                .replace("그려달라", "")
                .replace("그려달라고", "")
                .replace("사진처럼 생성해줘", "")
                .replace("일러스트 만들어줘", "")
                .replace("배경화면 만들어줘", "")
                .replace("캐릭터 그려줘", "")
                .replace("로고 만들어줘", "")
                .trim();

        if (cleaned.isEmpty()) {
            cleaned = raw;
        }

        // 과한 확장은 피하고, 구도/품질 키워드만 최소 보강.
        return cleaned + ", highly detailed, natural lighting, clean composition";
    }
}