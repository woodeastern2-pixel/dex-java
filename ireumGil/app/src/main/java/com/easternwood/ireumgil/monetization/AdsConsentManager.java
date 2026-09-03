package com.easternwood.ireumgil.monetization;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.easternwood.ireumgil.R;
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

    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
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

    public void showPrivacyOptions(Activity activity, Runnable onDismissed) {
        ConsentInformation.PrivacyOptionsRequirementStatus status =
                consentInformation.getPrivacyOptionsRequirementStatus();
        if (status == ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN) {
            Toast.makeText(activity, R.string.ad_privacy_checking, Toast.LENGTH_LONG).show();
            if (onDismissed != null) onDismissed.run();
            return;
        }
        if (status != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity, R.string.ad_privacy_not_required, Toast.LENGTH_LONG).show();
            if (onDismissed != null) onDismissed.run();
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (formError != null) {
                Toast.makeText(activity, R.string.ad_privacy_open_failed, Toast.LENGTH_LONG).show();
            }
            if (onDismissed != null) onDismissed.run();
        });
    }
}
