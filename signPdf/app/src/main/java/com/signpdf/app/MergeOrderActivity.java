package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reviews, previews and reorders PDFs selected by the Android system document picker. */
public class MergeOrderActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_URIS = "selected_pdf_uris";

    private final ArrayList<Uri> orderedUris = new ArrayList<>();
    private final ArrayList<String> displayNames = new ArrayList<>();
    private final Map<String, PreviewInfo> previewCache = new ConcurrentHashMap<>();
    private final Set<String> previewLoading = ConcurrentHashMap.newKeySet();
    private final Map<String, ImageView> currentPreviewViews = new HashMap<>();
    private final Map<String, TextView> currentMetaViews = new HashMap<>();
    private final ExecutorService previewExecutor = Executors.newFixedThreadPool(2);

    private LinearLayout listContainer;
    private ScrollView listScroll;
    private TextView countView;
    private MaterialButton confirmButton;
    private int selectedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Uri> incoming = getIntent()
            .getParcelableArrayListExtra(EXTRA_SELECTED_URIS);
        if (incoming != null) orderedUris.addAll(incoming);
        if (orderedUris.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        for (Uri uri : orderedUris) displayNames.add(getFileName(uri));
        buildUi();
        refreshList(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(18));
        root.setBackgroundColor(Color.parseColor("#F6F8FC"));

        TextView title = new TextView(this);
        title.setText(R.string.merge_order_title);
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.merge_order_hint);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#637083"));
        hint.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(6);
        root.addView(hint, hintParams);

        countView = new TextView(this);
        countView.setTextSize(14);
        countView.setTextColor(Color.parseColor("#2159C9"));
        countView.setTypeface(countView.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(12);
        countParams.bottomMargin = dp(8);
        root.addView(countView, countParams);

        listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listContainer, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout orderRow = new LinearLayout(this);
        orderRow.setOrientation(LinearLayout.HORIZONTAL);
        orderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams orderParams = matchWrap();
        orderParams.topMargin = dp(10);
        root.addView(orderRow, orderParams);

        MaterialButton up = button(R.string.move_up);
        MaterialButton down = button(R.string.move_down);
        MaterialButton remove = button(R.string.merge_order_remove);
        orderRow.addView(up, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        downParams.leftMargin = dp(6);
        orderRow.addView(down, downParams);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        removeParams.leftMargin = dp(6);
        orderRow.addView(remove, removeParams);

        up.setOnClickListener(v -> moveSelected(-1));
        down.setOnClickListener(v -> moveSelected(1));
        remove.setOnClickListener(v -> removeSelected());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = matchWrap();
        bottomParams.topMargin = dp(10);
        root.addView(bottom, bottomParams);

        MaterialButton cancel = button(R.string.cancel);
        cancel.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));

        confirmButton = button(R.string.confirm);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        confirmParams.leftMargin = dp(10);
        bottom.addView(confirmButton, confirmParams);
        confirmButton.setOnClickListener(v -> returnOrderedFiles());

        setContentView(root);
    }

    private void refreshList(int requestedSelection) {
        if (orderedUris.isEmpty()) selectedIndex = -1;
        else selectedIndex = Math.max(0, Math.min(requestedSelection, orderedUris.size() - 1));

        currentPreviewViews.clear();
        currentMetaViews.clear();
        listContainer.removeAllViews();

        for (int i = 0; i < orderedUris.size(); i++) {
            final int index = i;
            Uri uri = orderedUris.get(i);
            String name = displayNames.get(i);
            String key = uri.toString();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(12), dp(10));
            row.setBackground(rowBackground(i == selectedIndex));
            row.setOnClickListener(v -> refreshList(index));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(8);
            listContainer.addView(row, rowParams);

            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            thumbnail.setAdjustViewBounds(true);
            thumbnail.setBackgroundColor(Color.WHITE);
            thumbnail.setContentDescription(getString(R.string.merge_preview_title, name));
            thumbnail.setOnClickListener(v -> showLargePreview(uri, name));
            row.addView(thumbnail, new LinearLayout.LayoutParams(dp(82), dp(112)));

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textParams.leftMargin = dp(12);
            row.addView(textColumn, textParams);

            TextView order = new TextView(this);
            order.setText(String.valueOf(i + 1));
            order.setTextSize(20);
            order.setTextColor(Color.parseColor("#2159C9"));
            order.setTypeface(order.getTypeface(), android.graphics.Typeface.BOLD);
            textColumn.addView(order);

            TextView fileName = new TextView(this);
            fileName.setText(name);
            fileName.setTextSize(14);
            fileName.setTextColor(Color.parseColor("#15233A"));
            fileName.setMaxLines(2);
            LinearLayout.LayoutParams nameParams = matchWrap();
            nameParams.topMargin = dp(3);
            textColumn.addView(fileName, nameParams);

            TextView meta = new TextView(this);
            meta.setTextSize(12);
            meta.setTextColor(Color.parseColor("#637083"));
            LinearLayout.LayoutParams metaParams = matchWrap();
            metaParams.topMargin = dp(5);
            textColumn.addView(meta, metaParams);

            currentPreviewViews.put(key, thumbnail);
            currentMetaViews.put(key, meta);
            PreviewInfo cached = previewCache.get(key);
            if (cached != null) {
                applyPreview(thumbnail, meta, cached);
            } else {
                thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                meta.setText(R.string.merge_preview_loading);
                loadPreviewAsync(uri, key);
            }
        }

        countView.setText(getString(R.string.merge_order_count, orderedUris.size()));
        boolean valid = orderedUris.size() >= 2;
        confirmButton.setEnabled(valid);
        confirmButton.setAlpha(valid ? 1f : 0.45f);
    }

    private GradientDrawable rowBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Color.parseColor("#EAF1FF") : Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(selected ? 2 : 1),
            Color.parseColor(selected ? "#4A7FE3" : "#D8E0EC"));
        return background;
    }

    private void loadPreviewAsync(Uri uri, String key) {
        if (!previewLoading.add(key)) return;
        int maxWidth = dp(82);
        int maxHeight = dp(112);
        previewExecutor.execute(() -> {
            PreviewInfo info = renderFirstPage(uri, maxWidth, maxHeight);
            if (info != null) previewCache.put(key, info);
            previewLoading.remove(key);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                ImageView image = currentPreviewViews.get(key);
                TextView meta = currentMetaViews.get(key);
                if (image == null || meta == null) return;
                if (info == null) {
                    image.setImageResource(android.R.drawable.ic_menu_report_image);
                    meta.setText(R.string.merge_preview_unavailable);
                } else {
                    applyPreview(image, meta, info);
                }
            });
        });
    }

    private void applyPreview(ImageView image, TextView meta, PreviewInfo info) {
        image.setImageBitmap(info.bitmap);
        meta.setText(getString(R.string.merge_preview_pages, info.pageCount));
    }

    private PreviewInfo renderFirstPage(Uri uri, int maxWidth, int maxHeight) {
        try (ParcelFileDescriptor descriptor =
                 getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) return null;
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int pageCount = renderer.getPageCount();
                if (pageCount <= 0) return null;
                try (PdfRenderer.Page page = renderer.openPage(0)) {
                    float scale = Math.min(
                        maxWidth / (float) Math.max(1, page.getWidth()),
                        maxHeight / (float) Math.max(1, page.getHeight()));
                    scale = Math.max(0.1f, scale);
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return new PreviewInfo(bitmap, pageCount);
                }
            }
        } catch (IOException | SecurityException | IllegalStateException error) {
            return null;
        }
    }

    private void showLargePreview(Uri uri, String name) {
        FrameLayout container = new FrameLayout(this);
        container.setPadding(dp(12), dp(12), dp(12), dp(12));

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.WHITE);
        container.addView(image, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(480)));

        ProgressBar loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(46), dp(46));
        loadingParams.gravity = Gravity.CENTER;
        container.addView(loading, loadingParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.merge_preview_title, name))
            .setView(container)
            .setPositiveButton(R.string.confirm, null)
            .create();
        dialog.show();

        previewExecutor.execute(() -> {
            PreviewInfo large = renderFirstPage(uri, dp(720), dp(960));
            runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                loading.setVisibility(View.GONE);
                if (large == null) {
                    Toast.makeText(this, R.string.merge_preview_unavailable,
                        Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
                image.setImageBitmap(large.bitmap);
            });
        });
    }

    private void moveSelected(int direction) {
        if (selectedIndex < 0 || selectedIndex >= orderedUris.size()) return;
        int target = selectedIndex + direction;
        if (target < 0 || target >= orderedUris.size()) return;

        Uri uri = orderedUris.remove(selectedIndex);
        orderedUris.add(target, uri);
        String name = displayNames.remove(selectedIndex);
        displayNames.add(target, name);
        refreshList(target);
        scrollSelectionIntoView(target);
    }

    private void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= orderedUris.size()) return;
        orderedUris.remove(selectedIndex);
        displayNames.remove(selectedIndex);
        int next = orderedUris.isEmpty() ? -1 : Math.min(selectedIndex, orderedUris.size() - 1);
        refreshList(next);
        if (next >= 0) scrollSelectionIntoView(next);
    }

    private void scrollSelectionIntoView(int index) {
        listScroll.post(() -> {
            if (index < 0 || index >= listContainer.getChildCount()) return;
            View child = listContainer.getChildAt(index);
            listScroll.smoothScrollTo(0, Math.max(0, child.getTop() - dp(8)));
        });
    }

    private void returnOrderedFiles() {
        if (orderedUris.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putParcelableArrayListExtra(EXTRA_SELECTED_URIS, new ArrayList<>(orderedUris));
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (RuntimeException ignored) { }
        }
        if (result == null || result.trim().isEmpty()) result = uri.getLastPathSegment();
        return result == null || result.trim().isEmpty() ? "PDF" : result;
    }

    private MaterialButton button(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
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
        previewExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class PreviewInfo {
        final Bitmap bitmap;
        final int pageCount;

        PreviewInfo(Bitmap bitmap, int pageCount) {
            this.bitmap = bitmap;
            this.pageCount = pageCount;
        }
    }
}
