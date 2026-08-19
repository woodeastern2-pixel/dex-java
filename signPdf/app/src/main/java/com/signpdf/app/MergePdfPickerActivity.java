package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SignPDF-owned picker used by PDF merge.
 * A folder grant is requested once, then every PDF in that folder tree can be
 * toggled with a single tap. This avoids OEM-specific multi-select gestures.
 */
public class MergePdfPickerActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_URIS = "selected_pdf_uris";

    private static final String PREFS = "signpdf_merge_picker";
    private static final String KEY_ROOT_URI = "root_uri";
    private static final String KEY_ROOT_LABEL = "root_label";
    private static final int MAX_PDFS = 1000;
    private static final int MAX_DEPTH = 8;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<PdfEntry> entries = new ArrayList<>();
    private final LinkedHashMap<String, PdfEntry> selected = new LinkedHashMap<>();

    private ActivityResultLauncher<Intent> folderLauncher;
    private LinearLayout root;
    private LinearLayout listContainer;
    private TextView folderLabel;
    private TextView selectedCount;
    private TextView emptyView;
    private ProgressBar progress;
    private MaterialButton confirmButton;

    private Uri rootUri;
    private String rootLabelText = "";
    private boolean reviewMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        folderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handleFolderResult(result.getResultCode(), result.getData()));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (reviewMode) {
                    reviewMode = false;
                    buildSelectionScreen();
                    renderPdfRows();
                } else {
                    finish();
                }
            }
        });

        restoreRootFolder();
        buildSelectionScreen();
        if (rootUri == null) {
            chooseFolder();
        } else {
            loadPdfEntries();
        }
    }

    private void restoreRootFolder() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_ROOT_URI, null);
        rootLabelText = prefs.getString(KEY_ROOT_LABEL, "");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                rootUri = Uri.parse(raw);
            } catch (RuntimeException ignored) {
                rootUri = null;
            }
        }
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        folderLauncher.launch(intent);
    }

    private void handleFolderResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            if (rootUri == null) finish();
            return;
        }

        Uri uri = data.getData();
        int takeFlags = data.getFlags()
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) {
            // The grant still works for this session even if a provider cannot persist it.
        }

        DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
        String label = folder == null || folder.getName() == null
            ? uri.getLastPathSegment()
            : folder.getName();
        if (label == null || label.trim().isEmpty()) label = getString(R.string.merge_picker_select_folder);

        rootUri = uri;
        rootLabelText = label;
        selected.clear();
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_URI, uri.toString())
            .putString(KEY_ROOT_LABEL, label)
            .apply();

        buildSelectionScreen();
        loadPdfEntries();
    }

    private void buildSelectionScreen() {
        reviewMode = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.parseColor("#F6F8FC"));

        TextView title = new TextView(this);
        title.setText(R.string.merge_picker_title);
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.merge_picker_hint);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#637083"));
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(5);
        root.addView(hint, hintParams);

        LinearLayout folderRow = new LinearLayout(this);
        folderRow.setOrientation(LinearLayout.HORIZONTAL);
        folderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams folderRowParams = matchWrap();
        folderRowParams.topMargin = dp(14);
        root.addView(folderRow, folderRowParams);

        folderLabel = new TextView(this);
        folderLabel.setText(getString(
            R.string.merge_picker_folder,
            rootLabelText == null || rootLabelText.isEmpty()
                ? getString(R.string.merge_picker_select_folder)
                : rootLabelText));
        folderLabel.setTextSize(13);
        folderLabel.setTextColor(Color.parseColor("#3C4B60"));
        folderRow.addView(folderLabel, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton changeFolder = compactButton(R.string.merge_picker_change_folder);
        changeFolder.setOnClickListener(v -> chooseFolder());
        folderRow.addView(changeFolder, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

        selectedCount = new TextView(this);
        selectedCount.setTextSize(14);
        selectedCount.setTextColor(Color.parseColor("#2159C9"));
        selectedCount.setTypeface(selectedCount.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(12);
        root.addView(selectedCount, countParams);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(18);
        root.addView(progress, progressParams);

        emptyView = new TextView(this);
        emptyView.setText(R.string.merge_picker_loading);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(14);
        emptyView.setTextColor(Color.parseColor("#637083"));
        LinearLayout.LayoutParams emptyParams = matchWrap();
        emptyParams.topMargin = dp(18);
        root.addView(emptyView, emptyParams);

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        root.addView(scroll, scrollParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = matchWrap();
        bottomParams.topMargin = dp(12);
        root.addView(bottom, bottomParams);

        MaterialButton cancel = compactButton(R.string.cancel);
        cancel.setOnClickListener(v -> finish());
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));

        confirmButton = compactButton(R.string.merge_picker_confirm);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        confirmParams.leftMargin = dp(10);
        bottom.addView(confirmButton, confirmParams);
        confirmButton.setOnClickListener(v -> showReviewScreen());

        updateSelectionState();
        setContentView(root);
    }

    private void loadPdfEntries() {
        if (rootUri == null) return;
        setLoading(true);
        executor.execute(() -> {
            ArrayList<PdfEntry> found = new ArrayList<>();
            try {
                DocumentFile folder = DocumentFile.fromTreeUri(this, rootUri);
                if (folder != null && folder.exists() && folder.isDirectory()) {
                    collectPdfs(folder, found, 0);
                }
                found.sort(Comparator.comparing(
                    entry -> entry.name == null ? "" : entry.name,
                    String.CASE_INSENSITIVE_ORDER));
                runOnUiThread(() -> {
                    entries.clear();
                    entries.addAll(found);
                    setLoading(false);
                    renderPdfRows();
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
                });
            }
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
                || (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"));
            if (pdf) output.add(new PdfEntry(child.getUri(), name == null ? "PDF" : name));
        }
    }

    private void renderPdfRows() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        if (entries.isEmpty()) emptyView.setText(R.string.merge_picker_empty);

        for (PdfEntry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(10), dp(8));
            row.setMinimumHeight(dp(58));
            row.setClickable(true);
            row.setFocusable(true);

            CheckBox check = new CheckBox(this);
            check.setClickable(false);
            check.setFocusable(false);
            check.setChecked(selected.containsKey(entry.uri.toString()));
            row.addView(check, new LinearLayout.LayoutParams(dp(42), dp(42)));

            TextView name = new TextView(this);
            name.setText(entry.name);
            name.setTextSize(14);
            name.setTextColor(Color.parseColor("#15233A"));
            name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            updateRowSelectionBackground(row, check.isChecked());
            row.setOnClickListener(v -> {
                String key = entry.uri.toString();
                if (selected.containsKey(key)) selected.remove(key);
                else selected.put(key, entry);
                boolean checked = selected.containsKey(key);
                check.setChecked(checked);
                updateRowSelectionBackground(row, checked);
                updateSelectionState();
            });

            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(6);
            listContainer.addView(row, rowParams);
        }
        updateSelectionState();
    }

    private void updateRowSelectionBackground(LinearLayout row, boolean selectedNow) {
        row.setBackgroundColor(Color.parseColor(selectedNow ? "#E4EEFF" : "#FFFFFF"));
    }

    private void updateSelectionState() {
        if (selectedCount != null) {
            selectedCount.setText(getString(R.string.merge_picker_selected_count, selected.size()));
        }
        if (confirmButton != null) {
            confirmButton.setEnabled(selected.size() >= 2);
            confirmButton.setAlpha(selected.size() >= 2 ? 1f : 0.45f);
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (emptyView != null) {
            emptyView.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) emptyView.setText(R.string.merge_picker_loading);
        }
        if (confirmButton != null) confirmButton.setEnabled(!loading && selected.size() >= 2);
    }

    private void showReviewScreen() {
        if (selected.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            return;
        }
        reviewMode = true;
        ArrayList<PdfEntry> reviewEntries = new ArrayList<>(selected.values());

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.parseColor("#F6F8FC"));

        TextView title = new TextView(this);
        title.setText(R.string.merge_review_title);
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.merge_review_hint);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#637083"));
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(5);
        hintParams.bottomMargin = dp(10);
        root.addView(hint, hintParams);

        ArrayList<String> names = new ArrayList<>();
        for (PdfEntry entry : reviewEntries) names.add(entry.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_list_item_single_choice, names);
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(adapter);
        list.setItemChecked(0, true);
        root.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout orderRow = new LinearLayout(this);
        orderRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams orderRowParams = matchWrap();
        orderRowParams.topMargin = dp(8);
        root.addView(orderRow, orderRowParams);

        MaterialButton up = compactButton(R.string.move_up);
        MaterialButton down = compactButton(R.string.move_down);
        MaterialButton remove = compactButton(R.string.merge_review_remove);
        orderRow.addView(up, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        downParams.leftMargin = dp(6);
        orderRow.addView(down, downParams);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        removeParams.leftMargin = dp(6);
        orderRow.addView(remove, removeParams);

        up.setOnClickListener(v -> moveReviewItem(list, adapter, reviewEntries, names, -1));
        down.setOnClickListener(v -> moveReviewItem(list, adapter, reviewEntries, names, 1));
        remove.setOnClickListener(v -> {
            int position = list.getCheckedItemPosition();
            if (position == ListView.INVALID_POSITION || position < 0 || position >= reviewEntries.size()) return;
            PdfEntry removed = reviewEntries.remove(position);
            names.remove(position);
            selected.remove(removed.uri.toString());
            adapter.notifyDataSetChanged();
            if (!reviewEntries.isEmpty()) {
                int next = Math.min(position, reviewEntries.size() - 1);
                list.setItemChecked(next, true);
            }
        });

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomParams = matchWrap();
        bottomParams.topMargin = dp(12);
        root.addView(bottom, bottomParams);

        MaterialButton back = compactButton(R.string.merge_review_back);
        back.setOnClickListener(v -> {
            reviewMode = false;
            buildSelectionScreen();
            renderPdfRows();
        });
        bottom.addView(back, new LinearLayout.LayoutParams(0, dp(52), 1f));

        MaterialButton done = compactButton(R.string.merge_review_done);
        done.setOnClickListener(v -> {
            if (reviewEntries.size() < 2) {
                Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
                return;
            }
            ArrayList<Uri> resultUris = new ArrayList<>();
            for (PdfEntry entry : reviewEntries) resultUris.add(entry.uri);
            Intent result = new Intent();
            result.putParcelableArrayListExtra(EXTRA_SELECTED_URIS, resultUris);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        doneParams.leftMargin = dp(10);
        bottom.addView(done, doneParams);

        setContentView(root);
    }

    private void moveReviewItem(
        ListView list,
        ArrayAdapter<String> adapter,
        ArrayList<PdfEntry> reviewEntries,
        ArrayList<String> names,
        int direction
    ) {
        int selectedPosition = list.getCheckedItemPosition();
        if (selectedPosition == ListView.INVALID_POSITION) selectedPosition = 0;
        int target = selectedPosition + direction;
        if (target < 0 || target >= reviewEntries.size()) return;

        PdfEntry entry = reviewEntries.remove(selectedPosition);
        reviewEntries.add(target, entry);
        String name = names.remove(selectedPosition);
        names.add(target, name);
        adapter.notifyDataSetChanged();
        list.setItemChecked(target, true);
        list.setSelection(target);

        selected.clear();
        for (PdfEntry item : reviewEntries) selected.put(item.uri.toString(), item);
    }

    private MaterialButton compactButton(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(13);
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
