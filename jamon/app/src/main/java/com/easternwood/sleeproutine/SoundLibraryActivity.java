package com.easternwood.sleeproutine;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.ViewGroup;

/* JADX INFO: loaded from: classes.dex */
public class SoundLibraryActivity extends JamonActivity {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_NATURE = 1;
    private static final int FILTER_NOISE = 2;
    private static final int FILTER_INDOOR = 3;
    private static final int FILTER_PRO = 4;
    private static final long PREVIEW_MS = 30000;
    private TextView activePreviewButton;
    private LinearLayout listContainer;
    private MediaPlayer previewPlayer;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Runnable previewTimeout = new Runnable() { // from class: com.easternwood.sleeproutine.SoundLibraryActivity$$ExternalSyntheticLambda3
        @Override // java.lang.Runnable
        public final void run() {
            SoundLibraryActivity.this.stopPreview();
        }
    };
    private int filter = FILTER_ALL;
    private boolean lastProState;

    private void addFilter(final LinearLayout linearLayout, int i, final int i2) {
        TextView textViewChip = Ui.chip(this, getString(i), i2 == this.filter);
        textViewChip.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SoundLibraryActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SoundLibraryActivity.this.lambda$addFilter$0(i2, linearLayout, view);
            }
        });
        linearLayout.addView(textViewChip, Ui.wrapWrap(this, 9));
    }

    private HorizontalScrollView createFilters() {
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(FILTER_ALL);
        horizontalScrollView.addView(linearLayout);
        addFilter(linearLayout, R.string.filter_all, FILTER_ALL);
        addFilter(linearLayout, R.string.filter_nature, FILTER_NATURE);
        addFilter(linearLayout, R.string.filter_noise, FILTER_NOISE);
        addFilter(linearLayout, R.string.filter_indoor, FILTER_INDOOR);
        addFilter(linearLayout, R.string.filter_pro, FILTER_PRO);
        return horizontalScrollView;
    }

    private View createScreen() {
        ScrollView scrollViewScreen = Ui.screen(this);
        LinearLayout linearLayoutColumn = Ui.column(this, 16);
        scrollViewScreen.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -2));
        linearLayoutColumn.addView(Ui.sectionHeader(this, R.string.sound_library_title));
        linearLayoutColumn.addView(Ui.text(this, getString(R.string.library_heading), 23.0f, Ui.TEXT, true), Ui.matchWrap(this, 16));
        linearLayoutColumn.addView(Ui.text(this, getString(R.string.library_preview_pro), 13.0f, Ui.MUTED, false), Ui.matchWrap(this, 7));
        linearLayoutColumn.addView(createFilters(), Ui.matchWrap(this, 15));
        this.listContainer = new LinearLayout(this);
        this.listContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayoutColumn.addView(this.listContainer, Ui.matchWrap(this, 7));
        renderSounds();
        return Ui.withBottomNav(this, scrollViewScreen, 1);
    }

    private LinearLayout createSoundCard(final SoundCatalog.Sound sound) {
        LinearLayout linearLayoutCard = new LinearLayout(this);
        linearLayoutCard.setOrientation(LinearLayout.VERTICAL);
        linearLayoutCard.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 7));
        linearLayoutCard.setBackground(Ui.filledBackground(Ui.CARD, 7, this));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(FILTER_ALL);
        linearLayout.setGravity(16);
        linearLayoutCard.addView(linearLayout);
        ImageView artwork = Ui.artwork(this, sound.imageRes, 84, 5);
        linearLayout.addView(artwork, new LinearLayout.LayoutParams(Ui.dp(this, 84.0f), Ui.dp(this, 84.0f)));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(FILTER_ALL, -2, 1.0f);
        layoutParams.leftMargin = Ui.dp(this, 14.0f);
        linearLayout.addView(linearLayout3, layoutParams);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(FILTER_ALL);
        linearLayout4.setGravity(16);
        linearLayout3.addView(linearLayout4);
        linearLayout4.addView(Ui.text(this, getString(sound.nameRes), 16.0f, Ui.TEXT, true), new LinearLayout.LayoutParams(FILTER_ALL, -2, 1.0f));
        linearLayout4.addView(Ui.pill(this, sound.pro ? "PRO" : "FREE", sound.pro ? Ui.MOON : Ui.CYAN,
                sound.pro ? Color.rgb(54, 40, 63) : Color.rgb(14, 47, 61), Color.TRANSPARENT));
        linearLayout3.addView(Ui.text(this, getString(sound.descriptionRes), 11.5f, Ui.MUTED, false), Ui.matchWrap(this, 5));
        linearLayout3.addView(Ui.text(this, getString(sound.actualRecording ? R.string.actual_recording : R.string.spectral_noise), 10.5f, sound.actualRecording ? Ui.ACCENT : Ui.MUTED, true), Ui.matchWrap(this, 5));
        final TextView textViewText = Ui.text(this, "▶", 20.0f, -1, true);
        textViewText.setGravity(17);
        textViewText.setContentDescription(getString(R.string.preview));
        textViewText.setBackground(Ui.circle(Ui.PURPLE, FILTER_ALL, Ui.PURPLE, this));
        textViewText.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SoundLibraryActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SoundLibraryActivity.this.lambda$createSoundCard$1(sound, textViewText, view);
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.dp(this, 52.0f), Ui.dp(this, 52.0f));
        layoutParams2.leftMargin = Ui.dp(this, 10.0f);
        linearLayout.addView(textViewText, layoutParams2);
        TextView textViewText2 = Ui.text(this, getString((!sound.pro || Prefs.isPro(this)) ? R.string.start_sound_arrow : R.string.start_pro_sound_arrow), 13.0f, Ui.ACCENT, true);
        textViewText2.setGravity(16);
        textViewText2.setMinHeight(Ui.dp(this, 46.0f));
        textViewText2.setPadding(Ui.dp(this, 2.0f), Ui.dp(this, 10.0f), Ui.dp(this, 2.0f), FILTER_ALL);
        textViewText2.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.SoundLibraryActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SoundLibraryActivity.this.lambda$createSoundCard$2(sound, view);
            }
        });
        linearLayoutCard.addView(textViewText2, Ui.matchWrap(this, 3));
        linearLayoutCard.setLayoutParams(Ui.matchWrap(this, 5));
        return linearLayoutCard;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addFilter$0(int i, LinearLayout linearLayout, View view) {
        this.filter = i;
        int i2 = FILTER_ALL;
        while (i2 < linearLayout.getChildCount()) {
            Ui.selectChip((TextView) linearLayout.getChildAt(i2), i2 == i, this);
            i2++;
        }
        stopPreview();
        renderSounds();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSoundCard$1(SoundCatalog.Sound sound, TextView textView, View view) {
        togglePreview(sound, textView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSoundCard$2(SoundCatalog.Sound sound, View view) {
        stopPreview();
        if (!sound.pro || Prefs.isPro(this)) {
            startSession(sound);
        } else {
            startActivity(new Intent(this, (Class<?>) ProActivity.class));
        }
    }

    private void renderSounds() {
        this.listContainer.removeAllViews();
        for (SoundCatalog.Sound sound : SoundCatalog.ALL) {
            if (matchesFilter(sound)) {
                this.listContainer.addView(createSoundCard(sound));
            }
        }
    }

    private boolean matchesFilter(SoundCatalog.Sound sound) {
        if (filter == FILTER_ALL) return true;
        if (filter == FILTER_PRO) return sound.pro;
        if (filter == FILTER_NOISE) {
            return "brown".equals(sound.id) || "pink".equals(sound.id) || "white".equals(sound.id);
        }
        if (filter == FILTER_INDOOR) {
            return "fan".equals(sound.id) || "fireplace".equals(sound.id) || "train".equals(sound.id);
        }
        return !"brown".equals(sound.id) && !"pink".equals(sound.id)
                && !"white".equals(sound.id) && !"fan".equals(sound.id)
                && !"fireplace".equals(sound.id) && !"train".equals(sound.id);
    }

    private void startSession(SoundCatalog.Sound sound) {
        Intent intent = new Intent(this, (Class<?>) SleepSessionActivity.class);
        intent.putExtra(SleepSessionActivity.EXTRA_SOUND_RESOURCES, new int[]{sound.rawRes});
        intent.putExtra(SleepSessionActivity.EXTRA_SOUND_NAMES, new String[]{getString(sound.nameRes)});
        intent.putExtra(SleepSessionActivity.EXTRA_VOLUMES, new float[]{0.62f});
        intent.putExtra(SleepSessionActivity.EXTRA_DURATION_MINUTES, 20);
        intent.putExtra(SleepSessionActivity.EXTRA_ROUTINE_TITLE, getString(R.string.chosen_sound_routine));
        intent.putExtra(SleepSessionActivity.EXTRA_ROUTINE_GUIDANCE, getString(R.string.session_guidance));
        startActivity(intent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopPreview() {
        this.previewHandler.removeCallbacks(this.previewTimeout);
        if (this.previewPlayer != null) {
            try {
                this.previewPlayer.stop();
            } catch (RuntimeException e) {
            }
            this.previewPlayer.release();
            this.previewPlayer = null;
        }
        if (this.activePreviewButton != null) {
            this.activePreviewButton.setText("▶");
            this.activePreviewButton.setBackground(Ui.circle(Ui.PURPLE, FILTER_ALL, Ui.PURPLE, this));
            this.activePreviewButton = null;
        }
    }

    private void togglePreview(SoundCatalog.Sound sound, TextView textView) {
        if (this.activePreviewButton == textView && this.previewPlayer != null) {
            stopPreview();
            return;
        }
        stopPreview();
        try {
            this.previewPlayer = MediaPlayer.create(this, sound.rawRes);
            if (this.previewPlayer == null) {
                throw new IllegalStateException("audio unavailable");
            }
            this.previewPlayer.setLooping(true);
            this.previewPlayer.setVolume(0.68f, 0.68f);
            this.previewPlayer.start();
            this.activePreviewButton = textView;
            textView.setText("■");
            textView.setBackground(Ui.circle(Ui.ACCENT_DARK, 1, Ui.ACCENT, this));
            this.previewHandler.postDelayed(this.previewTimeout, PREVIEW_MS);
        } catch (RuntimeException e) {
            stopPreview();
            Toast.makeText(this, R.string.preview_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Ui.prepareWindow(this);
        lastProState = Prefs.isPro(this);
        setContentView(createScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean pro = Prefs.isPro(this);
        if (lastProState != pro) {
            lastProState = pro;
            setContentView(createScreen());
        }
    }

    @Override // android.app.Activity
    protected void onStop() {
        stopPreview();
        super.onStop();
    }
}
