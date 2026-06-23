package com.ireumgil.engine;

public class NameScoreCalculator {

    public int calculateTotal(int elementScore, int strokeScore, int yinYangScore, int meaningScore) {
        int total = elementScore + strokeScore + yinYangScore + meaningScore;
        if (total > 100) {
            return 100;
        }
        return Math.max(total, 0);
    }

    public String grade(int score) {
        if (score >= 85) {
            return "매우 좋음";
        }
        if (score >= 70) {
            return "좋음";
        }
        if (score >= 55) {
            return "보통";
        }
        return "주의 필요";
    }
}
