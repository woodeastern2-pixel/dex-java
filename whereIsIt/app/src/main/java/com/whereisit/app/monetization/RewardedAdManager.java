package com.whereisit.app.monetization;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.whereisit.app.BuildConfig;

/** Loads only the user-requested rewarded interstitial. */
public final class RewardedAdManager {
    public interface Listener {
        void onStateChanged();
        void onRewardGranted();
    }

    private final Activity activity;
    private final RewardAccessStore accessStore;
    private Listener listener;
    private RewardedInterstitialAd rewardedAd;
    private boolean loading;
    private boolean showing;
    private boolean enabled;
    private boolean destroyed;

    public RewardedAdManager(
            Activity activity,
            RewardAccessStore accessStore,
            Listener listener
    ) {
        this.activity = activity;
        this.accessStore = accessStore;
        this.listener = listener;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            if (enabled) load();
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            rewardedAd = null;
            loading = false;
        } else {
            load();
        }
        notifyStateChanged();
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isReady() {
        return rewardedAd != null;
    }

    public void load() {
        if (!enabled || destroyed || loading || rewardedAd != null || showing
                || accessStore.hasAccess()
                || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        loading = true;
        notifyStateChanged();
        RewardedInterstitialAd.load(
                activity,
                BuildConfig.ADMOB_REWARDED_INTERSTITIAL_ID,
                new AdRequest.Builder().build(),
                new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                        if (destroyed) return;
                        loading = false;
                        rewardedAd = enabled ? ad : null;
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        if (destroyed) return;
                        loading = false;
                        rewardedAd = null;
                        notifyStateChanged();
                    }
                });
    }

    public boolean show() {
        if (!enabled || destroyed || showing || rewardedAd == null
                || activity.isFinishing() || activity.isDestroyed()) {
            load();
            return false;
        }
        RewardedInterstitialAd ad = rewardedAd;
        rewardedAd = null;
        showing = true;
        notifyStateChanged();
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                if (destroyed) return;
                showing = false;
                notifyStateChanged();
                load();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                if (destroyed) return;
                showing = false;
                notifyStateChanged();
                load();
            }
        });
        ad.show(activity, rewardItem -> {
            if (destroyed) return;
            accessStore.grantAccess();
            if (listener != null) listener.onRewardGranted();
        });
        return true;
    }

    public void destroy() {
        destroyed = true;
        enabled = false;
        loading = false;
        showing = false;
        rewardedAd = null;
        listener = null;
    }

    private void notifyStateChanged() {
        if (listener != null) listener.onStateChanged();
    }
}
