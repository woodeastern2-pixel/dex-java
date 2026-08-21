package com.easternwood.ireumgil.engine;

import com.easternwood.ireumgil.model.HanjaCharacter;

import java.util.List;

public class YinYangAnalyzer {

    public int score(List<HanjaCharacter> chars) {
        if (!hasCompleteStrokes(chars)) return 0;
        int yin = countYin(chars);
        return (yin == 1 || yin == 2) ? 15 : 5;
    }

    public String summary(List<HanjaCharacter> chars) {
        if (!hasCompleteStrokes(chars)) {
            return "획수 정보가 불완전해 음양 배열을 계산하지 않았습니다.";
        }
        int yin = countYin(chars);
        int yang = chars.size() - yin;
        StringBuilder pattern = new StringBuilder();
        for (HanjaCharacter c : chars) {
            if (pattern.length() > 0) pattern.append("-");
            pattern.append(c.strokeCount % 2 == 0 ? "음" : "양");
        }
        int score = score(chars);
        return "획수 홀짝 배열 " + pattern + " · 음 " + yin + " / 양 " + yang + " · 음양 점수 " + score + "/15";
    }

    private boolean hasCompleteStrokes(List<HanjaCharacter> chars) {
        if (chars == null || chars.size() != 3) return false;
        for (HanjaCharacter c : chars) {
            if (c == null || c.strokeCount == null || c.strokeCount <= 0) return false;
        }
        return true;
    }

    private int countYin(List<HanjaCharacter> chars) {
        int yin = 0;
        for (HanjaCharacter c : chars) {
            if (c.strokeCount % 2 == 0) yin++;
        }
        return yin;
    }
}
