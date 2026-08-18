package com.lumora.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lumora.app.R;
import com.lumora.app.data.LumoraSettings;

public class ConsentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);

        LumoraSettings settings = new LumoraSettings(this);

        CheckBox cbTerms = findViewById(R.id.cbTerms);
        CheckBox cbAge = findViewById(R.id.cbAge);
        CheckBox cbOverseas = findViewById(R.id.cbOverseas);
        CheckBox cbUsage = findViewById(R.id.cbUsage);
        CheckBox cbLocation = findViewById(R.id.cbLocation);
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
            settings.acceptLegal(cbAge.isChecked(), cbOverseas.isChecked(),
                    cbUsage.isChecked(), cbLocation.isChecked());
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
