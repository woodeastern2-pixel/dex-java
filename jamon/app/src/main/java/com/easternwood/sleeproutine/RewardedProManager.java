package com.easternwood.sleeproutine;

import android.app.Activity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/** Loads the opt-in rewarded ad that grants one 24-hour Pro pass. */
final class RewardedProManager {
    interface Listener {
        void onStateChanged();
        void onRewardGranted();
        void onMessage(int messageResId);
    }

    private final Activity activity;
    private Listener listener;
    private RewardedAd rewardedAd;
    private boolean loading;
    private boolean showing;
    private boolean destroyed;

    RewardedProManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean isLoading() {
        return loading;
    }

    boolean isReady() {
        return rewardedAd != null;
    }

    void load() {
        if (destroyed || loading || showing || rewardedAd != null
                || Prefs.isPro(activity) || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        loading = true;
        notifyStateChanged();
        RewardedAd.load(
                activity,
                BuildConfig.ADMOB_REWARDED_ID,
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        if (destroyed) {
                            return;
                        }
                        loading = false;
                        rewardedAd = ad;
                        notifyStateChanged();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        if (destroyed) {
                            return;
                        }
                        loading = false;
                        rewardedAd = null;
                        notifyStateChanged();
                    }
                });
    }

    void show() {
        if (destroyed || showing || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (Prefs.isPro(activity)) {
            if (listener != null) {
                listener.onRewardGranted();
            }
            return;
        }
        if (rewardedAd == null) {
            load();
            if (listener != null) {
                listener.onMessage(R.string.reward_ad_not_ready);
            }
            return;
        }

        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        showing = true;
        notifyStateChanged();
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                if (destroyed) {
                    return;
                }
                showing = false;
                notifyStateChanged();
                if (!Prefs.isPro(activity)) {
                    load();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError error) {
                if (destroyed) {
                    return;
                }
                showing = false;
                notifyStateChanged();
                if (listener != null) {
                    listener.onMessage(R.string.reward_ad_show_failed);
                }
                load();
            }
        });
        ad.show(activity, reward -> {
            if (destroyed) {
                return;
            }
            Prefs.grantPro24Hours(activity);
            if (listener != null) {
                listener.onRewardGranted();
            }
        });
    }

    void destroy() {
        destroyed = true;
        loading = false;
        showing = false;
        rewardedAd = null;
        listener = null;
    }

    private void notifyStateChanged() {
        if (listener != null) {
            listener.onStateChanged();
        }
    }
}
