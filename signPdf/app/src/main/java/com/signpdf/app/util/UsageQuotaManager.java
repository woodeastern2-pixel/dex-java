package com.signpdf.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.signpdf.app.R;

import java.util.Calendar;

/**
 * Local monthly free quota plus a time-limited full-access reward.
 *
 * Free users receive 10 successful output operations per monthly cycle.
 * Completing the rewarded interstitial unlocks all tools for 60 minutes.
 */
public final class UsageQuotaManager {

    public static final int FREE_ACTION_LIMIT = 10;
    public static final long REWARD_ACCESS_DURATION_MS = 60L * 60L * 1000L;

    private static final String PREFS = "signpdf_usage_quota";
    private static final String KEY_CYCLE_START = "cycle_start";
    private static final String KEY_USED = "used_actions";
    private static final String KEY_REWARD_GRANTED_AT = "reward_granted_at";
    private static final String KEY_REWARD_UNTIL = "reward_access_until";

    private static Context appContext;

    private UsageQuotaManager() { }

    public static synchronized void initialize(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        ensureCycleLocked();
        hasRewardAccess();
    }

    public static synchronized long grantRewardAccess() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return 0L;
        long now = System.currentTimeMillis();
        long until = now + REWARD_ACCESS_DURATION_MS;
        prefs.edit()
            .putLong(KEY_REWARD_GRANTED_AT, now)
            .putLong(KEY_REWARD_UNTIL, until)
            .apply();
        return until;
    }

    public static synchronized boolean hasRewardAccess() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;

        long now = System.currentTimeMillis();
        long grantedAt = prefs.getLong(KEY_REWARD_GRANTED_AT, 0L);
        long until = prefs.getLong(KEY_REWARD_UNTIL, 0L);
        boolean invalid = grantedAt <= 0L
            || until <= grantedAt
            || until - grantedAt > REWARD_ACCESS_DURATION_MS + 1000L
            || now < grantedAt
            || now >= until;
        if (invalid) {
            if (grantedAt != 0L || until != 0L) {
                prefs.edit()
                    .remove(KEY_REWARD_GRANTED_AT)
                    .remove(KEY_REWARD_UNTIL)
                    .apply();
            }
            return false;
        }
        return true;
    }

    public static synchronized long getRewardRemainingMillis() {
        if (!hasRewardAccess()) return 0L;
        SharedPreferences prefs = prefs();
        if (prefs == null) return 0L;
        return Math.max(0L,
            prefs.getLong(KEY_REWARD_UNTIL, 0L) - System.currentTimeMillis());
    }

    public static synchronized long getRewardRemainingMinutes() {
        long remaining = getRewardRemainingMillis();
        return remaining <= 0L ? 0L : Math.max(1L, (remaining + 59_999L) / 60_000L);
    }

    public static synchronized boolean canUseAction() {
        if (hasRewardAccess()) return true;
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getInt(KEY_USED, 0) < FREE_ACTION_LIMIT;
    }

    /** Records one operation only after its output was created successfully. */
    public static synchronized void recordSuccessfulAction() {
        if (hasRewardAccess()) return;
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        int used = prefs.getInt(KEY_USED, 0);
        if (used < FREE_ACTION_LIMIT) {
            prefs.edit().putInt(KEY_USED, used + 1).apply();
        }
    }

    public static synchronized int getRemainingActions() {
        if (hasRewardAccess()) return Integer.MAX_VALUE;
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        if (prefs == null) return FREE_ACTION_LIMIT;
        return Math.max(0, FREE_ACTION_LIMIT - prefs.getInt(KEY_USED, 0));
    }

    public static synchronized long getNextResetAtMillis() {
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        if (prefs == null) return 0L;
        long start = prefs.getLong(KEY_CYCLE_START, 0L);
        if (start <= 0L) return 0L;
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(start);
        next.add(Calendar.MONTH, 1);
        return next.getTimeInMillis();
    }

    public static synchronized String getLimitReachedMessage() {
        if (appContext == null) {
            return "Free limit reached. Watch an ad to unlock all tools for 60 minutes.";
        }
        return appContext.getString(R.string.free_quota_limit_reached);
    }

    private static SharedPreferences prefs() {
        if (appContext == null) return null;
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void ensureCycleLocked() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;

        long now = System.currentTimeMillis();
        long start = prefs.getLong(KEY_CYCLE_START, 0L);
        if (start <= 0L) {
            prefs.edit()
                .putLong(KEY_CYCLE_START, now)
                .putInt(KEY_USED, 0)
                .apply();
            return;
        }

        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(start);
        next.add(Calendar.MONTH, 1);
        if (now >= next.getTimeInMillis() || now < start) {
            prefs.edit()
                .putLong(KEY_CYCLE_START, now)
                .putInt(KEY_USED, 0)
                .apply();
        }
    }
}
