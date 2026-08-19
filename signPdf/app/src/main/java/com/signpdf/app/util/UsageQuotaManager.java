package com.signpdf.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.signpdf.app.BuildConfig;
import com.signpdf.app.R;

import java.util.Calendar;

/**
 * Local monthly usage quota for the free tier.
 *
 * Free users receive 10 successful output operations per monthly cycle.
 * A cycle starts on first use and renews one calendar month later.
 * Pro users bypass the quota completely.
 */
public final class UsageQuotaManager {

    public static final int FREE_ACTION_LIMIT = 10;

    private static final String PREFS = "signpdf_usage_quota";
    private static final String KEY_CYCLE_START = "cycle_start";
    private static final String KEY_USED = "used_actions";
    private static final String KEY_PRO = "pro_entitled";
    private static final String KEY_DEBUG_PRO = "debug_pro_override";

    private static Context appContext;

    private UsageQuotaManager() { }

    public static synchronized void initialize(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        ensureCycleLocked();
    }

    public static synchronized void setPro(boolean pro) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putBoolean(KEY_PRO, pro).apply();
    }

    /** Debug builds only: lets a developer test Pro features without a Play purchase. */
    public static synchronized void setDebugProOverride(boolean enabled) {
        if (!BuildConfig.DEBUG) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putBoolean(KEY_DEBUG_PRO, enabled).apply();
    }

    public static synchronized boolean isDebugProOverride() {
        if (!BuildConfig.DEBUG) return false;
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(KEY_DEBUG_PRO, false);
    }

    public static synchronized boolean isPro() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        return (BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_PRO, false))
            || prefs.getBoolean(KEY_PRO, false);
    }

    public static synchronized boolean canUseAction() {
        if (isPro()) return true;
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getInt(KEY_USED, 0) < FREE_ACTION_LIMIT;
    }

    /** Records one operation only after its output was created successfully. */
    public static synchronized void recordSuccessfulAction() {
        if (isPro()) return;
        ensureCycleLocked();
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        int used = prefs.getInt(KEY_USED, 0);
        if (used < FREE_ACTION_LIMIT) {
            prefs.edit().putInt(KEY_USED, used + 1).apply();
        }
    }

    public static synchronized int getRemainingActions() {
        if (isPro()) return Integer.MAX_VALUE;
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
            return "Monthly free limit reached. Upgrade to Pro for unlimited use.";
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
