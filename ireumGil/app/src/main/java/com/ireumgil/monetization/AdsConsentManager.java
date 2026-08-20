package com.ireumgil.monetization;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class AdsConsentManager {

    public interface ConsentCallback {
        void onResult(boolean canRequestAds);
    }

    private final ConsentInformation consentInformation;
    private boolean initialized;

    public AdsConsentManager(Context context) {
        consentInformation = UserMessagingPlatform.getConsentInformation(context);
    }

    public void gatherConsent(Activity activity, ConsentCallback callback) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> finish(activity, callback)),
                error -> finish(activity, callback)
        );
    }

    private void finish(Context context, ConsentCallback callback) {
        boolean allowed = consentInformation.canRequestAds();
        if (allowed && !initialized) {
            initialized = true;
            MobileAds.initialize(context, status -> { });
        }
        callback.onResult(allowed);
    }
}
