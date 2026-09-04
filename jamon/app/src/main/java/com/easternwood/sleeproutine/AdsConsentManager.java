package com.easternwood.sleeproutine;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Resolves Google UMP consent before Jamon requests an ad. */
final class AdsConsentManager {
    interface Callback {
        void onResult(boolean canRequestAds);
    }

    private static boolean mobileAdsInitialized;
    private final ConsentInformation consentInformation;

    AdsConsentManager(Context context) {
        consentInformation = UserMessagingPlatform.getConsentInformation(context);
    }

    void gatherConsent(Activity activity, Callback callback) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity,
                        error -> finishConsent(activity, callback)),
                error -> finishConsent(activity, callback));
    }

    void showPrivacyOptions(Activity activity) {
        ConsentInformation.PrivacyOptionsRequirementStatus status =
                consentInformation.getPrivacyOptionsRequirementStatus();
        if (status == ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN) {
            Toast.makeText(activity, R.string.ad_privacy_checking, Toast.LENGTH_LONG).show();
            return;
        }
        if (status != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity, R.string.ad_privacy_not_required, Toast.LENGTH_LONG).show();
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (error != null) {
                Toast.makeText(activity, R.string.ad_privacy_open_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishConsent(Context context, Callback callback) {
        boolean canRequestAds = consentInformation.canRequestAds();
        initializeMobileAds(context, canRequestAds);
        callback.onResult(canRequestAds);
    }

    private static synchronized void initializeMobileAds(Context context, boolean allowed) {
        if (!allowed || mobileAdsInitialized) {
            return;
        }
        mobileAdsInitialized = true;
        MobileAds.initialize(context.getApplicationContext(), ignored -> { });
    }
}
