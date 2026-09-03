package com.easternwood.ireumgil.monetization;

import android.app.Activity;

import com.easternwood.ireumgil.BuildConfig;
import com.easternwood.ireumgil.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

/** Loads the rewarded interstitial that grants 60 minutes of full access. */
public final class RewardedAccessManager {

    public interface Listener {
        void onStateChanged();
        void onRewardGranted();
        void onMessage(int messageResId);
    }

    private final Activity activity;
    private Listener listener;
    private RewardedInterstitialAd rewardedAd;
    private boolean loading;
    private boolean showing;
    private boolean destroyed;

    public RewardedAccessManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public boolean isLoading() {
        return loading;
    }

    public void load() {
        if (destroyed || loading || rewardedAd != null || showing
                || RewardAccessStore.hasAccess()
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
                    public void onAdLoaded(RewardedInterstitialAd ad) {
                        if (destroyed) return;
                        loading = false;
                        rewardedAd = ad;
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        if (destroyed) return;
                        loading = false;
                        rewardedAd = null;
                        notifyStateChanged();
                    }
                });
    }

    public void show() {
        if (destroyed || showing || activity.isFinishing() || activity.isDestroyed()) return;
        if (RewardAccessStore.hasAccess()) {
            if (listener != null) listener.onRewardGranted();
            return;
        }
        if (rewardedAd == null) {
            load();
            if (listener != null) listener.onMessage(R.string.reward_ad_not_ready);
            return;
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
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                if (destroyed) return;
                showing = false;
                notifyStateChanged();
                if (listener != null) listener.onMessage(R.string.reward_ad_show_failed);
                load();
            }
        });
        ad.show(activity, rewardItem -> {
            if (destroyed) return;
            RewardAccessStore.grantAccess();
            if (listener != null) listener.onRewardGranted();
        });
    }

    public void destroy() {
        destroyed = true;
        loading = false;
        showing = false;
        rewardedAd = null;
        listener = null;
    }

    private void notifyStateChanged() {
        if (listener != null) listener.onStateChanged();
    }
}
