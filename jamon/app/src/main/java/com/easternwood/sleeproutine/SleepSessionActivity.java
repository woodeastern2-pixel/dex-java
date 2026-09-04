package com.easternwood.sleeproutine;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class SleepSessionActivity extends JamonActivity {
    public static final String EXTRA_DURATION_MINUTES = "duration_minutes";
    public static final String EXTRA_ROUTINE_GUIDANCE = "routine_guidance";
    public static final String EXTRA_ROUTINE_TITLE = "routine_title";
    public static final String EXTRA_SOUND_NAMES = "sound_names";
    public static final String EXTRA_SOUND_RESOURCES = "sound_resources";
    public static final String EXTRA_VOLUMES = "sound_volumes";
    private long endsAt;
    private boolean finishingSession;
    private TextView pauseButton;
    private boolean paused;
    private long pausedRemaining;
    private ProgressBar progress;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() { // from class: com.easternwood.sleeproutine.SleepSessionActivity$$ExternalSyntheticLambda3
        @Override // java.lang.Runnable
        public final void run() {
            SleepSessionActivity.this.updateTimer();
        }
    };
    private TextView timerView;
    private long totalMillis;

    private LinearLayout createMixCard(String[] strArr, float[] fArr) {
        LinearLayout linearLayoutCard = Ui.card(this);
        linearLayoutCard.addView(Ui.text(this, getString(R.string.sound_mix), 18.0f, Ui.TEXT, true));
        linearLayoutCard.addView(Ui.text(this, getString(R.string.sound_mix_live_support), 12.0f, Ui.MUTED, false), Ui.matchWrap(this, 5));
        for (int index = 0; index < strArr.length; index++) {
            final int i = index;
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setGravity(Gravity.CENTER_VERTICAL);
            linearLayoutCard.addView(linearLayout, Ui.matchWrap(this, 17));
            linearLayout.addView(Ui.text(this, strArr[i], 14.0f, Ui.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1.0f));
            final TextView textViewText = Ui.text(this, getString(R.string.volume_percent, new Object[]{Integer.valueOf(Math.round(fArr[i] * 100.0f))}), 12.0f, Ui.ACCENT, true);
            linearLayout.addView(textViewText);
            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(100);
            seekBar.setProgress(Math.round(fArr[i] * 100.0f));
            seekBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            seekBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.easternwood.sleeproutine.SleepSessionActivity.1
                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onProgressChanged(SeekBar seekBar2, int i2, boolean z) {
                    int iMax = Math.max(2, i2);
                    textViewText.setText(SleepSessionActivity.this.getString(R.string.volume_percent, new Object[]{Integer.valueOf(iMax)}));
                    if (z) {
                        SleepSessionActivity.this.setTrackVolume(i, iMax / 100.0f);
                    }
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStartTrackingTouch(SeekBar seekBar2) {
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStopTrackingTouch(SeekBar seekBar2) {
                }
            });
            linearLayoutCard.addView(seekBar, Ui.matchWrap(this, 3));
        }
        return linearLayoutCard;
    }

    private ScrollView createSession(String str, String str2, String[] strArr, float[] fArr) {
        ScrollView scrollViewScreen = Ui.screen(this);
        LinearLayout linearLayoutColumn = Ui.column(this, 24);
        linearLayoutColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollViewScreen.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -2));
        TextView textViewText = Ui.text(this, "×", 34.0f, Ui.MUTED, false);
        textViewText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        textViewText.setContentDescription(getString(R.string.finish));
        textViewText.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SleepSessionActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SleepSessionActivity.this.lambda$createSession$0(view);
            }
        });
        linearLayoutColumn.addView(textViewText, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52.0f)));
        ImageView imageViewIcon = Ui.icon(this, 88);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.dp(this, 88.0f), Ui.dp(this, 88.0f));
        layoutParams.topMargin = Ui.dp(this, 22.0f);
        linearLayoutColumn.addView(imageViewIcon, layoutParams);
        TextView textViewText2 = Ui.text(this, str, 24.0f, Ui.TEXT, true);
        textViewText2.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(textViewText2, Ui.matchWrap(this, 22));
        TextView textViewText3 = Ui.text(this, str2, 14.0f, Ui.MUTED, false);
        textViewText3.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(textViewText3, Ui.matchWrap(this, 10));
        TextView textViewLabel = Ui.label(this, getString(R.string.remaining_time));
        textViewLabel.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(textViewLabel, Ui.matchWrap(this, 36));
        this.timerView = Ui.text(this, "00:00", 52.0f, Ui.TEXT, false);
        this.timerView.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(this.timerView, Ui.matchWrap(this, 4));
        this.progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.progress.setMax((int) Math.max(1L, this.totalMillis / 1000));
        this.progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        this.progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.CARD_SOFT));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, Ui.dp(this, 8.0f));
        layoutParams2.topMargin = Ui.dp(this, 16.0f);
        linearLayoutColumn.addView(this.progress, layoutParams2);
        linearLayoutColumn.addView(createMixCard(strArr, fArr), Ui.matchWrap(this, 28));
        this.pauseButton = Ui.button(this, getString(R.string.pause), false);
        this.pauseButton.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SleepSessionActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SleepSessionActivity.this.lambda$createSession$1(view);
            }
        });
        linearLayoutColumn.addView(this.pauseButton, Ui.matchWrap(this, 18));
        TextView textViewButton = Ui.button(this, getString(R.string.finish), true);
        textViewButton.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SleepSessionActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SleepSessionActivity.this.lambda$createSession$2(view);
            }
        });
        linearLayoutColumn.addView(textViewButton, Ui.matchWrap(this, 10));
        return scrollViewScreen;
    }

    private void finishSession() {
        if (this.finishingSession) {
            return;
        }
        this.finishingSession = true;
        this.timerHandler.removeCallbacks(this.timerTick);
        Intent intent = new Intent(this, (Class<?>) PlaybackService.class);
        intent.setAction("com.easternwood.sleeproutine.STOP");
        startService(intent);
        finish();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSession$0(View view) {
        finishSession();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSession$1(View view) {
        togglePlayback();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSession$2(View view) {
        finishSession();
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            return;
        }
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 30);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setTrackVolume(int i, float f) {
        Intent intent = new Intent(this, (Class<?>) PlaybackService.class);
        intent.setAction("com.easternwood.sleeproutine.SET_VOLUME");
        intent.putExtra("service_sound_index", i);
        intent.putExtra("service_volume", f);
        startService(intent);
    }

    private void startPlayback(int[] iArr, String[] strArr, float[] fArr) {
        Intent intent = new Intent(this, (Class<?>) PlaybackService.class);
        intent.setAction("com.easternwood.sleeproutine.PLAY");
        intent.putExtra("service_sound_resources", iArr);
        intent.putExtra("service_sound_names", strArr);
        intent.putExtra("service_volumes", fArr);
        startForegroundService(intent);
    }

    private void togglePlayback() {
        this.paused = !this.paused;
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (this.paused) {
            this.pausedRemaining = Math.max(0L, this.endsAt - jElapsedRealtime);
        } else {
            this.endsAt = jElapsedRealtime + this.pausedRemaining;
        }
        Intent intent = new Intent(this, (Class<?>) PlaybackService.class);
        intent.setAction("com.easternwood.sleeproutine.TOGGLE");
        startService(intent);
        this.pauseButton.setText(this.paused ? R.string.resume : R.string.pause);
        updateTimer();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTimer() {
        this.timerHandler.removeCallbacks(this.timerTick);
        long jMax = this.paused ? this.pausedRemaining : Math.max(0L, this.endsAt - SystemClock.elapsedRealtime());
        long j = (999 + jMax) / 1000;
        long j2 = j / 60;
        long j3 = j % 60;
        if (j >= 3600) {
            this.timerView.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", Long.valueOf(j / 3600), Long.valueOf((j % 3600) / 60), Long.valueOf(j3)));
        } else {
            this.timerView.setText(String.format(Locale.getDefault(), "%02d:%02d", Long.valueOf(j2), Long.valueOf(j3)));
        }
        this.progress.setProgress((int) Math.min(2147483647L, Math.max(0L, this.totalMillis - jMax) / 1000));
        if (jMax <= 0) {
            finishSession();
        } else {
            if (this.paused) {
                return;
            }
            this.timerHandler.postDelayed(this.timerTick, 1000L);
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.prepareWindow(this);
        int[] intArrayExtra = getIntent().getIntArrayExtra(EXTRA_SOUND_RESOURCES);
        String[] stringArrayExtra = getIntent().getStringArrayExtra(EXTRA_SOUND_NAMES);
        float[] floatArrayExtra = getIntent().getFloatArrayExtra(EXTRA_VOLUMES);
        if (intArrayExtra == null || intArrayExtra.length == 0) {
            intArrayExtra = new int[]{R.raw.brown_noise};
            stringArrayExtra = new String[]{getString(R.string.sound_brown)};
            floatArrayExtra = new float[]{0.6f};
        }
        if (stringArrayExtra == null || stringArrayExtra.length != intArrayExtra.length) {
            stringArrayExtra = new String[intArrayExtra.length];
            for (int i = 0; i < intArrayExtra.length; i++) {
                stringArrayExtra[i] = getString(SoundCatalog.byRaw(intArrayExtra[i]).nameRes);
            }
        }
        if (floatArrayExtra == null || floatArrayExtra.length != intArrayExtra.length) {
            int length = intArrayExtra.length;
            float[] fArr = new float[length];
            for (int i2 = 0; i2 < length; i2++) {
                fArr[i2] = 0.55f;
            }
            floatArrayExtra = fArr;
        }
        int iMax = Math.max(1, Math.min(720, getIntent().getIntExtra(EXTRA_DURATION_MINUTES, 20)));
        String stringExtra = getIntent().getStringExtra(EXTRA_ROUTINE_TITLE);
        String stringExtra2 = getIntent().getStringExtra(EXTRA_ROUTINE_GUIDANCE);
        if (stringExtra == null) {
            stringExtra = getString(R.string.routine_name_0);
        }
        if (stringExtra2 == null) {
            stringExtra2 = getString(R.string.session_guidance);
        }
        this.totalMillis = ((long) iMax) * 60000;
        setContentView(createSession(stringExtra, stringExtra2, stringArrayExtra, floatArrayExtra));
        requestNotificationsIfNeeded();
        startPlayback(intArrayExtra, stringArrayExtra, floatArrayExtra);
        Prefs.recordSession(this, iMax);
        this.endsAt = SystemClock.elapsedRealtime() + this.totalMillis;
        updateTimer();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.timerHandler.removeCallbacks(this.timerTick);
        super.onDestroy();
    }
}
