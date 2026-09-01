package com.signpdf.app.monetization;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.signpdf.app.R;

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

    /**
     * Opens Google's UMP privacy-options form when one is required. If the
     * current user/region has no privacy choices to change, or consent status is
     * still unknown, the tap always produces clear feedback instead of silently
     * doing nothing.
     */
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
