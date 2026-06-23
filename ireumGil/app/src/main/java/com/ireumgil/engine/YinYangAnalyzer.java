package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;

import java.util.List;

public class YinYangAnalyzer {

    public int score(List<HanjaCharacter> chars) {
        int yin = 0;
        int yang = 0;
        for (HanjaCharacter c : chars) {
            int stroke = c.strokeCount == null ? 0 : c.strokeCount;
            if (stroke % 2 == 0) {
                yin++;
            } else {
                yang++;
            }
        }
        int diff = Math.abs(yin - yang);
        if (diff == 0) {
            return 18;
        }
        if (diff == 1) {
            return 14;
        }
        return 10;
    }

    public String summary(List<HanjaCharacter> chars) {
        int yin = 0;
        int yang = 0;
        for (HanjaCharacter c : chars) {
            int stroke = c.strokeCount == null ? 0 : c.strokeCount;
            if (stroke % 2 == 0) {
                yin++;
            } else {
                yang++;
            }
        }
        return "획수 홀짝 기준 음양 비율: 음 " + yin + " / 양 " + yang;
    }
}
