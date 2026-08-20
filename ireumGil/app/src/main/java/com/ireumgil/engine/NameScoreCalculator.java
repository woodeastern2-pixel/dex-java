package com.ireumgil.engine;

public class NameScoreCalculator {

    public int calculateTotal(int elementScore, int strokeScore, int yinYangScore, int dataScore) {
        int total = elementScore + strokeScore + yinYangScore + dataScore;
        return Math.max(0, Math.min(100, total));
    }

    public String grade(int score) {
        if (score >= 85) return "매우 좋음";
        if (score >= 70) return "좋음";
        if (score >= 55) return "보통";
        return "추가 검토";
    }
}
