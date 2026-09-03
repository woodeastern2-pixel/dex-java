package com.whereisit.app.monetization;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the local 60-minute ad-free reward without collecting account data. */
public final class RewardAccessStore {
    private static final String PREFS = "whereisit_reward_access";
    private static final String KEY_GRANTED_AT = "granted_at";
    private static final String KEY_ACCESS_UNTIL = "access_until";

    private final SharedPreferences preferences;

    public RewardAccessStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized long grantAccess() {
        long grantedAt = System.currentTimeMillis();
        long accessUntil = grantedAt + RewardAccessWindow.ACCESS_DURATION_MS;
        preferences.edit()
                .putLong(KEY_GRANTED_AT, grantedAt)
                .putLong(KEY_ACCESS_UNTIL, accessUntil)
                .apply();
        return accessUntil;
    }

    public synchronized boolean hasAccess() {
        long grantedAt = preferences.getLong(KEY_GRANTED_AT, 0L);
        long accessUntil = preferences.getLong(KEY_ACCESS_UNTIL, 0L);
        boolean active = RewardAccessWindow.isActive(
                grantedAt,
                accessUntil,
                System.currentTimeMillis());
        if (!active && (grantedAt != 0L || accessUntil != 0L)) {
            preferences.edit().remove(KEY_GRANTED_AT).remove(KEY_ACCESS_UNTIL).apply();
        }
        return active;
    }

    public synchronized long remainingMinutes() {
        if (!hasAccess()) return 0L;
        return RewardAccessWindow.remainingMinutes(
                preferences.getLong(KEY_ACCESS_UNTIL, 0L),
                System.currentTimeMillis());
    }
}
