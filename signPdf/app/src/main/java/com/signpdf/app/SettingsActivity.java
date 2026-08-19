package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;
import com.signpdf.app.databinding.ActivitySettingsBinding;
import com.signpdf.app.monetization.AdsConsentManager;
import com.signpdf.app.monetization.ProBillingManager;
import com.signpdf.app.util.SaveLocationPreferences;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding mBinding;
    private ProBillingManager mBillingManager;
    private ProBillingManager.State mBillingState;
    private AdsConsentManager mAdsConsentManager;
    private ActivityResultLauncher<Intent> mSaveFolderLauncher;
    private TextView mSaveLocationValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mSaveFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handleSaveFolderResult(result.getResultCode(), result.getData()));

        mBinding.tvAppVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        renderLanguageButtons();
        setupNavigation();
        setupLanguageActions();
        setupSaveLocationRow();
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

    /** Adds the save-location setting into the existing Document options card. */
    private void setupSaveLocationRow() {
        View parentView = (View) mBinding.rowRecentFiles.getParent();
        if (!(parentView instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) parentView;
        int insertIndex = parent.indexOfChild(mBinding.rowRecentFiles);
        if (insertIndex < 0) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(dpToPx(82));

        TypedValue selectable = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
            selectable, true) && selectable.resourceId != 0) {
            row.setBackgroundResource(selectable.resourceId);
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_folder);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
        row.addView(icon, new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)));

        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textGroupParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textGroupParams.leftMargin = dpToPx(12);
        row.addView(textGroup, textGroupParams);

        TextView title = new TextView(this);
        title.setText(R.string.settings_save_location);
        title.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
        title.setTextSize(14);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        textGroup.addView(title);

        TextView description = new TextView(this);
        description.setText(R.string.settings_save_location_desc);
        description.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
        description.setTextSize(11);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dpToPx(2);
        textGroup.addView(description, descParams);

        mSaveLocationValue = new TextView(this);
        mSaveLocationValue.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        mSaveLocationValue.setTextSize(11);
        mSaveLocationValue.setMaxLines(1);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(3);
        textGroup.addView(mSaveLocationValue, valueParams);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ContextCompat.getColor(this, R.color.colorTextTertiary));
        row.addView(chevron, new LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)));

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.colorDivider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        dividerParams.leftMargin = dpToPx(14);
        dividerParams.rightMargin = dpToPx(14);

        parent.addView(row, insertIndex, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(82)));
        parent.addView(divider, insertIndex + 1, dividerParams);

        row.setOnClickListener(v -> chooseSaveFolder());
        updateSaveLocationValue();
    }

    private void chooseSaveFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        mSaveFolderLauncher.launch(intent);
    }

    private void handleSaveFolderResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int takeFlags = data.getFlags()
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) {
            // Some providers do not support persisted grants; the current grant still works.
        }

        DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
        String label = folder == null ? null : folder.getName();
        if (label == null || label.trim().isEmpty()) label = uri.getLastPathSegment();
        if (label == null || label.trim().isEmpty()) label = getString(R.string.settings_save_location);
        SaveLocationPreferences.set(this, uri, label);
        updateSaveLocationValue();
    }

    private void updateSaveLocationValue() {
        if (mSaveLocationValue == null) return;
        if (!SaveLocationPreferences.hasCustomLocation(this)) {
            mSaveLocationValue.setText(R.string.settings_save_location_default);
            return;
        }
        String label = SaveLocationPreferences.getLabel(this);
        mSaveLocationValue.setText(
            label == null || label.trim().isEmpty()
                ? getString(R.string.settings_save_location)
                : label);
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
        updateSaveLocationValue();
        if (mBillingManager != null) mBillingManager.refreshPurchases();
    }

    @Override
    protected void onDestroy() {
        if (mBillingManager != null) mBillingManager.stop();
        super.onDestroy();
    }
}
