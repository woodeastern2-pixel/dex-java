package com.easternwood.sleeproutine;

import android.content.Context;
import android.content.SharedPreferences;

/* JADX INFO: loaded from: classes.dex */
final class Prefs {
    private static final String FILE = "jamon_preferences";
    private static final String LANGUAGE = "language";
    private static final String PRO_EXPIRES_AT = "pro_expires_at";
    private static final String ROUTINE_COUNT = "routine_count";
    private static final String ROUTINE_MINUTES = "routine_minutes";
    private static final String TOTAL_MINUTES = "total_minutes";

    private Prefs() {
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    static void clearHistory(Context context) {
        prefs(context).edit().remove(ROUTINE_COUNT).remove(TOTAL_MINUTES).apply();
    }

    static String getLanguage(Context context) {
        return prefs(context).getString(LANGUAGE, "ko");
    }

    static int getRoutineCount(Context context) {
        return prefs(context).getInt(ROUTINE_COUNT, 0);
    }

    static int getRoutineMinutes(Context context) {
        return Math.max(1, Math.min(720, prefs(context).getInt(ROUTINE_MINUTES, 20)));
    }

    static int getTotalMinutes(Context context) {
        return prefs(context).getInt(TOTAL_MINUTES, 0);
    }

    static boolean isPro(Context context) {
        return ProAccessPolicy.isActive(
                System.currentTimeMillis(),
                prefs(context).getLong(PRO_EXPIRES_AT, 0L));
    }

    static long getProRemainingMillis(Context context) {
        return ProAccessPolicy.remainingMillis(
                System.currentTimeMillis(),
                prefs(context).getLong(PRO_EXPIRES_AT, 0L));
    }

    static void grantPro24Hours(Context context) {
        long expiresAt = ProAccessPolicy.expiryAfterReward(System.currentTimeMillis());
        prefs(context).edit()
                .remove("developer_pro_review")
                .putLong(PRO_EXPIRES_AT, expiresAt)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, 0);
    }

    static void recordSession(Context context, int i) {
        SharedPreferences sharedPreferencesPrefs = prefs(context);
        sharedPreferencesPrefs.edit().putInt(ROUTINE_COUNT, sharedPreferencesPrefs.getInt(ROUTINE_COUNT, 0) + 1).putInt(TOTAL_MINUTES, sharedPreferencesPrefs.getInt(TOTAL_MINUTES, 0) + Math.max(1, i)).apply();
    }

    static void setLanguage(Context context, String str) {
        prefs(context).edit().putString(LANGUAGE, "en".equals(str) ? "en" : "ko").commit();
    }

    static void setRoutineMinutes(Context context, int i) {
        prefs(context).edit().putInt(ROUTINE_MINUTES, Math.max(1, Math.min(720, i))).apply();
    }
}
