package com.lumi.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.lumi.app.LumiApplication;
import com.lumi.app.R;
import com.lumi.app.data.CharacterRepository;
import com.lumi.app.data.LumiSettings;
import com.lumi.app.engine.AffinityManager;
import com.lumi.app.model.CharacterStateEntity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 9120;

    private CharacterRepository repository;
    private LumiSettings settings;
    private final AffinityManager affinityManager = new AffinityManager();

    private TextView moodChip;
    private TextView stageChip;
    private TextView dailyMessage;
    private TextView affinityLabel;
    private ProgressBar affinityBar;
    private ImageView lumiOrb;
    private View orbGlow;
    private View orbParticle1;
    private View orbParticle2;
    private View orbParticle3;
    private ScrollView mainRoot;
    private ObjectAnimator orbScaleXAnimator;
    private ObjectAnimator orbScaleYAnimator;
    private ObjectAnimator orbTranslateAnimator;
    private ObjectAnimator glowPulseAnimator;
    private final List<ObjectAnimator> particleAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 최초 실행 동의 게이팅
        settings = new LumiSettings(this);
        if (!ensureLegalAccepted()) return;
        setContentView(R.layout.activity_main);
        repository = ((LumiApplication) getApplication()).getRepository();

        moodChip = findViewById(R.id.moodChip);
        stageChip = findViewById(R.id.stageChip);
        dailyMessage = findViewById(R.id.dailyMessage);
        affinityLabel = findViewById(R.id.affinityLabel);
        affinityBar = findViewById(R.id.affinityBar);
        lumiOrb = findViewById(R.id.lumiOrb);
        orbGlow = findViewById(R.id.orbGlow);
        orbParticle1 = findViewById(R.id.orbParticle1);
        orbParticle2 = findViewById(R.id.orbParticle2);
        orbParticle3 = findViewById(R.id.orbParticle3);
        mainRoot = findViewById(R.id.mainRoot);

        Button openChat = findViewById(R.id.openChat);
        Button openStatus = findViewById(R.id.openStatus);
        Button openSettings = findViewById(R.id.openSettings);
        Button openLegal = findViewById(R.id.openLegal);
        openChat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        openStatus.setOnClickListener(v -> startActivity(new Intent(this, StatusActivity.class)));
        openSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        openLegal.setOnClickListener(v -> startActivity(new Intent(this, LegalActivity.class)));

        TextView versionLabel = findViewById(R.id.versionLabel);
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionLabel.setText(getString(R.string.home_version_label, v == null ? "1.0" : v));
        } catch (PackageManager.NameNotFoundException ignored) {
            versionLabel.setText(getString(R.string.home_version_label, "1.0"));
        }

        ensureNotificationPermission();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            String perm = "android.permission.POST_NOTIFICATIONS";
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_NOTIF);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ensureLegalAccepted()) return;
        bindState(repository.loadState());
    }

    private boolean ensureLegalAccepted() {
        if (settings == null) {
            settings = new LumiSettings(this);
        }
        if (!settings.isLegalAccepted()) {
            startActivity(new Intent(this, ConsentActivity.class));
            finish();
            return false;
        }
        return true;
    }

    private void bindState(CharacterStateEntity state) {
        applyLumiAvatar();
        moodChip.setText(MoodPalette.label(state.mood));
        stageChip.setText("성장 " + state.growthStage + "단계");
        dailyMessage.setText(repository.buildDailySummary(state));
        affinityLabel.setText(affinityManager.getRelationshipLabel(state.affinity)
                + " · " + state.affinity + " / 100");
        applyMoodVisual(state.mood);
        animateProgress(affinityBar, state.affinity);
    }

    private void applyLumiAvatar() {
        if (lumiOrb == null || settings == null) return;
        switch (settings.getAvatarStyle()) {
            case LumiSettings.AVATAR_NEUTRAL:
                lumiOrb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                lumiOrb.setImageResource(R.drawable.ic_lumi_avatar_neutral);
                break;
            case LumiSettings.AVATAR_MAID:
                lumiOrb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                lumiOrb.setImageResource(R.drawable.ic_lumi_avatar_maid);
                break;
            case LumiSettings.AVATAR_CUSTOM:
                if (applyCustomAvatar()) {
                    break;
                }
                lumiOrb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                lumiOrb.setImageResource(R.drawable.ic_lumi_avatar_feminine);
                break;
            case LumiSettings.AVATAR_CLASSIC:
                lumiOrb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                lumiOrb.setImageResource(R.drawable.ic_lumi_emblem);
                break;
            case LumiSettings.AVATAR_FEMININE:
            default:
                lumiOrb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                lumiOrb.setImageResource(R.drawable.ic_lumi_avatar_feminine);
                break;
        }
    }

    private boolean applyCustomAvatar() {
        String uriString = settings.getCustomAvatarUri();
        if (uriString == null || uriString.trim().isEmpty()) {
            return false;
        }
        try {
            lumiOrb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            lumiOrb.setImageURI(Uri.parse(uriString));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void animateProgress(ProgressBar bar, int target) {
        ValueAnimator animator = ValueAnimator.ofInt(bar.getProgress(), target);
        animator.setDuration(700);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> bar.setProgress((int) a.getAnimatedValue()));
        animator.start();
    }

    private void applyMoodVisual(String mood) {
        if (mainRoot != null) {
            mainRoot.setBackgroundResource(MoodPalette.backgroundRes(mood));
        }

        if (orbScaleXAnimator != null) orbScaleXAnimator.cancel();
        if (orbScaleYAnimator != null) orbScaleYAnimator.cancel();
        if (orbTranslateAnimator != null) orbTranslateAnimator.cancel();
        if (glowPulseAnimator != null) glowPulseAnimator.cancel();

        long pulse = MoodPalette.moodPulseDuration(mood);
        long orbDuration = Math.max(1100L, pulse + 800L);

        orbScaleXAnimator = ObjectAnimator.ofFloat(lumiOrb, "scaleX", 1f, 1.04f, 1f);
        orbScaleYAnimator = ObjectAnimator.ofFloat(lumiOrb, "scaleY", 1f, 1.04f, 1f);
        orbTranslateAnimator = ObjectAnimator.ofFloat(lumiOrb, "translationY", 0f, -10f, 0f);
        orbScaleXAnimator.setDuration(orbDuration);
        orbScaleYAnimator.setDuration(orbDuration);
        orbTranslateAnimator.setDuration(orbDuration + 500L);
        orbScaleXAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbScaleYAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbTranslateAnimator.setRepeatCount(ValueAnimator.INFINITE);
        orbScaleXAnimator.start();
        orbScaleYAnimator.start();
        orbTranslateAnimator.start();

        if (orbGlow != null) {
            glowPulseAnimator = ObjectAnimator.ofFloat(orbGlow, "alpha", 0.28f, 0.62f, 0.28f);
            glowPulseAnimator.setDuration(pulse);
            glowPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            glowPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            glowPulseAnimator.start();
            orbGlow.setBackgroundResource(R.drawable.bg_lumi_orb);
            orbGlow.setBackgroundTintList(ColorStateList.valueOf(MoodPalette.color(this, mood)));
        }

        applyMoodParticles(mood, pulse);
    }

    private void applyMoodParticles(String mood, long pulse) {
        clearParticleAnimators();
        int level = particleLevel(mood);
        setParticlesVisible(level > 0);
        if (level == 0) return;

        int color = MoodPalette.color(this, mood);
        orbParticle1.setBackgroundTintList(ColorStateList.valueOf(color));
        orbParticle2.setBackgroundTintList(ColorStateList.valueOf(color));
        orbParticle3.setBackgroundTintList(ColorStateList.valueOf(color));

        float ampY = level >= 3 ? 72f : (level == 2 ? 54f : 42f);
        long base = Math.max(900L, pulse - (level * 180L));

        startParticleAnimator(orbParticle1, base, 0L, ampY, 9f, 0.20f, 0.75f);
        startParticleAnimator(orbParticle2, base + 260L, 180L, ampY - 8f, 7f, 0.15f, 0.62f);
        startParticleAnimator(orbParticle3, base + 420L, 360L, ampY - 14f, 6f, 0.12f, 0.52f);
    }

    private int particleLevel(String mood) {
        if (mood == null) return 1;
        switch (mood) {
            case "excited":
            case "thrilled":
            case "euphoric":
            case "emotional_overflow":
                return 3;
            case "joyful":
            case "happy":
            case "curious":
            case "strong_curiosity":
            case "anticipating":
            case "attached":
            case "deep_bond":
            case "affectionate":
            case "trusting":
            case "synchronized":
                return 2;
            case "lonely":
            case "sad":
            case "fearful":
            case "burnout":
            case "sleepy":
            case "calm":
            case "peaceful":
            case "spaced_out":
                return 1;
            default:
                return 1;
        }
    }

    private void setParticlesVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (orbParticle1 != null) orbParticle1.setVisibility(v);
        if (orbParticle2 != null) orbParticle2.setVisibility(v);
        if (orbParticle3 != null) orbParticle3.setVisibility(v);
    }

    private void startParticleAnimator(View target,
                                       long duration,
                                       long delay,
                                       float ampY,
                                       float ampX,
                                       float alphaMin,
                                       float alphaMax) {
        if (target == null) return;
        target.setTranslationX(0f);
        target.setTranslationY(0f);

        ObjectAnimator y = ObjectAnimator.ofFloat(target, "translationY", 0f, -ampY, 0f);
        y.setDuration(duration);
        y.setStartDelay(delay);
        y.setInterpolator(new AccelerateDecelerateInterpolator());
        y.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator x = ObjectAnimator.ofFloat(target, "translationX", -ampX, ampX, -ampX);
        x.setDuration(duration + 180L);
        x.setStartDelay(delay);
        x.setInterpolator(new AccelerateDecelerateInterpolator());
        x.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator a = ObjectAnimator.ofFloat(target, "alpha", alphaMin, alphaMax, alphaMin);
        a.setDuration(Math.max(700L, duration - 120L));
        a.setStartDelay(delay);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.setRepeatCount(ValueAnimator.INFINITE);

        y.start();
        x.start();
        a.start();
        particleAnimators.add(y);
        particleAnimators.add(x);
        particleAnimators.add(a);
    }

    private void clearParticleAnimators() {
        for (ObjectAnimator animator : particleAnimators) {
            animator.cancel();
        }
        particleAnimators.clear();
    }

    @Override
    protected void onDestroy() {
        if (orbScaleXAnimator != null) orbScaleXAnimator.cancel();
        if (orbScaleYAnimator != null) orbScaleYAnimator.cancel();
        if (orbTranslateAnimator != null) orbTranslateAnimator.cancel();
        if (glowPulseAnimator != null) glowPulseAnimator.cancel();
        clearParticleAnimators();
        super.onDestroy();
    }
}
