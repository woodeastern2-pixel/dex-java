package com.lumi.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lumi.app.R;
import com.lumi.app.data.LumiSettings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 약관/개인정보/면책/안전/라이선스/동의 화면.
 *
 * extras EXTRA_SECTION 값이 없으면 허브를 표시하고, 값이 있으면 해당 섹션 본문을 바로 표시한다.
 * 동의 철회 화면(SECTION_CONSENT)은 본문 대신 동의 상태와 철회 버튼을 노출한다.
 */
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
                body.setText(readRawText(R.raw.terms_ko, R.string.legal_terms_body));
                break;
            case SECTION_PRIVACY:
                title.setText(R.string.legal_section_privacy);
                body.setText(readRawText(R.raw.privacy_ko, R.string.legal_privacy_body));
                break;
            case SECTION_SAFETY:
                title.setText(R.string.legal_section_safety);
                body.setText(readRawText(R.raw.safety_ko, R.string.legal_safety_body));
                break;
            case SECTION_DISCLAIMER:
                title.setText(R.string.legal_section_disclaimer);
                body.setText(readRawText(R.raw.disclaimer_ko, R.string.legal_disclaimer_body));
                break;
            case SECTION_LICENSES:
                title.setText(R.string.legal_section_licenses);
                body.setText(readRawText(R.raw.open_source_licenses, R.string.legal_licenses_body));
                break;
            case SECTION_CONSENT:
                LumiSettings settings = new LumiSettings(this);
                title.setText(R.string.legal_section_consent);
                body.setVisibility(View.GONE);
                divider.setVisibility(View.VISIBLE);
                consentStatus.setVisibility(View.VISIBLE);
                revoke.setVisibility(View.VISIBLE);
                bindConsentStatus(consentStatus, settings);
                revoke.setOnClickListener(v -> {
                    settings.revokeConsent();
                    Toast.makeText(this, R.string.legal_consent_revoked, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, ConsentActivity.class));
                    finish();
                });
                break;
            default:
                title.setText(R.string.legal_title);
                body.setText("");
        }
    }

    private void bindConsentStatus(TextView tv, LumiSettings settings) {
        int version = settings.getAcceptedLegalVersion();
        String yes = getString(R.string.common_yes);
        String no = getString(R.string.common_no);
        tv.setText(getString(R.string.legal_consent_status,
                version,
                settings.isLegalAccepted() ? yes : no,
                settings.isAgeConfirmed() ? yes : no,
                settings.isOverseasTransferConsented() ? yes : no,
                settings.isVoiceTransferConsented() ? yes : no));
    }

    private String readRawText(int rawResId, int fallbackResId) {
        try (InputStream in = getResources().openRawResource(rawResId);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name()).trim();
        } catch (Exception e) {
            return getString(fallbackResId);
        }
    }
}
