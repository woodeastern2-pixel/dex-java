package com.ireumgil.engine;

public class NameScoreCalculator {

    public int calculateTotal(int elementScore, int strokeScore, int yinYangScore, int meaningScore) {
        // The four analyzers currently have a combined maximum of 82 points.
        // Normalize that real subtotal instead of artificially forcing result bands.
        int subtotal = elementScore + strokeScore + yinYangScore + meaningScore;
        return Math.max(0, Math.min(100, Math.round(subtotal * 100f / 82f)));
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
