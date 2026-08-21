package com.easternwood.ireumgil.monetization;

public final class PremiumRecommendationPolicy {

    public static final int PREMIUM_SCORE = 95;

    private PremiumRecommendationPolicy() {
    }

    public static boolean requiresPro(int score) {
        return score >= PREMIUM_SCORE;
    }
}
