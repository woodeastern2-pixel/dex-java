package com.signpdf.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.signpdf.app.databinding.ActivitySettingsBinding;
import com.signpdf.app.monetization.AdsConsentManager;
import com.signpdf.app.monetization.ProBillingManager;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding mBinding;
    private ProBillingManager mBillingManager;
    private ProBillingManager.State mBillingState;
    private AdsConsentManager mAdsConsentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.tvAppVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        renderLanguageButtons();
        setupNavigation();
        setupLanguageActions();
        setupMonetization();
    }

    private void setupNavigation() {
        mBinding.btnSettingsBack.setOnClickListener(v -> finish());
        mBinding.navSettings.setOnClickListener(v -> mBinding.settingsScroll.smoothScrollTo(0, 0));
        mBinding.navHome.setOnClickListener(v -> openMain(null));
        mBinding.navRecent.setOnClickListener(v -> openMain(MainActivity.SECTION_RECENT));
        mBinding.navTools.setOnClickListener(v -> openMain(MainActivity.SECTION_TOOLS));
        mBinding.rowRecentFiles.setOnClickListener(v -> openMain(MainActivity.SECTION_RECENT));
    }

    private void setupLanguageActions() {
        mBinding.btnLanguageKorean.setOnClickListener(v -> {
            if (!AppLanguageManager.KOREAN.equals(AppLanguageManager.getLanguage(this))) {
                AppLanguageManager.setLanguage(this, AppLanguageManager.KOREAN);
            }
        });
        mBinding.btnLanguageEnglish.setOnClickListener(v -> {
            if (!AppLanguageManager.ENGLISH.equals(AppLanguageManager.getLanguage(this))) {
                AppLanguageManager.setLanguage(this, AppLanguageManager.ENGLISH);
            }
        });
    }

    private void renderLanguageButtons() {
        String language = AppLanguageManager.getLanguage(this);
        styleLanguageButton(mBinding.btnLanguageKorean,
            AppLanguageManager.KOREAN.equals(language));
        styleLanguageButton(mBinding.btnLanguageEnglish,
            AppLanguageManager.ENGLISH.equals(language));
    }

    private void styleLanguageButton(MaterialButton button, boolean selected) {
        int primary = ContextCompat.getColor(this, R.color.colorPrimary);
        int surface = ContextCompat.getColor(this, R.color.colorSurface);
        int textPrimary = ContextCompat.getColor(this, R.color.colorTextPrimary);
        int white = ContextCompat.getColor(this, R.color.colorTextOnPrimary);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? primary : surface));
        button.setTextColor(selected ? white : textPrimary);
        button.setStrokeColor(ColorStateList.valueOf(primary));
        button.setStrokeWidth(dpToPx(1));
    }

    private void setupMonetization() {
        mAdsConsentManager = new AdsConsentManager(this);
        mBillingManager = new ProBillingManager(this, this::renderBillingState);

        mBinding.btnSettingsPro.setOnClickListener(v -> {
            if (mBillingState != null && mBillingState.pro) {
                Toast.makeText(this, R.string.pro_active, Toast.LENGTH_SHORT).show();
                return;
            }
            mBillingManager.launchPurchase();
        });
        mBinding.rowRestorePurchase.setOnClickListener(v -> mBillingManager.refreshPurchases());
        mBinding.rowPrivacyOptions.setOnClickListener(v ->
            mAdsConsentManager.showPrivacyOptions(this, null));

        mBillingManager.start();
        mAdsConsentManager.gatherConsent(this, allowed -> { });
    }

    private void renderBillingState(ProBillingManager.State state) {
        mBillingState = state;
        String status = state.priceText == null || state.priceText.isEmpty()
            ? state.message
            : state.message + " · " + state.priceText;
        mBinding.tvSettingsProStatus.setText(status);

        if (state.pro) {
            mBinding.btnSettingsPro.setText(R.string.pro_active);
            mBinding.btnSettingsPro.setEnabled(false);
        } else {
            String label = getString(R.string.pro_buy);
            if (state.priceText != null && !state.priceText.isEmpty()) {
                label += " · " + state.priceText;
            }
            mBinding.btnSettingsPro.setText(label);
            mBinding.btnSettingsPro.setEnabled(state.ready);
        }
    }

    private void openMain(String section) {
        Intent intent = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (section != null) intent.putExtra(MainActivity.EXTRA_SECTION, section);
        startActivity(intent);
        finish();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderLanguageButtons();
        if (mBillingManager != null) mBillingManager.refreshPurchases();
    }

    @Override
    protected void onDestroy() {
        if (mBillingManager != null) mBillingManager.stop();
        super.onDestroy();
    }
}
