package com.easternwood.sleeproutine;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Opt-in rewarded flow for a local 24-hour Pro pass. */
public class ProActivity extends JamonActivity implements RewardedProManager.Listener {
    private AdsConsentManager consentManager;
    private RewardedProManager rewardedManager;
    private TextView actionButton;
    private TextView statusView;
    private boolean consentResolved;
    private boolean adsAllowed;
    private boolean consentStarted;
    private boolean screenWasPro;

    private void addFeature(LinearLayout parent, String number, int textRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView index = Ui.label(this, number);
        index.setGravity(Gravity.CENTER);
        row.addView(index, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        TextView copy = Ui.text(this, getString(textRes), 15, Ui.TEXT, true);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = Ui.dp(this, 13);
        row.addView(copy, copyParams);
        parent.addView(row, Ui.matchWrap(this, parent.getChildCount() == 0 ? 0 : 15));
    }

    private ScrollView createScreen() {
        screenWasPro = Prefs.isPro(this);
        ScrollView scroll = Ui.screen(this);
        LinearLayout column = Ui.column(this, 20);
        scroll.addView(column, new FrameLayout.LayoutParams(-1, -2));
        column.addView(Ui.sectionHeader(this, R.string.pro_title));

        LinearLayout hero = Ui.heroCard(this);
        hero.addView(Ui.label(this, screenWasPro ? getString(R.string.pro_active) : "24H PRO"));
        hero.addView(Ui.text(this,
                getString(screenWasPro ? R.string.pro_active_title : R.string.pro_reward_title),
                25, screenWasPro ? Ui.MOON : Ui.TEXT, true), Ui.matchWrap(this, 13));
        hero.addView(Ui.text(this, getString(R.string.pro_reward_body), 14, Ui.MUTED, false),
                Ui.matchWrap(this, 9));
        statusView = Ui.text(this, "", 13, screenWasPro ? Ui.CYAN : Ui.MUTED, true);
        hero.addView(statusView, Ui.matchWrap(this, 14));

        actionButton = Ui.button(this, "", true);
        actionButton.setOnClickListener(view -> onPrimaryAction());
        hero.addView(actionButton, Ui.matchWrap(this, 20));

        TextView continueFree = Ui.button(this, getString(R.string.continue_free), false);
        continueFree.setOnClickListener(view -> finish());
        hero.addView(continueFree, Ui.matchWrap(this, 9));
        if (BuildConfig.USING_TEST_ADS) {
            hero.addView(Ui.text(this, getString(R.string.test_ads_notice), 11, Ui.CYAN, true),
                    Ui.matchWrap(this, 12));
        }
        column.addView(hero, Ui.matchWrap(this, 20));

        LinearLayout features = Ui.card(this);
        addFeature(features, "01", R.string.pro_feature_1);
        addFeature(features, "02", R.string.pro_feature_2);
        addFeature(features, "03", R.string.pro_feature_3);
        column.addView(features, Ui.matchWrap(this, 16));

        column.addView(Ui.text(this, getString(R.string.pro_ad_rule), 12, Ui.MUTED, false),
                Ui.matchWrap(this, 17));
        updateUi();
        return scroll;
    }

    private void onPrimaryAction() {
        if (Prefs.isPro(this)) {
            startActivity(new Intent(this, SoundLibraryActivity.class));
            return;
        }
        if (!consentResolved) {
            Toast.makeText(this, R.string.ad_privacy_checking, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adsAllowed) {
            Toast.makeText(this, R.string.reward_ad_consent_required, Toast.LENGTH_LONG).show();
            return;
        }
        rewardedManager.show();
    }

    private void startConsentIfNeeded() {
        if (Prefs.isPro(this) || consentStarted) {
            return;
        }
        consentStarted = true;
        consentManager.gatherConsent(this, allowed -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            consentResolved = true;
            adsAllowed = allowed;
            updateUi();
            if (allowed) {
                rewardedManager.load();
            }
        });
    }

    private void updateUi() {
        if (actionButton == null || statusView == null) {
            return;
        }
        if (Prefs.isPro(this)) {
            long remaining = Prefs.getProRemainingMillis(this);
            long totalMinutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
            long hours = totalMinutes / 60L;
            long minutes = totalMinutes % 60L;
            statusView.setText(getString(R.string.pro_remaining, hours, minutes));
            actionButton.setText(R.string.pro_open_sounds);
            actionButton.setEnabled(true);
            return;
        }

        statusView.setText(R.string.pro_reward_status);
        if (!consentResolved) {
            actionButton.setText(R.string.reward_ad_preparing);
            actionButton.setEnabled(false);
        } else if (!adsAllowed) {
            actionButton.setText(R.string.reward_ad_unavailable);
            actionButton.setEnabled(false);
        } else if (rewardedManager.isLoading()) {
            actionButton.setText(R.string.reward_ad_loading);
            actionButton.setEnabled(false);
        } else if (rewardedManager.isReady()) {
            actionButton.setText(R.string.reward_watch_button);
            actionButton.setEnabled(true);
        } else {
            actionButton.setText(R.string.reward_ad_retry);
            actionButton.setEnabled(true);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareWindow(this);
        consentManager = new AdsConsentManager(this);
        rewardedManager = new RewardedProManager(this, this);
        setContentView(createScreen());
        startConsentIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screenWasPro != Prefs.isPro(this)) {
            setContentView(createScreen());
        } else {
            updateUi();
        }
        startConsentIfNeeded();
    }

    @Override
    protected void onDestroy() {
        rewardedManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onStateChanged() {
        updateUi();
    }

    @Override
    public void onRewardGranted() {
        Toast.makeText(this, R.string.pro_granted, Toast.LENGTH_LONG).show();
        setContentView(createScreen());
    }

    @Override
    public void onMessage(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
    }
}
