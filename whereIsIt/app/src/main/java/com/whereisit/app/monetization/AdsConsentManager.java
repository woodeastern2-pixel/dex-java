package com.whereisit.app.monetization;

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
    private volatile boolean initialized;

    public AdsConsentManager(Context context) {
        consentInformation = UserMessagingPlatform.getConsentInformation(context);
    }

    public void gatherConsent(Activity activity, ConsentCallback callback) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity,
                        error -> complete(activity, callback)
                ),
                error -> complete(activity, callback)
        );
    }

    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public boolean canRequestAds() {
        return consentInformation.canRequestAds();
    }

    public void showPrivacyOptions(Activity activity, Runnable onDismissed) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (onDismissed != null) onDismissed.run();
        });
    }

    private void complete(Activity activity, ConsentCallback callback) {
        boolean allowed = consentInformation.canRequestAds();
        if (!allowed) {
            callback.onResult(false);
            return;
        }
        if (initialized) {
            callback.onResult(true);
            return;
        }
        new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), status -> {
            initialized = true;
            activity.runOnUiThread(() -> callback.onResult(true));
        })).start();
    }
}
