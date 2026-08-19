package com.signpdf.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.signpdf.app.converter.AdvancedImageToPdfConverter;
import com.signpdf.app.converter.DocumentToPdfConverter;
import com.signpdf.app.util.PdfProTools;
import com.signpdf.app.util.PdfSecurityManager;
import com.signpdf.app.util.SaveManager;
import com.signpdf.app.util.UsageQuotaManager;
import com.signpdf.app.viewer.PdfViewerActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Entry point for SignPDF Pro-only tools. */
public class ProToolsActivity extends AppCompatActivity {

    private FilePickerHelper filePicker;
    private SaveManager saveManager;
    private ProgressBar progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UsageQuotaManager.isPro()) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.pro_locked_title)
                .setMessage(R.string.pro_locked_message)
                .setPositiveButton(R.string.confirm, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
            return;
        }

        filePicker = new FilePickerHelper(this);
        saveManager = new SaveManager(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F6F8FC"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.pro_tools_title);
        title.setTextSize(26);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.pro_description);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#637083"));
        subtitle.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(20);
        root.addView(subtitle, subtitleParams);

        MaterialButton merge = proButton(R.string.tool_merge_pdf);
        MaterialButton pages = proButton(R.string.tool_page_manage);
        MaterialButton advancedImages = proButton(R.string.tool_advanced_image_pdf);
        root.addView(merge, fullButtonParams());
        root.addView(pages, spacedButtonParams());
        root.addView(advancedImages, spacedButtonParams());

        TextView detail = new TextView(this);
        detail.setText(R.string.pro_benefit);
        detail.setTextSize(13);
        detail.setTextColor(Color.parseColor("#2159C9"));
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(22);
        root.addView(detail, detailParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(18);
        root.addView(progress, progressParams);

        merge.setOnClickListener(v -> pickPdfsForMerge());
        pages.setOnClickListener(v -> pickPdfForPageManagement());
        advancedImages.setOnClickListener(v -> pickImagesForAdvancedPdf());

        setContentView(scroll);
    }

    private MaterialButton proButton(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#17325C"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#D5DDEA")));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(16));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), 0, dp(18), 0);
        return button;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
    }

    private LinearLayout.LayoutParams spacedButtonParams() {
        LinearLayout.LayoutParams params = fullButtonParams();
        params.topMargin = dp(10);
        return params;
    }

    private void pickPdfsForMerge() {
        filePicker.openMultiplePdfPicker(new FilePickerHelper.OnFilesPickedListener() {
            @Override
            public void onFilesPicked(List<Uri> uris) {
                if (uris.size() < 2) {
                    Toast.makeText(ProToolsActivity.this, R.string.merge_pick_two,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                mergePdfs(uris);
            }

            @Override public void onCancelled() { }
        });
    }

    private void mergePdfs(List<Uri> uris) {
        setBusy(true);
        executor.execute(() -> {
            ArrayList<File> sources = new ArrayList<>();
            File output = null;
            try {
                for (Uri uri : uris) {
                    File file = copyToCache(uri, getFileName(uri));
                    if (PdfSecurityManager.requiresPassword(file)) {
                        throw new IOException(getString(R.string.merge_password_unsupported));
                    }
                    sources.add(file);
                }

                output = File.createTempFile("signpdf_merged_", ".pdf", getCacheDir());
                PdfProTools.merge(this, sources, output);
                String outputName = saveManager.timestampedName("merged_", "documents.pdf");
                Uri saved = saveManager.saveGenerated(output, outputName);
                String displayPath = saveManager.getDisplayPath(saved);
                File finalOutput = output;

                runOnUiThread(() -> {
                    setBusy(false);
                    showOutputReady(R.string.merge_success, displayPath, finalOutput, outputName);
                });
            } catch (IOException e) {
                if (output != null) output.delete();
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                for (File file : sources) file.delete();
            }
        });
    }

    private void pickPdfForPageManagement() {
        filePicker.openPdfPicker(new FilePickerHelper.OnFilePickedListener() {
            @Override
            public void onFilePicked(Uri uri, String mimeType) {
                setBusy(true);
                executor.execute(() -> {
                    try {
                        String name = getFileName(uri);
                        File cached = copyToCache(uri, name);
                        if (PdfSecurityManager.requiresPassword(cached)) {
                            cached.delete();
                            throw new IOException(getString(R.string.merge_password_unsupported));
                        }
                        runOnUiThread(() -> {
                            setBusy(false);
                            Intent intent = new Intent(ProToolsActivity.this,
                                ProPageManagerActivity.class);
                            intent.putExtra(ProPageManagerActivity.EXTRA_PDF_PATH,
                                cached.getAbsolutePath());
                            intent.putExtra(ProPageManagerActivity.EXTRA_DISPLAY_NAME, name);
                            startActivity(intent);
                        });
                    } catch (IOException e) {
                        runOnUiThread(() -> {
                            setBusy(false);
                            Toast.makeText(ProToolsActivity.this, safeMessage(e),
                                Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override public void onCancelled() { }
        });
    }

    private void pickImagesForAdvancedPdf() {
        filePicker.openMultipleImagePicker(new FilePickerHelper.OnFilesPickedListener() {
            @Override
            public void onFilesPicked(List<Uri> uris) {
                if (uris.isEmpty()) {
                    Toast.makeText(ProToolsActivity.this, R.string.select_images,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                showAdvancedImageOptions(new ArrayList<>(uris));
            }

            @Override public void onCancelled() { }
        });
    }

    private void showAdvancedImageOptions(ArrayList<Uri> orderedUris) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(4), dp(20), 0);

        TextView hint = label(R.string.advanced_image_order_hint);
        container.addView(hint);

        ArrayList<String> names = new ArrayList<>();
        for (Uri uri : orderedUris) names.add(getFileName(uri));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_list_item_single_choice, names);
        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(adapter);
        list.setItemChecked(0, true);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        listParams.topMargin = dp(6);
        container.addView(list, listParams);

        LinearLayout orderButtons = new LinearLayout(this);
        orderButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton up = smallButton(R.string.move_up);
        MaterialButton down = smallButton(R.string.move_down);
        orderButtons.addView(up, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        downParams.leftMargin = dp(8);
        orderButtons.addView(down, downParams);
        container.addView(orderButtons);

        up.setOnClickListener(v -> moveSelected(list, adapter, orderedUris, names, -1));
        down.setOnClickListener(v -> moveSelected(list, adapter, orderedUris, names, 1));

        TextView pageSizeLabel = label(R.string.page_size);
        pageSizeLabel.setPadding(0, dp(12), 0, dp(2));
        container.addView(pageSizeLabel);
        Spinner pageSize = new Spinner(this);
        String[] pageSizes = {
            getString(R.string.page_size_a4),
            getString(R.string.page_size_letter),
            getString(R.string.page_size_fit)
        };
        pageSize.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, pageSizes));
        container.addView(pageSize);

        TextView marginLabel = label(R.string.margin);
        marginLabel.setPadding(0, dp(10), 0, 0);
        container.addView(marginLabel);
        TextView marginValue = new TextView(this);
        SeekBar margin = new SeekBar(this);
        margin.setMax(4);
        margin.setProgress(1);
        marginValue.setText("12 pt");
        container.addView(marginValue);
        container.addView(margin);
        margin.setOnSeekBarChangeListener(simpleSeek(progress ->
            marginValue.setText((progress * 12) + " pt")));

        TextView qualityLabel = label(R.string.quality);
        qualityLabel.setPadding(0, dp(8), 0, 0);
        container.addView(qualityLabel);
        TextView qualityValue = new TextView(this);
        SeekBar quality = new SeekBar(this);
        quality.setMax(60);
        quality.setProgress(45);
        qualityValue.setText("85%");
        container.addView(qualityValue);
        container.addView(quality);
        quality.setOnSeekBarChangeListener(simpleSeek(progress ->
            qualityValue.setText((40 + progress) + "%")));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.advanced_image_title)
            .setView(container)
            .setPositiveButton(R.string.create_pdf, null)
            .setNegativeButton(R.string.cancel, null)
            .create();

        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                AdvancedImageToPdfConverter.PageSize size;
                switch (pageSize.getSelectedItemPosition()) {
                    case 1:
                        size = AdvancedImageToPdfConverter.PageSize.LETTER;
                        break;
                    case 2:
                        size = AdvancedImageToPdfConverter.PageSize.FIT_IMAGE;
                        break;
                    default:
                        size = AdvancedImageToPdfConverter.PageSize.A4;
                }
                AdvancedImageToPdfConverter.Options options =
                    new AdvancedImageToPdfConverter.Options(
                        size, margin.getProgress() * 12, 40 + quality.getProgress());
                dialog.dismiss();
                createAdvancedImagePdf(orderedUris, options);
            }));
        dialog.show();
    }

    private void moveSelected(
        ListView list,
        ArrayAdapter<String> adapter,
        ArrayList<Uri> uris,
        ArrayList<String> names,
        int direction
    ) {
        int selected = list.getCheckedItemPosition();
        if (selected == ListView.INVALID_POSITION) selected = 0;
        int target = selected + direction;
        if (target < 0 || target >= uris.size()) return;

        Uri uri = uris.remove(selected);
        uris.add(target, uri);
        String name = names.remove(selected);
        names.add(target, name);
        adapter.notifyDataSetChanged();
        list.setItemChecked(target, true);
        list.setSelection(target);
    }

    private void createAdvancedImagePdf(
        ArrayList<Uri> orderedUris,
        AdvancedImageToPdfConverter.Options options
    ) {
        setBusy(true);
        executor.execute(() -> {
            ArrayList<File> images = new ArrayList<>();
            File output = null;
            try {
                for (Uri uri : orderedUris) images.add(copyToCache(uri, getFileName(uri)));
                output = File.createTempFile("signpdf_images_", ".pdf", getCacheDir());
                new AdvancedImageToPdfConverter().convert(images, output, options);
                String outputName = saveManager.timestampedName("images_", "collection.pdf");
                Uri saved = saveManager.saveGenerated(output, outputName);
                String displayPath = saveManager.getDisplayPath(saved);
                File finalOutput = output;

                runOnUiThread(() -> {
                    setBusy(false);
                    showOutputReady(R.string.advanced_image_success,
                        displayPath, finalOutput, outputName);
                });
            } catch (IOException | DocumentToPdfConverter.ConversionException e) {
                if (output != null) output.delete();
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                for (File file : images) file.delete();
            }
        });
    }

    private void showOutputReady(int titleRes, String path, File pdfFile, String displayName) {
        new AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(getString(R.string.saved_path, path))
            .setPositiveButton(R.string.open_file, (d, w) -> {
                Intent intent = new Intent(this, PdfViewerActivity.class);
                intent.putExtra(PdfViewerActivity.EXTRA_PDF_PATH, pdfFile.getAbsolutePath());
                intent.putExtra(PdfViewerActivity.EXTRA_DISPLAY_NAME, displayName);
                startActivity(intent);
            })
            .setNegativeButton(R.string.confirm, null)
            .show();
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(IntConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                consumer.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private interface IntConsumer { void accept(int value); }

    private MaterialButton smallButton(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
        return button;
    }

    private TextView label(int textRes) {
        TextView view = new TextView(this);
        view.setText(textRes);
        view.setTextSize(13);
        view.setTextColor(Color.parseColor("#3C4B60"));
        return view;
    }

    private File copyToCache(Uri uri, String fileName) throws IOException {
        String safeName = fileName == null ? "file" :
            fileName.replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
        File temp = new File(getCacheDir(),
            "pro_" + System.currentTimeMillis() + "_" + safeName);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temp)) {
            if (input == null) throw new IOException(getString(R.string.file_not_found));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return temp;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (RuntimeException ignored) { }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result == null ? "document" : result;
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private String safeMessage(Exception error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
            ? getString(R.string.unknown_error) : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
