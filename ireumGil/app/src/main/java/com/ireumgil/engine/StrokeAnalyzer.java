package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;

import java.util.List;

public class StrokeAnalyzer {

    public int totalStrokes(List<HanjaCharacter> chars) {
        int sum = 0;
        for (HanjaCharacter c : chars) {
            if (c.strokeCount != null) {
                sum += c.strokeCount;
            }
        }
        return sum;
    }

    public int scoreByStrokes(int total) {
        int mod = total % 10;
        if (mod == 1 || mod == 3 || mod == 5 || mod == 8) {
            return 18;
        }
        if (mod == 2 || mod == 6 || mod == 9) {
            return 14;
        }
        return 10;
    }

    public String summary(int total) {
        int mod = total % 10;
        if (mod == 1 || mod == 3 || mod == 5 || mod == 8) {
            return "총 획수 " + total + "획: 전통 수리 관점에서 안정 구간으로 해석됩니다.";
        }
        if (mod == 2 || mod == 6 || mod == 9) {
            return "총 획수 " + total + "획: 무난한 흐름으로, 다른 요소와 함께 보는 것이 좋습니다.";
        }
        return "총 획수 " + total + "획: 다소 기복이 있을 수 있어 오행/의미 보완이 중요합니다.";
    }
}
