package com.lumi.app.image;

import java.util.Locale;

public final class ImageRequestDetector {
    private ImageRequestDetector() {
    }

    public static boolean isImageGenerationRequest(String text) {
        if (text == null) return false;
        String t = normalize(text);
        String[] keywords = new String[] {
                "이미지만들", "이미지생성", "이미지그려",
                "그림그려", "그림을그려", "그림만들", "일러스트만들",
                "그려줘", "그려주라", "그려주세요", "그려달라", "그려달라고", "그려봐",
                "사진생성", "사진을생성", "사진처럼생성", "사진만들", "사진을만들",
                "사진보여", "사진을보여", "사진으로보여", "사진그려", "사진을그려",
                "배경화면만들", "캐릭터그려", "로고만들",
                "draw", "generateimage", "makeimage", "makepicture", "illustration"
        };
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    public static boolean isImageRevisionRequest(String text) {
        if (text == null) return false;
        String t = normalize(text);
        String[] keywords = new String[] {
                "부족", "아쉬", "빠졌", "누락", "안보여", "안보임", "보이지않",
                "없어", "없네", "없다", "없습니다", "없던데", "없는것같",
                "더넣", "더해", "추가", "넣어줘", "넣어주세요", "고쳐줘", "수정해줘",
                "다시만들", "다시생성", "다시그려", "다시보여", "다시뽑아", "한번더", "한번다시", "재생성"
        };
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    public static boolean isImageRegenerateRequest(String text) {
        if (text == null) return false;
        String t = normalize(text);
        String[] keywords = new String[] {
                "다시만들", "다시생성", "다시그려", "다시뽑아", "한번더", "한번다시", "재생성"
        };
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    public static boolean hasImageContextReference(String text) {
        if (text == null) return false;
        String t = normalize(text);
        String[] keywords = new String[] {
                "사진", "이미지", "그림", "일러스트", "결과물", "방금그린", "방금만든"
        };
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    public static boolean isSaveLatestImageRequest(String text) {
        if (text == null) return false;
        String t = normalize(text);
        return t.contains("이미지저장") || t.contains("이이미지저장") || t.contains("저장해줘");
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}