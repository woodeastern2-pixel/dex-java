package com.lumi.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lumi.app.R;
import com.lumi.app.data.LumiSettings;

public class ConsentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        LumiSettings settings = new LumiSettings(this);

        CheckBox cbTerms = findViewById(R.id.cbTerms);
        CheckBox cbAge = findViewById(R.id.cbAge);
        CheckBox cbOverseas = findViewById(R.id.cbOverseas);
        CheckBox cbVoice = findViewById(R.id.cbVoice);
        Button btnContinue = findViewById(R.id.btnContinue);
        Button btnDecline = findViewById(R.id.btnDecline);
        Button btnViewTerms = findViewById(R.id.btnViewTerms);
        Button btnViewPrivacy = findViewById(R.id.btnViewPrivacy);

        btnViewTerms.setOnClickListener(v ->
                startActivity(LegalActivity.intent(this, LegalActivity.SECTION_TERMS)));
        btnViewPrivacy.setOnClickListener(v ->
                startActivity(LegalActivity.intent(this, LegalActivity.SECTION_PRIVACY)));

        btnContinue.setOnClickListener(v -> {
            if (!cbTerms.isChecked() || !cbAge.isChecked()) {
                Toast.makeText(this, R.string.consent_required_toast, Toast.LENGTH_LONG).show();
                return;
            }
            settings.acceptLegal(cbAge.isChecked(), cbOverseas.isChecked(), cbVoice.isChecked());
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        btnDecline.setOnClickListener(v -> finishAffinity());
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}
