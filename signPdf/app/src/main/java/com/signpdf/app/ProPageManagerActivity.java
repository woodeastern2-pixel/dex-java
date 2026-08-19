package com.signpdf.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.signpdf.app.util.PdfProTools;
import com.signpdf.app.util.SaveManager;
import com.signpdf.app.util.UsageQuotaManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dedicated Pro page management screen: reorder, rotate, delete, extract and split. */
public class ProPageManagerActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "extra_pdf_path";
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";

    private String pdfPath;
    private String displayName;
    private int currentPage = 0;
    private PdfRenderer renderer;
    private ParcelFileDescriptor parcelFd;

    private ImageView preview;
    private TextView pageStatus;
    private ProgressBar progress;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private MaterialButton rotateButton;
    private MaterialButton deleteButton;
    private MaterialButton moveUpButton;
    private MaterialButton moveDownButton;
    private MaterialButton extractButton;
    private MaterialButton splitButton;
    private MaterialButton saveButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SaveManager saveManager;
    private boolean busy;

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

        pdfPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        displayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);
        if (displayName == null || displayName.trim().isEmpty()) displayName = "document.pdf";
        if (pdfPath == null || !new File(pdfPath).exists()) {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        saveManager = new SaveManager(this);
        buildUi();
        openRenderer(0);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F6F8FC"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.page_tools_title);
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(displayName);
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.parseColor("#637083"));
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        preview = new ImageView(this);
        preview.setBackgroundColor(Color.WHITE);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(460));
        previewParams.bottomMargin = dp(12);
        root.addView(preview, previewParams);

        pageStatus = new TextView(this);
        pageStatus.setGravity(Gravity.CENTER);
        pageStatus.setTextSize(14);
        pageStatus.setTextColor(Color.parseColor("#3C4B60"));
        pageStatus.setPadding(0, dp(4), 0, dp(8));
        root.addView(pageStatus);

        LinearLayout nav = horizontalRow();
        prevButton = actionButton(R.string.previous_page);
        nextButton = actionButton(R.string.next_page);
        nav.addView(prevButton, weightedButtonParams());
        LinearLayout.LayoutParams nextParams = weightedButtonParams();
        nextParams.leftMargin = dp(8);
        nav.addView(nextButton, nextParams);
        root.addView(nav);

        LinearLayout row1 = horizontalRow();
        rotateButton = actionButton(R.string.page_rotate);
        deleteButton = actionButton(R.string.page_delete);
        row1.addView(rotateButton, weightedButtonParams());
        LinearLayout.LayoutParams deleteParams = weightedButtonParams();
        deleteParams.leftMargin = dp(8);
        row1.addView(deleteButton, deleteParams);
        addRow(root, row1);

        LinearLayout row2 = horizontalRow();
        moveUpButton = actionButton(R.string.page_move_up);
        moveDownButton = actionButton(R.string.page_move_down);
        row2.addView(moveUpButton, weightedButtonParams());
        LinearLayout.LayoutParams downParams = weightedButtonParams();
        downParams.leftMargin = dp(8);
        row2.addView(moveDownButton, downParams);
        addRow(root, row2);

        LinearLayout row3 = horizontalRow();
        extractButton = actionButton(R.string.page_extract);
        splitButton = actionButton(R.string.page_split);
        row3.addView(extractButton, weightedButtonParams());
        LinearLayout.LayoutParams splitParams = weightedButtonParams();
        splitParams.leftMargin = dp(8);
        row3.addView(splitButton, splitParams);
        addRow(root, row3);

        saveButton = actionButton(R.string.save);
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            Color.parseColor("#2159C9")));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        saveParams.topMargin = dp(14);
        root.addView(saveButton, saveParams);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(14);
        progress.setVisibility(View.GONE);
        root.addView(progress, progressParams);

        prevButton.setOnClickListener(v -> showPage(currentPage - 1));
        nextButton.setOnClickListener(v -> showPage(currentPage + 1));
        rotateButton.setOnClickListener(v -> confirmMutation(Mutation.ROTATE));
        deleteButton.setOnClickListener(v -> confirmMutation(Mutation.DELETE));
        moveUpButton.setOnClickListener(v -> confirmMutation(Mutation.MOVE_UP));
        moveDownButton.setOnClickListener(v -> confirmMutation(Mutation.MOVE_DOWN));
        extractButton.setOnClickListener(v -> confirmExtractCurrentPage());
        splitButton.setOnClickListener(v -> confirmSplitAllPages());
        saveButton.setOnClickListener(v -> confirmSaveManagedPdf());

        setContentView(scroll);
    }

    private enum Mutation { ROTATE, DELETE, MOVE_UP, MOVE_DOWN }

    private void confirmMutation(Mutation mutation) {
        if (busy || renderer == null) return;
        int pageCount = renderer.getPageCount();
        if (mutation == Mutation.MOVE_UP && currentPage <= 0) return;
        if (mutation == Mutation.MOVE_DOWN && currentPage >= pageCount - 1) return;
        if (mutation == Mutation.DELETE && pageCount <= 1) {
            Toast.makeText(this, R.string.page_last_delete_blocked, Toast.LENGTH_SHORT).show();
            return;
        }

        int pageNumber = currentPage + 1;
        int titleRes;
        int messageRes;
        int positiveRes;
        switch (mutation) {
            case ROTATE:
                titleRes = R.string.page_rotate;
                messageRes = R.string.confirm_rotate_message;
                positiveRes = R.string.action_rotate;
                break;
            case DELETE:
                titleRes = R.string.page_delete;
                messageRes = R.string.confirm_delete_page_message;
                positiveRes = R.string.delete;
                break;
            case MOVE_UP:
                titleRes = R.string.page_move_up;
                messageRes = R.string.confirm_move_page_up_message;
                positiveRes = R.string.action_move;
                break;
            case MOVE_DOWN:
            default:
                titleRes = R.string.page_move_down;
                messageRes = R.string.confirm_move_page_down_message;
                positiveRes = R.string.action_move;
                break;
        }

        new AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(getString(messageRes, pageNumber))
            .setPositiveButton(positiveRes, (d, w) -> mutatePage(mutation))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void confirmExtractCurrentPage() {
        if (busy || renderer == null) return;
        int pageNumber = currentPage + 1;
        new AlertDialog.Builder(this)
            .setTitle(R.string.page_extract)
            .setMessage(getString(R.string.confirm_extract_page_message, pageNumber))
            .setPositiveButton(R.string.action_extract, (d, w) -> extractCurrentPage())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void confirmSplitAllPages() {
        if (busy || renderer == null) return;
        int pageCount = renderer.getPageCount();
        new AlertDialog.Builder(this)
            .setTitle(R.string.page_split)
            .setMessage(getString(R.string.confirm_split_pages_message, pageCount))
            .setPositiveButton(R.string.action_split, (d, w) -> splitAllPages())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void confirmSaveManagedPdf() {
        if (busy) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.confirm_save_managed_title)
            .setMessage(R.string.confirm_save_managed_message)
            .setPositiveButton(R.string.save, (d, w) -> saveManagedPdf())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void mutatePage(Mutation mutation) {
        if (busy || renderer == null) return;
        int pageCount = renderer.getPageCount();
        if (mutation == Mutation.MOVE_UP && currentPage <= 0) return;
        if (mutation == Mutation.MOVE_DOWN && currentPage >= pageCount - 1) return;
        if (mutation == Mutation.DELETE && pageCount <= 1) return;

        int sourceIndex = currentPage;
        int targetIndex = sourceIndex;
        if (mutation == Mutation.MOVE_UP) targetIndex = sourceIndex - 1;
        if (mutation == Mutation.MOVE_DOWN) targetIndex = sourceIndex + 1;
        if (mutation == Mutation.DELETE) targetIndex = Math.min(sourceIndex, pageCount - 2);
        final int reopenIndex = Math.max(0, targetIndex);

        setBusy(true);
        closeRenderer();
        executor.execute(() -> {
            File source = new File(pdfPath);
            File temp = null;
            try {
                temp = File.createTempFile("signpdf_page_edit_", ".pdf", getCacheDir());
                switch (mutation) {
                    case ROTATE:
                        PdfProTools.rotatePage(this, source, sourceIndex, temp);
                        break;
                    case DELETE:
                        PdfProTools.deletePage(this, source, sourceIndex, temp);
                        break;
                    case MOVE_UP:
                        PdfProTools.movePage(this, source, sourceIndex, -1, temp);
                        break;
                    case MOVE_DOWN:
                        PdfProTools.movePage(this, source, sourceIndex, 1, temp);
                        break;
                }
                replaceFile(temp, source);
                runOnUiThread(() -> {
                    setBusy(false);
                    openRenderer(reopenIndex);
                    Toast.makeText(this, R.string.page_tool_done, Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    openRenderer(Math.max(0, sourceIndex));
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (temp != null) temp.delete();
            }
        });
    }

    private void extractCurrentPage() {
        if (busy || renderer == null) return;
        int pageIndex = currentPage;
        setBusy(true);
        executor.execute(() -> {
            File temp = null;
            try {
                temp = File.createTempFile("signpdf_extract_", ".pdf", getCacheDir());
                PdfProTools.extractPage(this, new File(pdfPath), pageIndex, temp);
                String name = saveManager.timestampedName(
                    "page_" + (pageIndex + 1) + "_", displayName);
                Uri saved = saveManager.saveGenerated(temp, name);
                String path = saveManager.getDisplayPath(saved);
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.page_extract_saved)
                        .setMessage(path)
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (temp != null) temp.delete();
            }
        });
    }

    private void splitAllPages() {
        if (busy || renderer == null) return;
        setBusy(true);
        executor.execute(() -> {
            File splitDir = new File(getCacheDir(), "split_" + System.currentTimeMillis());
            try {
                List<File> parts = PdfProTools.splitAll(this, new File(pdfPath), splitDir);
                for (int i = 0; i < parts.size(); i++) {
                    saveManager.saveGenerated(parts.get(i), saveManager.timestampedName(
                        "split_p" + (i + 1) + "_", displayName));
                }
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.page_tools_title)
                        .setMessage(getString(R.string.page_split_saved, parts.size()))
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            } finally {
                deleteRecursively(splitDir);
            }
        });
    }

    private void saveManagedPdf() {
        if (busy) return;
        setBusy(true);
        executor.execute(() -> {
            try {
                Uri saved = saveManager.saveGenerated(
                    new File(pdfPath),
                    saveManager.timestampedName("managed_", displayName));
                String path = saveManager.getDisplayPath(saved);
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.save_success)
                        .setMessage(getString(R.string.saved_path, path))
                        .setPositiveButton(R.string.confirm, null)
                        .show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openRenderer(int requestedIndex) {
        closeRenderer();
        try {
            parcelFd = ParcelFileDescriptor.open(new File(pdfPath), ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(parcelFd);
            if (renderer.getPageCount() <= 0) throw new IOException("PDF has no pages.");
            currentPage = Math.max(0, Math.min(requestedIndex, renderer.getPageCount() - 1));
            showPage(currentPage);
        } catch (IOException e) {
            Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showPage(int index) {
        if (renderer == null || busy) return;
        if (index < 0 || index >= renderer.getPageCount()) return;
        currentPage = index;

        try (PdfRenderer.Page page = renderer.openPage(index)) {
            int targetWidth = Math.max(900, getResources().getDisplayMetrics().widthPixels * 2);
            float ratio = page.getHeight() / (float) Math.max(1, page.getWidth());
            int targetHeight = Math.max(1, Math.round(targetWidth * ratio));
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            preview.setImageBitmap(bitmap);
        }
        updateButtons();
    }

    private void updateButtons() {
        if (renderer == null) return;
        int count = renderer.getPageCount();
        pageStatus.setText(getString(R.string.page_tools_status, currentPage + 1, count));
        prevButton.setEnabled(currentPage > 0 && !busy);
        nextButton.setEnabled(currentPage < count - 1 && !busy);
        moveUpButton.setEnabled(currentPage > 0 && !busy);
        moveDownButton.setEnabled(currentPage < count - 1 && !busy);
        deleteButton.setEnabled(count > 1 && !busy);
    }

    private void setBusy(boolean value) {
        busy = value;
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
        MaterialButton[] buttons = {
            prevButton, nextButton, rotateButton, deleteButton, moveUpButton,
            moveDownButton, extractButton, splitButton, saveButton
        };
        for (MaterialButton button : buttons) {
            if (button != null) button.setEnabled(!value);
        }
        if (!value) updateButtons();
    }

    private void closeRenderer() {
        if (preview != null) preview.setImageDrawable(null);
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (parcelFd != null) {
            try { parcelFd.close(); } catch (IOException ignored) { }
            parcelFd = null;
        }
    }

    private void replaceFile(File source, File destination) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void addRow(LinearLayout root, LinearLayout row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        root.addView(row, params);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(50), 1f);
    }

    private MaterialButton actionButton(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setTextSize(13);
        button.setAllCaps(false);
        return button;
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
        closeRenderer();
        executor.shutdown();
        super.onDestroy();
    }
}
