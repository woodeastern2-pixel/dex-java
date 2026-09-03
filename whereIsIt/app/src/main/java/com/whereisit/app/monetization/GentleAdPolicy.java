package com.whereisit.app.monetization;

/**
 * 전면 광고가 사용 흐름을 방해하지 않도록 시간과 의미 있는 사용 행동을 함께 확인합니다.
 */
final class GentleAdPolicy {
    static final long MIN_SESSION_MS = 3 * 60 * 1000L;
    static final long MIN_INTERVAL_MS = 20 * 60 * 1000L;
    static final int MIN_MEANINGFUL_ACTIONS = 4;

    private GentleAdPolicy() {
    }

    static boolean isEligible(long sessionAgeMs, long sinceLastShownMs, int meaningfulActions) {
        return sessionAgeMs >= MIN_SESSION_MS
                && sinceLastShownMs >= MIN_INTERVAL_MS
                && meaningfulActions >= MIN_MEANINGFUL_ACTIONS;
    }
}
