package com.easternwood.ireumgil.monetization;

public final class RewardAccessPolicy {

    public static final int REWARDED_SCORE = 95;

    private RewardAccessPolicy() { }

    public static boolean requiresReward(int score) {
        return score >= REWARDED_SCORE;
    }
}
