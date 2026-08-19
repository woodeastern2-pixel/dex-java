package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;
import com.signpdf.app.util.SaveLocationPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SignPDF-owned PDF merge picker.
 *
 * The folder is configured during first-run onboarding or from Settings.
 * This screen never asks the user to choose a folder. It opens directly as a
 * PDF selection screen where one tap toggles each file. The top-right confirm
 * button returns the selected PDFs to the Pro merge tool.
 */
public class MergePdfPickerActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_URIS = "selected_pdf_uris";

    private static final int MAX_PDFS = 1000;
    private static final int MAX_DEPTH = 8;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<PdfEntry> entries = new ArrayList<>();
    private final LinkedHashMap<String, PdfEntry> selected = new LinkedHashMap<>();

    private LinearLayout listContainer;
    private TextView selectedCount;
    private TextView statusView;
    private ProgressBar progress;
    private MaterialButton confirmButton;
    private MaterialButton settingsButton;

    private Uri rootUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootUri = SaveLocationPreferences.getTreeUri(this);
        buildUi();

        if (rootUri == null) {
            showFolderNotConfigured();
        } else {
            loadPdfEntries();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F6F8FC"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        topBar.setBackgroundColor(Color.WHITE);
        root.addView(topBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64)));

        MaterialButton backButton = compactButton(R.string.settings_back);
        backButton.setOnClickListener(v -> finish());
        topBar.addView(backButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(44)));

        TextView title = new TextView(this);
        title.setText(R.string.merge_picker_title);
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f);
        titleParams.leftMargin = dp(10);
        topBar.addView(title, titleParams);

        confirmButton = compactButton(R.string.merge_picker_confirm);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.45f);
        confirmButton.setOnClickListener(v -> finishSelection());
        topBar.addView(confirmButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(44)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.addView(content, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f));

        TextView hint = new TextView(this);
        hint.setText(R.string.merge_picker_hint);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#637083"));
        content.addView(hint, matchWrap());

        TextView folderLabel = new TextView(this);
        String label = SaveLocationPreferences.getLabel(this);
        if (label == null || label.trim().isEmpty()) {
            label = getString(R.string.settings_save_location_default);
        }
        folderLabel.setText(getString(R.string.merge_picker_folder, label));
        folderLabel.setTextSize(12);
        folderLabel.setTextColor(Color.parseColor("#7A8798"));
        LinearLayout.LayoutParams folderParams = matchWrap();
        folderParams.topMargin = dp(6);
        content.addView(folderLabel, folderParams);

        selectedCount = new TextView(this);
        selectedCount.setText(getString(R.string.merge_picker_selected_count, 0));
        selectedCount.setTextSize(14);
        selectedCount.setTextColor(Color.parseColor("#2159C9"));
        selectedCount.setTypeface(selectedCount.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams selectedParams = matchWrap();
        selectedParams.topMargin = dp(12);
        content.addView(selectedCount, selectedParams);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(22);
        content.addView(progress, progressParams);

        statusView = new TextView(this);
        statusView.setText(R.string.merge_picker_loading);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.parseColor("#637083"));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(14);
        content.addView(statusView, statusParams);

        settingsButton = compactButton(R.string.merge_picker_open_settings);
        settingsButton.setVisibility(View.GONE);
        settingsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48));
        settingsParams.topMargin = dp(12);
        content.addView(settingsButton, settingsParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f);
        scrollParams.topMargin = dp(10);
        content.addView(scroll, scrollParams);

        setContentView(root);
    }

    private void loadPdfEntries() {
        setLoading(true);
        executor.execute(() -> {
            ArrayList<PdfEntry> found = new ArrayList<>();
            boolean accessible = false;
            try {
                DocumentFile folder = DocumentFile.fromTreeUri(this, rootUri);
                if (folder != null && folder.exists() && folder.isDirectory()) {
                    accessible = true;
                    collectPdfs(folder, found, 0);
                    found.sort(Comparator.comparing(
                        entry -> entry.name == null ? "" : entry.name,
                        String.CASE_INSENSITIVE_ORDER));
                }
            } catch (RuntimeException ignored) {
                accessible = false;
            }

            boolean finalAccessible = accessible;
            runOnUiThread(() -> {
                if (!finalAccessible) {
                    showFolderAccessFailed();
                    return;
                }
                entries.clear();
                entries.addAll(found);
                setLoading(false);
                renderPdfRows();
            });
        });
    }

    private void collectPdfs(DocumentFile folder, List<PdfEntry> output, int depth) {
        if (depth > MAX_DEPTH || output.size() >= MAX_PDFS) return;

        DocumentFile[] children;
        try {
            children = folder.listFiles();
        } catch (RuntimeException error) {
            return;
        }

        for (DocumentFile child : children) {
            if (output.size() >= MAX_PDFS) return;
            if (child == null || !child.exists()) continue;

            if (child.isDirectory()) {
                collectPdfs(child, output, depth + 1);
                continue;
            }

            String name = child.getName();
            String type = child.getType();
            boolean pdf = "application/pdf".equals(type)
                || (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
            if (pdf) {
                output.add(new PdfEntry(
                    child.getUri(),
                    name == null || name.trim().isEmpty() ? "PDF" : name));
            }
        }
    }

    private void renderPdfRows() {
        listContainer.removeAllViews();

        if (entries.isEmpty()) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(R.string.merge_picker_empty);
            return;
        }

        statusView.setVisibility(View.GONE);

        for (PdfEntry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(12), dp(8));
            row.setMinimumHeight(dp(60));
            row.setClickable(true);
            row.setFocusable(true);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setClickable(false);
            checkBox.setFocusable(false);
            checkBox.setChecked(selected.containsKey(entry.uri.toString()));
            row.addView(checkBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView fileName = new TextView(this);
            fileName.setText(entry.name);
            fileName.setTextSize(14);
            fileName.setTextColor(Color.parseColor("#15233A"));
            fileName.setMaxLines(2);
            row.addView(fileName, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

            updateRowBackground(row, checkBox.isChecked());
            row.setOnClickListener(v -> {
                String key = entry.uri.toString();
                if (selected.containsKey(key)) {
                    selected.remove(key);
                } else {
                    selected.put(key, entry);
                }
                boolean checked = selected.containsKey(key);
                checkBox.setChecked(checked);
                updateRowBackground(row, checked);
                updateSelectionState();
            });

            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(6);
            listContainer.addView(row, rowParams);
        }
    }

    private void updateRowBackground(LinearLayout row, boolean checked) {
        row.setBackgroundColor(Color.parseColor(checked ? "#E4EEFF" : "#FFFFFF"));
    }

    private void updateSelectionState() {
        selectedCount.setText(getString(R.string.merge_picker_selected_count, selected.size()));
        boolean ready = selected.size() >= 2;
        confirmButton.setEnabled(ready);
        confirmButton.setAlpha(ready ? 1f : 0.45f);
    }

    private void finishSelection() {
        if (selected.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (PdfEntry entry : selected.values()) {
            uris.add(entry.uri);
        }

        Intent result = new Intent();
        result.putParcelableArrayListExtra(EXTRA_SELECTED_URIS, uris);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void showFolderNotConfigured() {
        progress.setVisibility(View.GONE);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.merge_picker_folder_missing);
        settingsButton.setVisibility(View.VISIBLE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.45f);
    }

    private void showFolderAccessFailed() {
        progress.setVisibility(View.GONE);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.save_location_access_failed);
        settingsButton.setVisibility(View.VISIBLE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.45f);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusView.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) statusView.setText(R.string.merge_picker_loading);
        settingsButton.setVisibility(View.GONE);
        confirmButton.setEnabled(!loading && selected.size() >= 2);
        confirmButton.setAlpha(!loading && selected.size() >= 2 ? 1f : 0.45f);
    }

    private MaterialButton compactButton(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setCornerRadius(dp(12));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class PdfEntry {
        final Uri uri;
        final String name;

        PdfEntry(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }
}
