package com.whereisit.app.monetization;

/** Pure time-window rules for the 60-minute ad-free reward. */
public final class RewardAccessWindow {
    public static final long ACCESS_DURATION_MS = 60L * 60L * 1000L;
    private static final long CLOCK_TOLERANCE_MS = 1_000L;

    private RewardAccessWindow() {
    }

    public static boolean isActive(long grantedAtMs, long accessUntilMs, long nowMs) {
        return grantedAtMs > 0L
                && accessUntilMs > grantedAtMs
                && accessUntilMs - grantedAtMs <= ACCESS_DURATION_MS + CLOCK_TOLERANCE_MS
                && nowMs >= grantedAtMs
                && nowMs < accessUntilMs;
    }

    public static long remainingMinutes(long accessUntilMs, long nowMs) {
        long remainingMs = Math.max(0L, accessUntilMs - nowMs);
        return remainingMs == 0L ? 0L : Math.max(1L, (remainingMs + 59_999L) / 60_000L);
    }
}
