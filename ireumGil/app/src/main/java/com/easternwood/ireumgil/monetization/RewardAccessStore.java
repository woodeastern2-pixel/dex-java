package com.easternwood.ireumgil.monetization;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the local 60-minute access reward without collecting account data. */
public final class RewardAccessStore {

    public static final long ACCESS_DURATION_MS = 60L * 60L * 1000L;

    private static final String PREFS = "ireumon_reward_access";
    private static final String KEY_GRANTED_AT = "granted_at";
    private static final String KEY_ACCESS_UNTIL = "access_until";

    private static Context appContext;

    private RewardAccessStore() { }

    public static synchronized void initialize(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        hasAccess();
    }

    public static synchronized long grantAccess() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return 0L;
        long now = System.currentTimeMillis();
        long until = now + ACCESS_DURATION_MS;
        prefs.edit()
                .putLong(KEY_GRANTED_AT, now)
                .putLong(KEY_ACCESS_UNTIL, until)
                .apply();
        return until;
    }

    public static synchronized boolean hasAccess() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        long now = System.currentTimeMillis();
        long grantedAt = prefs.getLong(KEY_GRANTED_AT, 0L);
        long until = prefs.getLong(KEY_ACCESS_UNTIL, 0L);
        boolean invalid = grantedAt <= 0L
                || until <= grantedAt
                || until - grantedAt > ACCESS_DURATION_MS + 1000L
                || now < grantedAt
                || now >= until;
        if (invalid) {
            if (grantedAt != 0L || until != 0L) {
                prefs.edit().remove(KEY_GRANTED_AT).remove(KEY_ACCESS_UNTIL).apply();
            }
            return false;
        }
        return true;
    }

    public static synchronized long remainingMinutes() {
        if (!hasAccess()) return 0L;
        SharedPreferences prefs = prefs();
        if (prefs == null) return 0L;
        long remaining = Math.max(0L,
                prefs.getLong(KEY_ACCESS_UNTIL, 0L) - System.currentTimeMillis());
        return remaining <= 0L ? 0L : Math.max(1L, (remaining + 59_999L) / 60_000L);
    }

    private static SharedPreferences prefs() {
        return appContext == null
                ? null
                : appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
