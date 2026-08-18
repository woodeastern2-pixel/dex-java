package com.signpdf.app.monetization;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Google UMP 동의 상태를 확인하고 광고 SDK 초기화를 관리합니다.
 */
public class AdsConsentManager {

    public interface ConsentCallback {
        void onResult(boolean canRequestAds);
    }

    private final ConsentInformation consentInformation;
    private boolean mobileAdsInitialized = false;

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
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                activity,
                formError -> {
                    boolean allowed = consentInformation.canRequestAds();
                    initializeAdsIfAllowed(activity, allowed);
                    callback.onResult(allowed);
                }
            ),
            requestError -> {
                boolean allowed = consentInformation.canRequestAds();
                initializeAdsIfAllowed(activity, allowed);
                callback.onResult(allowed);
            }
        );
    }

    public void showPrivacyOptions(Activity activity, Runnable onDismissed) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (onDismissed != null) {
                onDismissed.run();
            }
        });
    }

    private void initializeAdsIfAllowed(Context context, boolean allowed) {
        if (!allowed || mobileAdsInitialized) return;
        mobileAdsInitialized = true;
        MobileAds.initialize(context, initializationStatus -> { });
    }
}
