package com.whereisit.app.monetization;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.whereisit.app.BuildConfig;

/**
 * Utility 앱의 흐름을 방해하지 않도록 긴 시간 간격과 사용 행동 조건을 함께 적용합니다.
 */
public class GentleAdManager {
    private static final String PREFS = "whereisit_ads";
    private static final String KEY_LAST_SHOWN = "last_interstitial_shown";
    private static final long MIN_SESSION_MS = 3 * 60 * 1000L;
    private static final long MIN_INTERVAL_MS = 20 * 60 * 1000L;
    private static final int MIN_MEANINGFUL_ACTIONS = 4;

    private final Context appContext;
    private final SharedPreferences preferences;
    private final long sessionStartedAt = SystemClock.elapsedRealtime();
    private InterstitialAd interstitialAd;
    private int meaningfulActions;
    private boolean loading;
    private boolean adsEnabled;

    public GentleAdManager(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start() {
        adsEnabled = true;
        load();
    }

    public void stop() {
        adsEnabled = false;
        interstitialAd = null;
    }

    public void recordMeaningfulAction() {
        meaningfulActions++;
    }

    public void showAfterNaturalBreak(Activity activity) {
        if (!adsEnabled || !isEligible() || interstitialAd == null || activity.isFinishing()) return;
        InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                load();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                load();
            }
        });
        meaningfulActions = 0;
        preferences.edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply();
        ad.show(activity);
    }

    private boolean isEligible() {
        long sessionAge = SystemClock.elapsedRealtime() - sessionStartedAt;
        long lastShown = preferences.getLong(KEY_LAST_SHOWN, 0L);
        long sinceLast = lastShown == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastShown;
        return sessionAge >= MIN_SESSION_MS
                && sinceLast >= MIN_INTERVAL_MS
                && meaningfulActions >= MIN_MEANINGFUL_ACTIONS;
    }

    private void load() {
        if (!adsEnabled || loading || interstitialAd != null) return;
        loading = true;
        InterstitialAd.load(
                appContext,
                BuildConfig.ADMOB_INTERSTITIAL_ID,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        loading = false;
                        interstitialAd = adsEnabled ? ad : null;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        loading = false;
                        interstitialAd = null;
                    }
                }
        );
    }
}
