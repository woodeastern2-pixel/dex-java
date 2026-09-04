package com.easternwood.sleeproutine;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Borderless, editorial sound mixer used by the redesigned routine flow. */
public class SoundMixerActivity extends JamonActivity {
    public static final String EXTRA_SOUND_RESOURCES = "mix_sound_resources";
    public static final String EXTRA_VOLUMES = "mix_volumes";
    private static final long PREVIEW_MS = 30000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<Integer, Float> selected = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, RowViews> rows = new LinkedHashMap<>();
    private TextView activePreviewButton;
    private TextView countView;
    private MediaPlayer previewPlayer;
    private boolean lastProState;
    private final Runnable stopPreviewTask = this::stopPreview;

    private static final class RowViews {
        final TextView toggle;
        final SeekBar volume;

        RowViews(TextView toggle, SeekBar volume) {
            this.toggle = toggle;
            this.volume = volume;
        }
    }

    private View createScreen() {
        rows.clear();
        ScrollView scroll = Ui.screen(this);
        LinearLayout column = Ui.column(this, 20);
        scroll.addView(column, new FrameLayout.LayoutParams(-1, -2));

        column.addView(Ui.sectionHeader(this, R.string.mixer_title));
        column.addView(Ui.text(this, getString(R.string.mixer_heading), 27, Ui.TEXT, true), Ui.matchWrap(this, 18));
        column.addView(Ui.text(this, getString(R.string.mixer_support), 14, Ui.MUTED, false), Ui.matchWrap(this, 8));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, Ui.dp(this, 19), 0, Ui.dp(this, 17));
        LinearLayout statusText = new LinearLayout(this);
        statusText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        status.addView(statusText, statusTextParams);
        countView = Ui.text(this, "", 14, Ui.CYAN, true);
        statusText.addView(countView);
        statusText.addView(Ui.text(this,
                getString(Prefs.isPro(this) ? R.string.mixer_pro_limit : R.string.mixer_free_limit),
                12, Ui.MUTED, false), Ui.matchWrap(this, 5));
        column.addView(status, Ui.matchWrap(this, 13));
        column.addView(Ui.divider(this));
        updateCount();

        for (SoundCatalog.Sound sound : SoundCatalog.ALL) {
            if (selected.containsKey(sound.rawRes)) {
                column.addView(createSoundRow(sound));
            }
        }

        TextView addSound = Ui.text(this, getString(R.string.add_sound_full), 15, Ui.TEXT, false);
        addSound.setGravity(Gravity.CENTER_VERTICAL);
        addSound.setMinHeight(Ui.dp(this, 58));
        addSound.setOnClickListener(v -> showSoundPicker());
        column.addView(addSound, Ui.matchWrap(this, 3));
        column.addView(Ui.divider(this));

        TextView autoStop = Ui.text(this, "◷   " + getString(R.string.auto_stop_20) + "     ›", 14, Ui.MUTED, false);
        autoStop.setGravity(Gravity.CENTER_VERTICAL);
        autoStop.setMinHeight(Ui.dp(this, 58));
        column.addView(autoStop);
        column.addView(Ui.divider(this));

        TextView save = Ui.button(this, getString(R.string.save_mix), true);
        save.setOnClickListener(v -> saveMix());
        column.addView(save, Ui.matchWrap(this, 24));
        return Ui.withBottomNav(this, scroll, 1);
    }

    private LinearLayout createSoundRow(final SoundCatalog.Sound sound) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Ui.dp(this, 17), 0, Ui.dp(this, 13));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(summary);

        ImageView art = Ui.artwork(this, sound.imageRes, 78, 39);
        summary.addView(art, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = Ui.dp(this, 14);
        summary.addView(labels, labelParams);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(titleLine);
        titleLine.addView(Ui.text(this, getString(sound.nameRes), 17, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (sound.pro) {
            titleLine.addView(Ui.pill(this, "PRO", Ui.MOON, Color.rgb(50, 38, 61), Color.TRANSPARENT));
        }
        labels.addView(Ui.text(this, getString(sound.descriptionRes), 12, Ui.MUTED, false), Ui.matchWrap(this, 5));
        labels.addView(Ui.text(this,
                getString(sound.actualRecording ? R.string.real_ambience : R.string.spectral_noise),
                11, sound.actualRecording ? Ui.CYAN : Ui.MUTED, true), Ui.matchWrap(this, 5));

        final TextView preview = Ui.text(this, "▶", 17, Ui.TEXT, true);
        preview.setGravity(Gravity.CENTER);
        preview.setContentDescription(getString(R.string.preview));
        preview.setBackground(Ui.circle(Ui.ACCENT_DARK, 0, Color.TRANSPARENT, this));
        preview.setOnClickListener(v -> togglePreview(sound, preview));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        previewParams.leftMargin = Ui.dp(this, 9);
        summary.addView(preview, previewParams);

        TextView toggle = Ui.text(this, "×", 28, Ui.MUTED, false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setMinHeight(Ui.dp(this, 44));
        toggle.setPadding(Ui.dp(this, 12), 0, 0, 0);
        toggle.setOnClickListener(v -> toggleSound(sound));
        summary.addView(toggle, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 48)));

        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(Math.round(selected.getOrDefault(sound.rawRes, .55f) * 100));
        volume.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        volume.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.HAIRLINE));
        volume.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (selected.containsKey(sound.rawRes)) {
                    selected.put(sound.rawRes, Math.max(.05f, progress / 100f));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(volume, Ui.matchWrap(this, 10));
        row.addView(Ui.divider(this), Ui.matchWrap(this, 10));

        rows.put(sound.rawRes, new RowViews(toggle, volume));
        updateRow(sound);
        return row;
    }

    private void loadInitialMix() {
        int[] resources = getIntent().getIntArrayExtra(EXTRA_SOUND_RESOURCES);
        float[] volumes = getIntent().getFloatArrayExtra(EXTRA_VOLUMES);
        if (resources != null) {
            for (int i = 0; i < resources.length; i++) {
                selected.put(resources[i], volumes == null || i >= volumes.length ? .55f : volumes[i]);
            }
        }
        if (selected.isEmpty()) selected.put(R.raw.brown_noise, .6f);
    }

    private void saveMix() {
        int[] resources = new int[selected.size()];
        float[] volumes = new float[selected.size()];
        int index = 0;
        for (Map.Entry<Integer, Float> entry : selected.entrySet()) {
            resources[index] = entry.getKey();
            volumes[index] = entry.getValue();
            index++;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_SOUND_RESOURCES, resources);
        result.putExtra(EXTRA_VOLUMES, volumes);
        setResult(RESULT_OK, result);
        finish();
    }

    private void showSoundPicker() {
        List<SoundCatalog.Sound> available = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (SoundCatalog.Sound sound : SoundCatalog.ALL) {
            if (!selected.containsKey(sound.rawRes)) {
                available.add(sound);
                labels.add(getString(sound.nameRes) + (sound.pro ? "  ·  PRO" : ""));
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_sound_full)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    SoundCatalog.Sound sound = available.get(which);
                    toggleSound(sound);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void stopPreview() {
        handler.removeCallbacks(stopPreviewTask);
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (RuntimeException ignored) { }
            previewPlayer.release();
            previewPlayer = null;
        }
        if (activePreviewButton != null) {
            activePreviewButton.setText("▶");
            activePreviewButton.setBackground(Ui.circle(Ui.ACCENT_DARK, 0, Color.TRANSPARENT, this));
            activePreviewButton = null;
        }
    }

    private void togglePreview(SoundCatalog.Sound sound, TextView button) {
        if (activePreviewButton == button && previewPlayer != null) {
            stopPreview();
            return;
        }
        stopPreview();
        previewPlayer = MediaPlayer.create(this, sound.rawRes);
        if (previewPlayer == null) {
            Toast.makeText(this, R.string.preview_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        previewPlayer.setLooping(true);
        previewPlayer.setVolume(.65f, .65f);
        previewPlayer.start();
        activePreviewButton = button;
        button.setText("■");
        button.setBackground(Ui.circle(Ui.ACCENT, 0, Color.TRANSPARENT, this));
        handler.postDelayed(stopPreviewTask, PREVIEW_MS);
    }

    private void toggleSound(SoundCatalog.Sound sound) {
        if (selected.containsKey(sound.rawRes)) {
            if (selected.size() == 1) {
                Toast.makeText(this, R.string.sound_required, Toast.LENGTH_SHORT).show();
                return;
            }
            selected.remove(sound.rawRes);
        } else {
            if (sound.pro && !Prefs.isPro(this)) {
                startActivity(new Intent(this, ProActivity.class));
                return;
            }
            int limit = Prefs.isPro(this) ? 4 : 2;
            if (selected.size() >= limit) {
                if (!Prefs.isPro(this)) {
                    startActivity(new Intent(this, ProActivity.class));
                    return;
                }
                Toast.makeText(this, getString(R.string.mix_limit_reached, limit), Toast.LENGTH_SHORT).show();
                return;
            }
            RowViews row = rows.get(sound.rawRes);
            selected.put(sound.rawRes, row == null ? .55f : Math.max(.05f, row.volume.getProgress() / 100f));
        }
        updateCount();
        setContentView(createScreen());
    }

    private void updateCount() {
        if (countView != null) {
            countView.setText(getString(R.string.mixer_count, selected.size(), Prefs.isPro(this) ? 4 : 2));
        }
    }

    private void updateRow(SoundCatalog.Sound sound) {
        RowViews row = rows.get(sound.rawRes);
        if (row == null) return;
        boolean isSelected = selected.containsKey(sound.rawRes);
        if (!sound.pro || Prefs.isPro(this)) {
            row.toggle.setText(isSelected ? "×" : getString(R.string.add_sound));
            row.toggle.setTextColor(isSelected ? Ui.MUTED : Ui.ACCENT);
        } else {
            row.toggle.setText(R.string.add_with_pro);
            row.toggle.setTextColor(Ui.MOON);
        }
        row.volume.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        if (isSelected) row.volume.setProgress(Math.round(selected.get(sound.rawRes) * 100));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareWindow(this);
        loadInitialMix();
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

    @Override
    protected void onStop() {
        stopPreview();
        super.onStop();
    }
}
