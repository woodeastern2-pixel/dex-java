package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StrokeAnalyzer {

    private static final Set<Integer> AUSPICIOUS = new HashSet<>(Arrays.asList(
            1, 3, 5, 6, 7, 8, 11, 13, 15, 16, 17, 18, 21, 23, 24, 25,
            29, 31, 32, 33, 35, 37, 39, 41, 45, 47, 48, 52, 57, 61, 63,
            65, 67, 68, 81
    ));
    private static final Set<Integer> NEUTRAL = new HashSet<>(Arrays.asList(
            27, 30, 38, 40, 42, 43, 50, 51, 53, 55, 58, 71, 73, 75, 77, 78
    ));

    public static class Analysis {
        public final boolean valid;
        public final int total;
        public final int score;
        public final String detail;

        public Analysis(boolean valid, int total, int score, String detail) {
            this.valid = valid;
            this.total = total;
            this.score = score;
            this.detail = detail;
        }
    }

    public Analysis analyze(List<HanjaCharacter> chars) {
        if (chars == null || chars.size() != 3) {
            return invalid("성명 한자 3글자가 필요합니다.");
        }
        for (HanjaCharacter c : chars) {
            if (c == null || c.strokeCount == null || c.strokeCount <= 0) {
                String label = c == null ? "선택하지 않은 글자" : c.character;
                return invalid(label + "의 획수 원전 데이터가 없어 점수를 계산하지 않았습니다.");
            }
        }

        int surname = chars.get(0).strokeCount;
        int first = chars.get(1).strokeCount;
        int second = chars.get(2).strokeCount;
        int origin = first + second;
        int form = surname + first;
        int relation = surname + second;
        int total = surname + first + second;

        int raw = ratingPoints(origin) + ratingPoints(form) + ratingPoints(relation) + ratingPoints(total);
        int score = Math.round(raw * 30f / 12f);
        String detail = String.format(
                Locale.KOREA,
                "글자별 획수 %s %d획 · %s %d획 · %s %d획\n"
                        + "원격 %d(%s) · 형격 %d(%s) · 이격 %d(%s) · 정격 %d(%s)\n"
                        + "수리 점수 %d/30",
                chars.get(0).character, surname,
                chars.get(1).character, first,
                chars.get(2).character, second,
                origin, ratingLabel(origin),
                form, ratingLabel(form),
                relation, ratingLabel(relation),
                total, ratingLabel(total),
                score
        );
        return new Analysis(true, total, score, detail);
    }

    public int scoreOnly(List<HanjaCharacter> chars) {
        if (chars == null || chars.size() != 3) {
            return 0;
        }
        for (HanjaCharacter character : chars) {
            if (character == null || character.strokeCount == null || character.strokeCount <= 0) {
                return 0;
            }
        }
        int surname = chars.get(0).strokeCount;
        int first = chars.get(1).strokeCount;
        int second = chars.get(2).strokeCount;
        int raw = ratingPoints(first + second)
                + ratingPoints(surname + first)
                + ratingPoints(surname + second)
                + ratingPoints(surname + first + second);
        return Math.round(raw * 30f / 12f);
    }

    public int totalStrokes(List<HanjaCharacter> chars) {
        int sum = 0;
        if (chars == null) return sum;
        for (HanjaCharacter c : chars) {
            if (c != null && c.strokeCount != null) sum += c.strokeCount;
        }
        return sum;
    }

    private Analysis invalid(String message) {
        return new Analysis(false, 0, 0, message);
    }

    private int normalizedNumber(int value) {
        return value <= 81 ? value : ((value - 1) % 80) + 1;
    }

    private int ratingPoints(int value) {
        int number = normalizedNumber(value);
        if (AUSPICIOUS.contains(number)) return 3;
        if (NEUTRAL.contains(number)) return 2;
        return 1;
    }

    private String ratingLabel(int value) {
        int number = normalizedNumber(value);
        if (AUSPICIOUS.contains(number)) return "길";
        if (NEUTRAL.contains(number)) return "보통";
        return "주의";
    }
}
