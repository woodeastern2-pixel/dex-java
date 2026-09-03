package com.easternwood.ireumgil.monetization;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.easternwood.ireumgil.R;

/** Coordinates consent, rewarded-ad loading, and the access explanation dialog. */
public final class RewardedAccessController {

    private final Activity activity;
    private final Runnable onAccessChanged;
    private final AdsConsentManager consentManager;
    private final RewardedAccessManager rewardedManager;
    private boolean consentResolved;
    private boolean adsAllowed;

    public RewardedAccessController(Activity activity, Runnable onAccessChanged) {
        this.activity = activity;
        this.onAccessChanged = onAccessChanged;
        consentManager = new AdsConsentManager(activity);
        rewardedManager = new RewardedAccessManager(activity, new RewardedAccessManager.Listener() {
            @Override
            public void onStateChanged() {
                notifyChanged();
            }

            @Override
            public void onRewardGranted() {
                Toast.makeText(activity, R.string.reward_access_granted, Toast.LENGTH_LONG).show();
                notifyChanged();
            }

            @Override
            public void onMessage(int messageResId) {
                Toast.makeText(activity, messageResId, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void start() {
        notifyChanged();
        gatherConsent();
    }

    private void gatherConsent() {
        consentManager.gatherConsent(activity, allowed -> {
            consentResolved = true;
            adsAllowed = allowed;
            if (allowed) rewardedManager.load();
            notifyChanged();
        });
    }

    public void requestAccess() {
        if (RewardAccessStore.hasAccess()) {
            Toast.makeText(activity, R.string.reward_access_already_active, Toast.LENGTH_SHORT).show();
            notifyChanged();
            return;
        }
        if (!consentResolved) {
            Toast.makeText(activity, R.string.reward_consent_waiting, Toast.LENGTH_LONG).show();
            return;
        }
        if (!adsAllowed) {
            Toast.makeText(activity, R.string.reward_ad_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.reward_ad_intro_title)
                .setMessage(R.string.reward_ad_intro_message)
                .setPositiveButton(R.string.reward_ad_watch,
                        (dialog, which) -> rewardedManager.show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public boolean canRequestAds() {
        return consentResolved && adsAllowed;
    }

    public boolean isLoading() {
        return rewardedManager.isLoading();
    }

    public boolean isPrivacyOptionsRequired() {
        return consentManager.isPrivacyOptionsRequired();
    }

    public void showPrivacyOptions() {
        consentManager.showPrivacyOptions(activity, this::gatherConsent);
    }

    public void onResume() {
        if (adsAllowed) rewardedManager.load();
        notifyChanged();
    }

    public void destroy() {
        rewardedManager.destroy();
    }

    private void notifyChanged() {
        if (onAccessChanged != null) onAccessChanged.run();
    }
}
