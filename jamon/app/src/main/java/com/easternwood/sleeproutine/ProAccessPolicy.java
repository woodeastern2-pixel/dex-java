package com.easternwood.sleeproutine;

/** Time rules for the opt-in rewarded Pro pass. */
final class ProAccessPolicy {
    static final long ACCESS_MILLIS = 24L * 60L * 60L * 1000L;

    private ProAccessPolicy() {
    }

    static long expiryAfterReward(long nowMillis) {
        return nowMillis + ACCESS_MILLIS;
    }

    static boolean isActive(long nowMillis, long expiresAtMillis) {
        return remainingMillis(nowMillis, expiresAtMillis) > 0L;
    }

    static long remainingMillis(long nowMillis, long expiresAtMillis) {
        return Math.max(0L, expiresAtMillis - nowMillis);
    }
}
