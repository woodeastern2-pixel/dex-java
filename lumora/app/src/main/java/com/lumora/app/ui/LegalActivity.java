package com.lumora.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lumora.app.R;
import com.lumora.app.data.LumoraSettings;

public class LegalActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "section";
    public static final String SECTION_TERMS = "terms";
    public static final String SECTION_PRIVACY = "privacy";
    public static final String SECTION_SAFETY = "safety";
    public static final String SECTION_DISCLAIMER = "disclaimer";
    public static final String SECTION_LICENSES = "licenses";
    public static final String SECTION_CONSENT = "consent";

    public static Intent intent(Context ctx, String section) {
        Intent i = new Intent(ctx, LegalActivity.class);
        i.putExtra(EXTRA_SECTION, section);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) {
            showHub();
        } else {
            showSection(section);
        }
    }

    private void showHub() {
        setContentView(R.layout.activity_legal_hub);
        findViewById(R.id.sectionTerms).setOnClickListener(v ->
                startActivity(intent(this, SECTION_TERMS)));
        findViewById(R.id.sectionPrivacy).setOnClickListener(v ->
                startActivity(intent(this, SECTION_PRIVACY)));
        findViewById(R.id.sectionSafety).setOnClickListener(v ->
                startActivity(intent(this, SECTION_SAFETY)));
        findViewById(R.id.sectionDisclaimer).setOnClickListener(v ->
                startActivity(intent(this, SECTION_DISCLAIMER)));
        findViewById(R.id.sectionLicenses).setOnClickListener(v ->
                startActivity(intent(this, SECTION_LICENSES)));
        findViewById(R.id.sectionConsent).setOnClickListener(v ->
                startActivity(intent(this, SECTION_CONSENT)));
    }

    private void showSection(String section) {
        setContentView(R.layout.activity_legal);
        TextView title = findViewById(R.id.legalTitle);
        TextView body = findViewById(R.id.legalBody);
        View divider = findViewById(R.id.legalDivider);
        TextView consentStatus = findViewById(R.id.legalConsentStatus);
        Button revoke = findViewById(R.id.btnRevoke);

        switch (section) {
            case SECTION_TERMS:
                title.setText(R.string.legal_section_terms);
                body.setText(R.string.legal_terms_body);
                break;
            case SECTION_PRIVACY:
                title.setText(R.string.legal_section_privacy);
                body.setText(R.string.legal_privacy_body);
                break;
            case SECTION_SAFETY:
                title.setText(R.string.legal_section_safety);
                body.setText(R.string.legal_safety_body);
                break;
            case SECTION_DISCLAIMER:
                title.setText(R.string.legal_section_disclaimer);
                body.setText(R.string.legal_disclaimer_body);
                break;
            case SECTION_LICENSES:
                title.setText(R.string.legal_section_licenses);
                body.setText(R.string.legal_licenses_body);
                break;
            case SECTION_CONSENT:
                LumoraSettings settings = new LumoraSettings(this);
                title.setText(R.string.legal_section_consent);
                body.setVisibility(View.GONE);
                divider.setVisibility(View.VISIBLE);
                consentStatus.setVisibility(View.VISIBLE);
                revoke.setVisibility(View.VISIBLE);
                bindConsentStatus(consentStatus, settings);
                revoke.setOnClickListener(v -> {
                    settings.revokeConsent();
                    Toast.makeText(this, R.string.legal_consent_revoked, Toast.LENGTH_LONG).show();
                    bindConsentStatus(consentStatus, settings);
                });
                break;
            default:
                title.setText(R.string.legal_title);
                body.setText("");
        }
    }

    private void bindConsentStatus(TextView tv, LumoraSettings settings) {
        int version = settings.isLegalAccepted() ? LumoraSettings.CURRENT_LEGAL_VERSION : 0;
        String yes = getString(R.string.common_yes);
        String no = getString(R.string.common_no);
        tv.setText(getString(R.string.legal_consent_status,
                version,
                settings.isLegalAccepted() ? yes : no,
                settings.isOverseasTransferConsented() ? yes : no,
                settings.isUsageConsented() ? yes : no,
                settings.isLocationConsented() ? yes : no));
    }
}
