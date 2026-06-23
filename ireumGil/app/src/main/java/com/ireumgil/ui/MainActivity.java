package com.ireumgil.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ireumgil.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnRecommend = findViewById(R.id.btnRecommend);
        Button btnCreateHanja = findViewById(R.id.btnCreateHanja);
        Button btnFortune = findViewById(R.id.btnFortune);

        btnRecommend.setOnClickListener(v -> startActivity(new Intent(this, RecommendNameActivity.class)));
        btnCreateHanja.setOnClickListener(v -> startActivity(new Intent(this, CreateHanjaNameActivity.class)));
        btnFortune.setOnClickListener(v -> startActivity(new Intent(this, CheckNameFortuneActivity.class)));

        showOnboardingIfNeeded();
    }

    private void showOnboardingIfNeeded() {
        SharedPreferences pref = getSharedPreferences("ireumgil_info", MODE_PRIVATE);
        boolean shown = pref.getBoolean("disclaimer_shown", false);
        if (shown) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("안내")
                .setMessage("본 앱은 전통 작명 기준에 따른 참고용 해석을 제공합니다.\n\n" +
                        "본 결과는 전통 작명 이론을 참고한 문화적 해석이며, 절대적인 판단 기준이 아닙니다.")
                .setPositiveButton("확인", (d, w) -> pref.edit().putBoolean("disclaimer_shown", true).apply())
                .show();
    }
}
