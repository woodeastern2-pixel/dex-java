package com.easternwood.sleeproutine;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends JamonActivity {
    private static final int REQUEST_MIX = 41;
    private TextView customDurationButton;
    private TextView durationSummary;
    private TextView mixSummary;
    private TextView planBadge;
    private int selectedDuration;
    private int selectedState;
    private TextView statsValue;
    private AdsConsentManager adsConsentManager;
    private FrameLayout bannerContainer;
    private AdView bannerAd;
    private boolean adsConsentStarted;
    private boolean adsConsentResolved;
    private boolean adsAllowed;
    private static final int[] DURATION_PRESETS = {10, 20, 30};
    private static final int[] STATES = {R.string.state_thoughts, R.string.state_stress, R.string.state_tired, R.string.state_nap, R.string.state_anxious, R.string.state_quick, R.string.state_noise_sensitive, R.string.state_clear_mind, R.string.state_deep_rest, R.string.state_awake_again};
    private static final int[] STATE_ICONS = {R.drawable.mood_thoughts, R.drawable.mood_stress, R.drawable.mood_tired, R.drawable.mood_nap, R.drawable.mood_anxious, R.drawable.mood_quick, R.drawable.mood_noise, R.drawable.mood_clear, R.drawable.mood_deep, R.drawable.mood_awake};
    private static final int[] ROUTINE_TITLES = {R.string.routine_name_0, R.string.routine_name_1, R.string.routine_name_2, R.string.routine_name_3, R.string.routine_name_5, R.string.routine_name_4, R.string.routine_name_6, R.string.routine_name_7, R.string.routine_name_8, R.string.routine_name_9};
    private static final int[] ROUTINE_GUIDES = {R.string.routine_guide_0, R.string.routine_guide_1, R.string.routine_guide_2, R.string.routine_guide_3, R.string.routine_guide_5, R.string.routine_guide_4, R.string.routine_guide_6, R.string.routine_guide_7, R.string.routine_guide_8, R.string.routine_guide_9};
    private final List<MoodCell> stateButtons = new ArrayList<>();
    private final List<View> durationDots = new ArrayList<>();
    private final TextView[] durationButtons = new TextView[DURATION_PRESETS.length];
    private final List<SoundCatalog.Sound> selectedSounds = new ArrayList<>();
    private final List<Float> selectedVolumes = new ArrayList<>();

    private void applyRoutinePreset(int i) {
        this.selectedSounds.clear();
        this.selectedVolumes.clear();
        String[][] strArr = {new String[]{"brown", "rain"}, new String[]{"rain", "waves"}, new String[]{"fan", "brown"}, new String[]{"forest", "waves"}, new String[]{"rain", "fan"}, new String[]{"brown"}, new String[]{"brown", "fan"}, new String[]{Prefs.isPro(this) ? "stream" : "forest", "waves"}, new String[]{Prefs.isPro(this) ? "ocean" : "waves", "brown"}, new String[]{"rain", "brown"}};
        float[][] fArr = {new float[]{0.58f, 0.46f}, new float[]{0.62f, 0.44f}, new float[]{0.58f, 0.42f}, new float[]{0.52f, 0.46f}, new float[]{0.55f, 0.42f}, new float[]{0.68f}, new float[]{0.6f, 0.45f}, new float[]{0.52f, 0.44f}, new float[]{0.56f, 0.38f}, new float[]{0.5f, 0.46f}};
        for (int i2 = 0; i2 < strArr[i].length; i2++) {
            this.selectedSounds.add(SoundCatalog.byId(strArr[i][i2]));
            this.selectedVolumes.add(Float.valueOf(fArr[i][i2]));
        }
    }

    private LinearLayout createDurationPresetRow(int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        for (int i2 = 0; i2 < 3; i2++) {
            int i3 = i + i2;
            final int i4 = DURATION_PRESETS[i3];
            TextView textViewChip = Ui.text(this, formatDuration(i4), 12.0f, Ui.MUTED, false);
            textViewChip.setGravity(Gravity.CENTER);
            textViewChip.setMinHeight(Ui.dp(this, 44.0f));
            styleDuration(textViewChip, this.selectedDuration == i4);
            textViewChip.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.MainActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$createDurationPresetRow$5(i4, view);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            linearLayout.addView(textViewChip, layoutParams);
            this.durationButtons[i3] = textViewChip;
        }
        return linearLayout;
    }

    private LinearLayout createDurationPresets() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(createDurationPresetRow(0));
        linearLayout.addView(createDurationTimeline(), Ui.matchWrap(this, 0));
        return linearLayout;
    }

    private View createDurationTimeline() {
        FrameLayout timeline = new FrameLayout(this);
        View line = new View(this);
        line.setBackgroundColor(Color.argb(82, 150, 165, 196));
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(-1, Ui.dp(this, 1), Gravity.CENTER_VERTICAL);
        lineParams.leftMargin = Ui.dp(this, 30);
        lineParams.rightMargin = Ui.dp(this, 30);
        timeline.addView(line, lineParams);
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        durationDots.clear();
        for (int index = 0; index < 7; index++) {
            FrameLayout cell = new FrameLayout(this);
            View dot = new View(this);
            int size = index == 3 ? 10 : 5;
            dot.setBackground(Ui.circle(index == 3 ? Ui.ACCENT : Color.rgb(88, 101, 132), 0, Color.TRANSPARENT, this));
            cell.addView(dot, new FrameLayout.LayoutParams(Ui.dp(this, size), Ui.dp(this, size), Gravity.CENTER));
            dots.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(this, 20), 1));
            durationDots.add(dot);
        }
        timeline.addView(dots, new FrameLayout.LayoutParams(-1, Ui.dp(this, 20)));
        timeline.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        return timeline;
    }

    private LinearLayout createHeader() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        linearLayout.addView(linearLayout2, layoutParams);
        linearLayout2.addView(Ui.text(this, getString(R.string.app_name), 21.0f, Ui.TEXT, true));
        linearLayout2.addView(Ui.text(this, getString(R.string.home_welcome), 12.0f, Ui.MUTED, false));
        this.planBadge = Ui.pill(this, "FREE", Ui.MOON, Color.argb(150, 49, 37, 70), Color.TRANSPARENT);
        this.planBadge.setContentDescription(getString(R.string.open_pro));
        this.planBadge.setClickable(true);
        this.planBadge.setFocusable(true);
        this.planBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ProActivity.class));
            }
        });
        linearLayout.addView(this.planBadge);
        ImageView textViewText = Ui.lineIcon(this, R.drawable.ic_nav_settings, Ui.TEXT, R.string.settings);
        textViewText.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.MainActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$createHeader$0(view);
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.dp(this, 48.0f), Ui.dp(this, 48.0f));
        layoutParams2.leftMargin = Ui.dp(this, 6.0f);
        linearLayout.addView(textViewText, layoutParams2);
        updatePlanBadge();
        return linearLayout;
    }

    private View createHome() {
        ScrollView scrollViewScreen = Ui.screen(this);
        LinearLayout linearLayoutColumn = Ui.column(this, 20);
        scrollViewScreen.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -2));
        linearLayoutColumn.addView(createHero());
        linearLayoutColumn.addView(createRoutineCard(), Ui.matchWrap(this, 20));
        linearLayoutColumn.addView(createBannerSlot(), Ui.matchWrap(this, 18));
        return Ui.withBottomNav(this, scrollViewScreen, 0);
    }

    private View createBannerSlot() {
        bannerContainer = new FrameLayout(this);
        bannerContainer.setVisibility(View.GONE);
        bannerContainer.setMinimumHeight(Ui.dp(this, 50));
        return bannerContainer;
    }

    private FrameLayout createHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setBackgroundColor(Ui.BG_TOP);
        ImageView art = Ui.artwork(this, R.drawable.home_hero, 270, 0);
        hero.addView(art, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 16));
        overlay.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(18, 3, 8, 23), Color.argb(20, 3, 8, 23), Color.argb(236, 3, 8, 23)}));
        overlay.addView(createHeader());
        View spacer = new View(this);
        overlay.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1.0f));
        overlay.addView(Ui.text(this, getString(R.string.home_question), 25.0f, Ui.TEXT, true));
        overlay.addView(Ui.text(this, getString(R.string.home_question_support), 13.0f, Ui.MUTED, false), Ui.matchWrap(this, 7));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        hero.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 270.0f)));
        return hero;
    }

    private LinearLayout createMixBox() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setPadding(Ui.dp(this, 2.0f), Ui.dp(this, 13.0f), Ui.dp(this, 2.0f), Ui.dp(this, 13.0f));
        ImageView artwork = Ui.artwork(this, SoundCatalog.byId("brown").imageRes, 48, 7);
        linearLayout.addView(artwork, new LinearLayout.LayoutParams(Ui.dp(this, 48.0f), Ui.dp(this, 48.0f)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(this, 12.0f), 0, Ui.dp(this, 8.0f), 0);
        labels.addView(Ui.text(this, getString(R.string.sound_mix), 13.0f, Ui.MUTED, false));
        this.mixSummary = Ui.text(this, "", 14.0f, Ui.TEXT, true);
        labels.addView(this.mixSummary, Ui.matchWrap(this, 5));
        linearLayout.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView textViewButton = Ui.text(this, "›", 30.0f, Ui.ACCENT, false);
        textViewButton.setGravity(Gravity.CENTER);
        textViewButton.setContentDescription(getString(R.string.customize_sound_mix));
        textViewButton.setMinWidth(Ui.dp(this, 48.0f));
        textViewButton.setMinHeight(Ui.dp(this, 48.0f));
        textViewButton.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$createMixBox$6(view);
            }
        });
        linearLayout.addView(textViewButton, new LinearLayout.LayoutParams(Ui.dp(this, 48.0f), Ui.dp(this, 48.0f)));
        updateMixSummary();
        return linearLayout;
    }

    private LinearLayout createPickerColumn(NumberPicker numberPicker, int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(numberPicker, new LinearLayout.LayoutParams(-2, -2));
        TextView textViewText = Ui.text(this, getString(i), 13.0f, Ui.MUTED, true);
        textViewText.setGravity(Gravity.CENTER);
        linearLayout.addView(textViewText, Ui.matchWrap(this, 2));
        return linearLayout;
    }

    private LinearLayout createRoutineCard() {
        LinearLayout linearLayoutCard = new LinearLayout(this);
        linearLayoutCard.setOrientation(LinearLayout.VERTICAL);
        linearLayoutCard.addView(Ui.text(this, getString(R.string.routine_title), 21.0f, Ui.TEXT, true));
        linearLayoutCard.addView(Ui.text(this, getString(R.string.routine_prompt), 13.0f, Ui.MUTED, false), Ui.matchWrap(this, 6));
        linearLayoutCard.addView(createStateGrid(), Ui.matchWrap(this, 12));
        linearLayoutCard.addView(Ui.text(this, getString(R.string.routine_length), 13.0f, Ui.MUTED, true), Ui.matchWrap(this, 18));
        linearLayoutCard.addView(createDurationPresets(), Ui.matchWrap(this, 8));
        linearLayoutCard.addView(createCustomDurationControl(), Ui.matchWrap(this, 2));
        updateDurationUi();
        linearLayoutCard.addView(Ui.divider(this), Ui.matchWrap(this, 10));
        linearLayoutCard.addView(createMixBox(), Ui.matchWrap(this, 0));
        TextView textViewButton = Ui.button(this, getString(R.string.start_routine_arrow), true);
        textViewButton.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$createRoutineCard$3(view);
            }
        });
        linearLayoutCard.addView(textViewButton, Ui.matchWrap(this, 18));
        return linearLayoutCard;
    }

    private LinearLayout createSoundLibraryCard() {
        LinearLayout linearLayoutCard = new LinearLayout(this);
        linearLayoutCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayoutCard.addView(linearLayout);
        TextView icon = Ui.text(this, "♫", 27.0f, Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.circle(Color.rgb(24, 30, 73), 0, Color.TRANSPARENT, this));
        linearLayout.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 58.0f), Ui.dp(this, 58.0f)));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = Ui.dp(this, 13.0f);
        linearLayout.addView(linearLayout2, layoutParams);
        linearLayout2.addView(Ui.text(this, getString(R.string.sound_library_title), 19.0f, Ui.TEXT, true));
        linearLayout2.addView(Ui.text(this, getString(R.string.sound_catalog_count), 13.0f, Ui.ACCENT, true), Ui.matchWrap(this, 4));
        linearLayout2.addView(Ui.text(this, getString(R.string.sound_preview_before_purchase), 12.0f, Ui.MUTED, false), Ui.matchWrap(this, 3));
        TextView textViewButton = Ui.text(this, getString(R.string.browse_sounds_arrow), 14.0f, Ui.ACCENT, true);
        textViewButton.setGravity(Gravity.CENTER_VERTICAL);
        textViewButton.setMinHeight(Ui.dp(this, 48.0f));
        textViewButton.setOnClickListener(new View.OnClickListener() { // from class: com.easternwood.sleeproutine.MainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$createSoundLibraryCard$7(view);
            }
        });
        linearLayoutCard.addView(textViewButton, Ui.matchWrap(this, 10));
        return linearLayoutCard;
    }

    private LinearLayout createStateGrid() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.stateButtons.clear();
        int i = 0;
        while (i < 4) {
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
            for (int i2 = 0; i2 < 3; i2++) {
                int i3 = i < 3 ? (i * 3) + i2 : (i2 == 1 ? 9 : -1);
                if (i3 < 0 || i3 >= STATES.length) {
                    linearLayout2.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1.0f));
                    continue;
                }
                MoodCell textViewChip = new MoodCell(i3);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
                linearLayout2.addView(textViewChip, layoutParams);
                this.stateButtons.add(textViewChip);
            }
            linearLayout.addView(linearLayout2, Ui.matchWrap(this, i == 0 ? 0 : 2));
            i++;
        }
        return linearLayout;
    }

    private LinearLayout createCustomDurationControl() {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setMinimumHeight(Ui.dp(this, 54));
        control.setPadding(Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4));
        control.setContentDescription(getString(R.string.duration_picker_title));
        control.setClickable(true);
        control.setFocusable(true);

        this.customDurationButton = Ui.text(this, "", 13.0f, Ui.MUTED, true);
        this.customDurationButton.setGravity(Gravity.CENTER_VERTICAL);
        control.addView(this.customDurationButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1.0f));

        TextView stepper = Ui.text(this, "▲\n▼", 10.0f, Ui.ACCENT, true);
        stepper.setGravity(Gravity.CENTER);
        stepper.setLineSpacing(-Ui.dp(this, 3), 1.0f);
        stepper.setContentDescription(getString(R.string.duration_stepper_description));
        control.addView(stepper, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 46)));

        control.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDurationPicker();
            }
        });
        return control;
    }

    private final class MoodCell extends LinearLayout {
        private final int stateIndex;
        private final ImageView icon;
        private final TextView label;
        private final View underline;

        MoodCell(final int index) {
            super(MainActivity.this);
            this.stateIndex = index;
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setMinimumHeight(Ui.dp(MainActivity.this, 68));
            setPadding(Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 2), Ui.dp(MainActivity.this, 2), 0);
            setClickable(true);
            setFocusable(true);
            setContentDescription(getString(STATES[index]));

            this.icon = new ImageView(MainActivity.this);
            this.icon.setImageResource(STATE_ICONS[index]);
            this.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            this.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(this.icon, new LinearLayout.LayoutParams(Ui.dp(MainActivity.this, 42), Ui.dp(MainActivity.this, 42)));

            this.label = Ui.text(MainActivity.this, getString(STATES[index]), 11.5f, Ui.MUTED, false);
            this.label.setGravity(Gravity.CENTER);
            this.label.setMaxLines(1);
            this.label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(this.label, Ui.matchWrap(MainActivity.this, 1));

            this.underline = new View(MainActivity.this);
            this.underline.setBackgroundColor(Ui.ACCENT);
            LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(Ui.dp(MainActivity.this, 42), Ui.dp(MainActivity.this, 2));
            underlineParams.topMargin = Ui.dp(MainActivity.this, 5);
            addView(this.underline, underlineParams);

            setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectState(index);
                }
            });
            setSelectedState(index == selectedState);
        }

        void setSelectedState(boolean selected) {
            this.label.setTextColor(selected ? Ui.TEXT : Ui.MUTED);
            this.label.setTypeface(android.graphics.Typeface.create("sans", selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            this.icon.setAlpha(selected ? 1.0f : 0.88f);
            this.icon.setScaleX(selected ? 1.06f : 1.0f);
            this.icon.setScaleY(selected ? 1.06f : 1.0f);
            this.underline.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private LinearLayout createStatsCard() {
        LinearLayout linearLayoutCard = new LinearLayout(this);
        linearLayoutCard.setOrientation(LinearLayout.VERTICAL);
        linearLayoutCard.addView(Ui.label(this, getString(R.string.weekly)));
        linearLayoutCard.addView(Ui.text(this, getString(R.string.weekly_nights), 19.0f, Ui.TEXT, true), Ui.matchWrap(this, 8));
        this.statsValue = Ui.text(this, "", 14.0f, Ui.MUTED, false);
        linearLayoutCard.addView(this.statsValue, Ui.matchWrap(this, 7));
        updateStats();
        return linearLayoutCard;
    }

    private String formatDuration(int i) {
        if (i < 60) {
            return getString(R.string.duration_minutes_short, new Object[]{Integer.valueOf(i)});
        }
        int i2 = i / 60;
        int i3 = i % 60;
        return i3 == 0 ? getString(R.string.duration_hours_short, new Object[]{Integer.valueOf(i2)}) : getString(R.string.duration_hours_minutes, new Object[]{Integer.valueOf(i2), Integer.valueOf(i3)});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createDurationPresetRow$5(int i, View view) {
        this.selectedDuration = i;
        Prefs.setRoutineMinutes(this, i);
        updateDurationUi();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createHeader$0(View view) {
        startActivity(new Intent(this, (Class<?>) SettingsActivity.class));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createMixBox$6(View view) {
        openMixer();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createRoutineCard$3(View view) {
        openSession();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$createSoundLibraryCard$7(View view) {
        startActivity(new Intent(this, (Class<?>) SoundLibraryActivity.class));
    }

    private void openMixer() {
        Intent intent = new Intent(this, (Class<?>) SoundMixerActivity.class);
        int[] iArr = new int[this.selectedSounds.size()];
        float[] fArr = new float[this.selectedSounds.size()];
        for (int i = 0; i < this.selectedSounds.size(); i++) {
            iArr[i] = this.selectedSounds.get(i).rawRes;
            fArr[i] = this.selectedVolumes.get(i).floatValue();
        }
        intent.putExtra(SoundMixerActivity.EXTRA_SOUND_RESOURCES, iArr);
        intent.putExtra(SoundMixerActivity.EXTRA_VOLUMES, fArr);
        startActivityForResult(intent, REQUEST_MIX);
    }

    private void openSession() {
        Intent intent = new Intent(this, (Class<?>) SleepSessionActivity.class);
        int[] iArr = new int[this.selectedSounds.size()];
        String[] strArr = new String[this.selectedSounds.size()];
        float[] fArr = new float[this.selectedSounds.size()];
        for (int i = 0; i < this.selectedSounds.size(); i++) {
            iArr[i] = this.selectedSounds.get(i).rawRes;
            strArr[i] = getString(this.selectedSounds.get(i).nameRes);
            fArr[i] = this.selectedVolumes.get(i).floatValue();
        }
        intent.putExtra(SleepSessionActivity.EXTRA_SOUND_RESOURCES, iArr);
        intent.putExtra(SleepSessionActivity.EXTRA_SOUND_NAMES, strArr);
        intent.putExtra(SleepSessionActivity.EXTRA_VOLUMES, fArr);
        intent.putExtra(SleepSessionActivity.EXTRA_DURATION_MINUTES, this.selectedDuration);
        intent.putExtra(SleepSessionActivity.EXTRA_ROUTINE_TITLE, getString(ROUTINE_TITLES[this.selectedState]));
        intent.putExtra(SleepSessionActivity.EXTRA_ROUTINE_GUIDANCE, getString(ROUTINE_GUIDES[this.selectedState]));
        startActivity(intent);
    }

    private void removeLockedSounds() {
        boolean z = true;
        boolean z2 = false;
        for (int size = this.selectedSounds.size() - 1; size >= 0; size--) {
            if (this.selectedSounds.get(size).pro) {
                this.selectedSounds.remove(size);
                this.selectedVolumes.remove(size);
                z2 = true;
            }
        }
        if (this.selectedSounds.isEmpty()) {
            this.selectedSounds.add(SoundCatalog.byId("brown"));
            this.selectedVolumes.add(Float.valueOf(0.6f));
        } else {
            z = z2;
        }
        if (z) {
            updateMixSummary();
        }
    }

    private void selectState(int i) {
        this.selectedState = i;
        int i2 = 0;
        while (i2 < this.stateButtons.size()) {
            MoodCell state = this.stateButtons.get(i2);
            state.setSelectedState(state.stateIndex == this.selectedState);
            i2++;
        }
        applyRoutinePreset(i);
        updateMixSummary();
    }

    private void showDurationPicker() {
        final NumberPicker hoursPicker = new NumberPicker(this);
        hoursPicker.setMinValue(0);
        hoursPicker.setMaxValue(12);
        hoursPicker.setValue(this.selectedDuration / 60);
        hoursPicker.setWrapSelectorWheel(false);

        final NumberPicker minutesPicker = new NumberPicker(this);
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(59);
        minutesPicker.setValue(this.selectedDuration % 60);
        minutesPicker.setWrapSelectorWheel(true);

        final EditText totalMinutes = new EditText(this);
        totalMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        totalMinutes.setText(String.valueOf(this.selectedDuration));
        totalMinutes.setTextColor(Ui.TEXT);
        totalMinutes.setHintTextColor(Ui.MUTED);
        totalMinutes.setGravity(Gravity.CENTER);
        totalMinutes.setTextSize(20.0f);
        totalMinutes.setSelectAllOnFocus(true);
        totalMinutes.setSingleLine(true);
        totalMinutes.setContentDescription(getString(R.string.duration_number_label));
        totalMinutes.setBackgroundTintList(ColorStateList.valueOf(Ui.ACCENT));

        final boolean[] syncing = {false};
        NumberPicker.OnValueChangeListener wheelListener = new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldValue, int newValue) {
                if (hoursPicker.getValue() == 12 && minutesPicker.getValue() != 0) {
                    minutesPicker.setValue(0);
                }
                if (!syncing[0]) {
                    syncing[0] = true;
                    totalMinutes.setText(String.valueOf((hoursPicker.getValue() * 60) + minutesPicker.getValue()));
                    totalMinutes.setSelection(totalMinutes.length());
                    syncing[0] = false;
                }
            }
        };
        hoursPicker.setOnValueChangedListener(wheelListener);
        minutesPicker.setOnValueChangedListener(wheelListener);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        TextView directLabel = Ui.text(this, getString(R.string.duration_number_label), 12.0f, Ui.MUTED, true);
        directLabel.setGravity(Gravity.CENTER);
        content.addView(directLabel);
        content.addView(totalMinutes, Ui.matchWrap(this, 4));

        TextView wheelHint = Ui.text(this, getString(R.string.duration_wheel_hint), 12.0f, Ui.MUTED, false);
        wheelHint.setGravity(Gravity.CENTER);
        content.addView(wheelHint, Ui.matchWrap(this, 16));

        LinearLayout wheels = new LinearLayout(this);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setGravity(Gravity.CENTER);
        wheels.addView(createPickerColumn(hoursPicker, R.string.hours), new LinearLayout.LayoutParams(0, -2, 1.0f));
        wheels.addView(createPickerColumn(minutesPicker, R.string.minutes), new LinearLayout.LayoutParams(0, -2, 1.0f));
        content.addView(wheels);

        new AlertDialog.Builder(this)
                .setTitle(R.string.duration_picker_title)
                .setMessage(R.string.duration_picker_message)
                .setView(content)
                .setNegativeButton(R.string.cancel, (DialogInterface.OnClickListener) null)
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        int minutes = (hoursPicker.getValue() * 60) + minutesPicker.getValue();
                        String typed = totalMinutes.getText().toString().trim();
                        if (!typed.isEmpty()) {
                            try {
                                minutes = Integer.parseInt(typed);
                            } catch (NumberFormatException ignored) {
                                minutes = MainActivity.this.selectedDuration;
                            }
                        }
                        MainActivity.this.selectedDuration = Math.max(1, Math.min(720, minutes));
                        Prefs.setRoutineMinutes(MainActivity.this, MainActivity.this.selectedDuration);
                        updateDurationUi();
                    }
                })
                .show();
    }

    private void showRecovery(Throwable th) {
        Ui.prepareWindow(this);
        LinearLayout linearLayoutColumn = Ui.column(this, 24);
        linearLayoutColumn.setBackgroundColor(Ui.BG);
        linearLayoutColumn.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(Ui.text(this, getString(R.string.app_name), 30.0f, Ui.TEXT, true));
        TextView textViewText = Ui.text(this, getString(R.string.startup_error, new Object[]{th.getClass().getSimpleName() + ": " + (th.getMessage() == null ? "unknown" : th.getMessage())}), 14.0f, Ui.MUTED, false);
        textViewText.setGravity(Gravity.CENTER);
        linearLayoutColumn.addView(textViewText, Ui.matchWrap(this, 18));
        setContentView(linearLayoutColumn);
    }

    private void updateDurationUi() {
        boolean z = false;
        int i = 0;
        while (true) {
            if (i >= this.durationButtons.length) {
                break;
            }
            if (this.durationButtons[i] != null) {
                styleDuration(this.durationButtons[i], this.selectedDuration == DURATION_PRESETS[i]);
            }
            i++;
        }
        if (this.customDurationButton != null) {
            this.customDurationButton.setText(getString(R.string.custom_duration_current, new Object[]{formatDuration(this.selectedDuration)}));
            int[] iArr = DURATION_PRESETS;
            int length = iArr.length;
            int i2 = 0;
            while (true) {
                if (i2 >= length) {
                    z = true;
                    break;
                } else {
                    if (this.selectedDuration == iArr[i2]) {
                        break;
                    } else {
                        i2++;
                    }
                }
            }
            this.customDurationButton.setTextColor(z ? Ui.ACCENT : Ui.TEXT);
            this.customDurationButton.setBackgroundColor(Color.TRANSPARENT);
        }
        if (this.durationSummary != null) {
            this.durationSummary.setText(getString(R.string.duration_summary, new Object[]{formatDuration(this.selectedDuration)}));
        }
        int activeDot = this.selectedDuration <= 10 ? 0 : this.selectedDuration >= 30 ? 6 : 3;
        for (int dotIndex = 0; dotIndex < durationDots.size(); dotIndex++) {
            View dot = durationDots.get(dotIndex);
            int size = dotIndex == activeDot ? 10 : 5;
            dot.setBackground(Ui.circle(dotIndex == activeDot ? Ui.ACCENT : Color.rgb(88, 101, 132), 0, Color.TRANSPARENT, this));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dot.getLayoutParams();
            params.width = Ui.dp(this, size);
            params.height = Ui.dp(this, size);
            dot.setLayoutParams(params);
        }
    }

    private void styleDuration(TextView view, boolean selected) {
        view.setTextColor(selected ? Ui.TEXT : Ui.MUTED);
        view.setTextSize(selected ? 20f : 12f);
        view.setTypeface(android.graphics.Typeface.create("sans", selected
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    private void updateMixSummary() {
        if (this.mixSummary == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.selectedSounds.size(); i++) {
            if (i > 0) {
                sb.append("  +  ");
            }
            sb.append(getString(this.selectedSounds.get(i).nameRes));
        }
        this.mixSummary.setText(sb.length() == 0 ? getString(R.string.sound_brown) : sb.toString());
    }

    private void updatePlanBadge() {
        if (this.planBadge == null) {
            return;
        }
        boolean zIsPro = Prefs.isPro(this);
        this.planBadge.setText(getString(zIsPro ? R.string.pro_active : R.string.free_plan));
        this.planBadge.setTextColor(zIsPro ? Ui.MOON : Ui.CYAN);
        this.planBadge.setBackground(Ui.filledBackground(zIsPro ? Color.rgb(54, 40, 74) : Color.rgb(17, 48, 67), 6, this));
    }

    private void startAdsIfNeeded() {
        if (adsConsentManager == null) {
            return;
        }
        if (Prefs.isPro(this) || adsConsentStarted) {
            updateBannerVisibility();
            return;
        }
        adsConsentStarted = true;
        adsConsentManager.gatherConsent(this, new AdsConsentManager.Callback() {
            @Override
            public void onResult(boolean allowed) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                adsConsentResolved = true;
                adsAllowed = allowed;
                updateBannerVisibility();
            }
        });
    }

    private void updateBannerVisibility() {
        if (bannerContainer == null) {
            return;
        }
        if (Prefs.isPro(this) || !adsConsentResolved || !adsAllowed) {
            bannerContainer.setVisibility(View.GONE);
            if (Prefs.isPro(this)) {
                destroyBanner();
            }
            return;
        }
        if (bannerAd == null) {
            bannerAd = new AdView(this);
            bannerAd.setAdSize(AdSize.BANNER);
            bannerAd.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            bannerAd.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    if (bannerContainer != null && !Prefs.isPro(MainActivity.this)) {
                        bannerContainer.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    if (bannerContainer != null) {
                        bannerContainer.setVisibility(View.GONE);
                    }
                }
            });
            bannerContainer.removeAllViews();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            bannerContainer.addView(bannerAd, params);
            bannerAd.loadAd(new AdRequest.Builder().build());
        }
    }

    private void destroyBanner() {
        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }
        if (bannerContainer != null) {
            bannerContainer.removeAllViews();
            bannerContainer.setVisibility(View.GONE);
        }
    }

    private void updateStats() {
        if (this.statsValue != null) {
            this.statsValue.setText(getString(R.string.routine_stats, new Object[]{Integer.valueOf(Prefs.getRoutineCount(this)), Integer.valueOf(Prefs.getTotalMinutes(this))}));
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQUEST_MIX && i2 == -1 && intent != null) {
            int[] intArrayExtra = intent.getIntArrayExtra(SoundMixerActivity.EXTRA_SOUND_RESOURCES);
            float[] floatArrayExtra = intent.getFloatArrayExtra(SoundMixerActivity.EXTRA_VOLUMES);
            if (intArrayExtra == null || intArrayExtra.length == 0) {
                return;
            }
            this.selectedSounds.clear();
            this.selectedVolumes.clear();
            int i3 = 0;
            while (i3 < intArrayExtra.length) {
                this.selectedSounds.add(SoundCatalog.byRaw(intArrayExtra[i3]));
                this.selectedVolumes.add(Float.valueOf((floatArrayExtra == null || i3 >= floatArrayExtra.length) ? 0.55f : floatArrayExtra[i3]));
                i3++;
            }
            updateMixSummary();
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            Ui.prepareWindow(this);
            this.selectedDuration = Prefs.getRoutineMinutes(this);
            applyRoutinePreset(0);
            setContentView(createHome());
            adsConsentManager = new AdsConsentManager(this);
            startAdsIfNeeded();
        } catch (Throwable th) {
            showRecovery(th);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) {
            bannerAd.resume();
        }
        updatePlanBadge();
        updateStats();
        startAdsIfNeeded();
        updateBannerVisibility();
        if (Prefs.isPro(this)) {
            return;
        }
        removeLockedSounds();
    }

    @Override
    protected void onPause() {
        if (bannerAd != null) {
            bannerAd.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyBanner();
        super.onDestroy();
    }
}
