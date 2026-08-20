package com.ireumgil.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ireumgil.R;
import com.ireumgil.data.PopularNameRepository;
import com.ireumgil.BuildConfig;
import com.ireumgil.monetization.AdsConsentManager;
import com.ireumgil.monetization.ProBillingManager;
import com.ireumgil.model.PopularName;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ProBillingManager billingManager;
    private ProBillingManager.State billingState;
    private AdsConsentManager adsConsentManager;
    private boolean consentResolved;
    private boolean adsAllowed;
    private boolean monetizationStarted;
    private AdView bannerAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Button btnRecommend = findViewById(R.id.btnRecommend);
        Button btnCreateHanja = findViewById(R.id.btnCreateHanja);
        Button btnFortune = findViewById(R.id.btnFortune);

        btnRecommend.setOnClickListener(v -> startActivity(new Intent(this, RecommendNameActivity.class)));
        btnCreateHanja.setOnClickListener(v -> startActivity(new Intent(this, CreateHanjaNameActivity.class)));
        btnFortune.setOnClickListener(v -> startActivity(new Intent(this, CheckNameFortuneActivity.class)));
        findViewById(R.id.cardFortune).setOnClickListener(v ->
                startActivity(new Intent(this, CheckNameFortuneActivity.class)));
        findViewById(R.id.btnPro).setOnClickListener(v -> {
            if (billingState != null && billingState.pro) {
                Toast.makeText(this, "이름온 Pro가 활성화되어 있습니다.", Toast.LENGTH_SHORT).show();
            } else if (billingManager != null) {
                billingManager.launchPurchase();
            }
        });

        renderPopularNames();

        showOnboardingIfNeeded();
    }

    private void renderPopularNames() {
        PopularNameRepository repository = new PopularNameRepository(this);
        renderRanking(findViewById(R.id.layoutMaleRanking), repository.topTen("M"));
        renderRanking(findViewById(R.id.layoutFemaleRanking), repository.topTen("F"));

        TextView period = findViewById(R.id.textRankingPeriod);
        if (!repository.latestPeriod().isEmpty()) {
            period.setText("2008.01–" + repository.latestPeriod().replace('-', '.')
                    + " 누적 출생신고 · " + repository.updatedAt() + " 갱신");
        }
    }

    private void renderRanking(LinearLayout container, List<PopularName> items) {
        container.removeAllViews();
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.KOREA);
        for (PopularName item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));

            TextView rank = rankingText(String.valueOf(item.rank), 12, R.color.brand_primary);
            rank.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(rank, new LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView name = rankingText(item.name, 14, R.color.deep_navy);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView count = rankingText(number.format(item.count), 10, R.color.charcoal);
            row.addView(count);
            container.addView(row);
        }
        if (items.isEmpty()) {
            container.addView(rankingText("데이터 준비 중", 12, R.color.charcoal));
        }
    }

    private TextView rankingText(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(getColor(color));
        text.setSingleLine(true);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void renderBillingState(ProBillingManager.State state) {
        billingState = state;
        TextView pro = findViewById(R.id.btnPro);
        if (state.pro) {
            pro.setText("PRO ✓");
        } else if (state.price != null && !state.price.isEmpty()) {
            pro.setText("PRO · " + state.price);
        } else {
            pro.setText(R.string.pro_label);
        }
        updateBanner();
    }

    private void startMonetization() {
        if (monetizationStarted || isFinishing() || isDestroyed()) return;
        monetizationStarted = true;
        billingManager = new ProBillingManager(this, this::renderBillingState);
        adsConsentManager = new AdsConsentManager(this);
        billingManager.start();
        adsConsentManager.gatherConsent(this, allowed -> {
            consentResolved = true;
            adsAllowed = allowed;
            updateBanner();
        });
    }

    private void updateBanner() {
        FrameLayout container = findViewById(R.id.adContainer);
        boolean billingReady = billingState != null && billingState.ready;
        boolean pro = billingState != null && billingState.pro;
        boolean show = consentResolved && adsAllowed && billingReady && !pro;
        if (!show) {
            container.setVisibility(View.GONE);
            if (pro) destroyBanner();
            return;
        }
        if (bannerAd == null) {
            bannerAd = new AdView(this);
            bannerAd.setAdSize(AdSize.BANNER);
            bannerAd.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            container.removeAllViews();
            container.addView(bannerAd);
            bannerAd.loadAd(new AdRequest.Builder().build());
        }
        container.setVisibility(View.VISIBLE);
    }

    private void destroyBanner() {
        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }
        FrameLayout container = findViewById(R.id.adContainer);
        container.removeAllViews();
        container.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (billingManager != null) billingManager.refresh();
    }

    @Override
    protected void onDestroy() {
        destroyBanner();
        if (billingManager != null) billingManager.stop();
        super.onDestroy();
    }

    private void showOnboardingIfNeeded() {
        SharedPreferences pref = getSharedPreferences("ireumgil_info", MODE_PRIVATE);
        boolean shown = pref.getBoolean("disclaimer_shown", false);
        if (shown) {
            startMonetization();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("안내")
                .setMessage("본 앱은 전통 작명 기준에 따른 참고용 해석을 제공합니다.\n\n" +
                        "본 결과는 전통 작명 이론을 참고한 문화적 해석이며, 절대적인 판단 기준이 아닙니다.")
                .setPositiveButton("확인", (d, w) -> {
                    pref.edit().putBoolean("disclaimer_shown", true).apply();
                    startMonetization();
                })
                .show();
    }
}
