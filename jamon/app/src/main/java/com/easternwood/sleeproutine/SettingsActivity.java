package com.easternwood.sleeproutine;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Flat grouped settings screen matching the new moonlit UI. */
public class SettingsActivity extends JamonActivity {
    private static final String PRIVACY_POLICY_URL =
            "https://github.com/woodeastern2-pixel/dex-java/blob/main/privacy/jamon.md";

    private AdsConsentManager adsConsentManager;
    private boolean screenWasPro;

    private LinearLayout createGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        return group;
    }

    private LinearLayout createTextRow(CharSequence title, CharSequence body, CharSequence action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 17), 0, Ui.dp(this, 17));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        copy.addView(Ui.text(this, title, 17, Ui.TEXT, true));
        if (body != null && body.length() > 0) {
            copy.addView(Ui.text(this, body, 13, Ui.MUTED, false), Ui.matchWrap(this, 7));
        }
        if (action != null && action.length() > 0) {
            TextView actionView = Ui.text(this, action, 23, Ui.ACCENT, false);
            actionView.setGravity(Gravity.CENTER);
            row.addView(actionView, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));
        }
        return row;
    }

    private LinearLayout createProRow() {
        boolean active = Prefs.isPro(this);
        CharSequence body;
        if (active) {
            long remaining = Prefs.getProRemainingMillis(this);
            long totalMinutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
            body = getString(R.string.pro_remaining, totalMinutes / 60L, totalMinutes % 60L);
        } else {
            body = getString(R.string.settings_pro_body);
        }
        LinearLayout row = createTextRow(
                getString(active ? R.string.pro_active : R.string.pro_reward_title), body, "›");
        row.setOnClickListener(view -> startActivity(new Intent(this, ProActivity.class)));
        return row;
    }

    private LinearLayout createLanguageGroup() {
        LinearLayout group = createGroup();
        boolean english = "en".equals(Prefs.getLanguage(this));
        LinearLayout korean = languageRow(getString(R.string.language_korean), !english);
        korean.setOnClickListener(v -> switchLanguage("ko"));
        group.addView(korean);
        group.addView(Ui.divider(this));
        LinearLayout englishRow = languageRow(getString(R.string.language_english), english);
        englishRow.setOnClickListener(v -> switchLanguage("en"));
        group.addView(englishRow);
        return group;
    }

    private LinearLayout languageRow(CharSequence label, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 58));
        row.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        row.addView(Ui.text(this, label, 16, selected ? Ui.ACCENT : Ui.TEXT, selected),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = Ui.text(this, selected ? "✓" : "", 20, Ui.ACCENT, true);
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));
        return row;
    }

    private View createScreen() {
        screenWasPro = Prefs.isPro(this);
        ScrollView scroll = Ui.screen(this);
        LinearLayout column = Ui.column(this, 20);
        scroll.addView(column, new FrameLayout.LayoutParams(-1, -2));

        column.addView(Ui.sectionHeader(this, R.string.settings_title));

        column.addView(Ui.label(this, getString(R.string.pro_title)), Ui.matchWrap(this, 24));
        column.addView(createProRow(), Ui.matchWrap(this, 2));
        column.addView(Ui.divider(this));

        column.addView(Ui.label(this, getString(R.string.language)), Ui.matchWrap(this, 28));
        column.addView(createLanguageGroup(), Ui.matchWrap(this, 3));

        column.addView(Ui.label(this, getString(R.string.my_data)), Ui.matchWrap(this, 28));
        LinearLayout delete = createTextRow(
                getString(R.string.delete_sleep_history),
                getString(R.string.delete_sleep_history_body), "›");
        delete.setOnClickListener(v -> confirmDeleteHistory());
        column.addView(delete, Ui.matchWrap(this, 2));
        column.addView(Ui.divider(this));

        column.addView(Ui.label(this, getString(R.string.app_information)), Ui.matchWrap(this, 28));
        LinearLayout info = createTextRow(
                getString(R.string.app_info_title),
                getString(R.string.app_info_body), null);
        info.addView(Ui.text(this, getString(R.string.demo_build), 12, Ui.CYAN, true));
        column.addView(info, Ui.matchWrap(this, 2));
        column.addView(Ui.divider(this));

        LinearLayout credits = createTextRow(
                getString(R.string.audio_credits_title),
                getString(R.string.audio_credits_description), "›");
        credits.setOnClickListener(v -> showCredits());
        column.addView(credits);
        column.addView(Ui.divider(this));

        LinearLayout privacyPolicy = createTextRow(
                getString(R.string.privacy_policy_title),
                getString(R.string.privacy_policy_body), "›");
        privacyPolicy.setOnClickListener(view -> openPrivacyPolicy());
        column.addView(privacyPolicy);
        column.addView(Ui.divider(this));

        LinearLayout privacy = createTextRow(
                getString(R.string.ad_privacy_title),
                getString(R.string.ad_privacy_body), "›");
        privacy.setOnClickListener(view -> adsConsentManager.showPrivacyOptions(this));
        column.addView(privacy);
        column.addView(Ui.divider(this));
        return Ui.withBottomNav(this, scroll, 3);
    }

    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.privacy_policy_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_sleep_history_title)
                .setMessage(R.string.delete_sleep_history_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    Prefs.clearHistory(this);
                    Toast.makeText(this, R.string.sleep_history_deleted, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showCredits() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.audio_credits_title)
                .setMessage(R.string.audio_credits_body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void switchLanguage(String language) {
        if (language.equals(Prefs.getLanguage(this))) return;
        Prefs.setLanguage(this, language);
        recreate();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareWindow(this);
        adsConsentManager = new AdsConsentManager(this);
        setContentView(createScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (screenWasPro != Prefs.isPro(this)) {
            setContentView(createScreen());
        }
    }
}
