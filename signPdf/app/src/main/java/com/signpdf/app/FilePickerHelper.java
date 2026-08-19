package com.signpdf.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** Android SAF based file picker with single and multi-selection entry points. */
public class FilePickerHelper {

    private static final String[] SUPPORTED_MIME_TYPES = {
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/bmp",
        "image/webp"
    };

    private static final String[] IMAGE_MIME_TYPES = {
        "image/jpeg",
        "image/png",
        "image/bmp",
        "image/webp"
    };

    public interface OnFilePickedListener {
        void onFilePicked(Uri uri, String mimeType);
        void onCancelled();
    }

    public interface OnFilesPickedListener {
        void onFilesPicked(List<Uri> uris);
        void onCancelled();
    }

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<Intent> launcher;
    private final ActivityResultLauncher<Intent> multiLauncher;
    private final ActivityResultLauncher<Intent> mergePickerLauncher;
    private OnFilePickedListener listener;
    private OnFilesPickedListener multiListener;
    private OnFilesPickedListener mergeListener;

    public FilePickerHelper(AppCompatActivity activity) {
        this.activity = activity;

        launcher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (listener == null) return;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        persistReadPermission(uri);
                        String mimeType = activity.getContentResolver().getType(uri);
                        listener.onFilePicked(uri, mimeType != null ? mimeType : "");
                    } else {
                        listener.onCancelled();
                    }
                } else {
                    listener.onCancelled();
                }
            }
        );

        multiLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (multiListener == null) return;
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    multiListener.onCancelled();
                    return;
                }

                Intent data = result.getData();
                ArrayList<Uri> uris = new ArrayList<>();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) {
                            persistReadPermission(uri);
                            uris.add(uri);
                        }
                    }
                } else if (data.getData() != null) {
                    Uri uri = data.getData();
                    persistReadPermission(uri);
                    uris.add(uri);
                }

                if (uris.isEmpty()) multiListener.onCancelled();
                else multiListener.onFilesPicked(uris);
            }
        );

        mergePickerLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mergeListener == null) return;
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    mergeListener.onCancelled();
                    return;
                }
                ArrayList<Uri> uris = result.getData()
                    .getParcelableArrayListExtra(MergePdfPickerActivity.EXTRA_SELECTED_URIS);
                if (uris == null || uris.isEmpty()) mergeListener.onCancelled();
                else mergeListener.onFilesPicked(uris);
            }
        );
    }

    public void openFilePicker(OnFilePickedListener listener) {
        launch("*/*", SUPPORTED_MIME_TYPES, listener);
    }

    public void openPdfPicker(OnFilePickedListener listener) {
        launch("application/pdf", null, listener);
    }

    public void openImagePicker(OnFilePickedListener listener) {
        launch("image/*", IMAGE_MIME_TYPES, listener);
    }

    /**
     * PDF merge uses SignPDF's own folder-backed picker so every PDF toggles
     * selected/unselected with one tap regardless of OEM file-picker behavior.
     */
    public void openMultiplePdfPicker(OnFilesPickedListener listener) {
        this.mergeListener = listener;
        mergePickerLauncher.launch(new Intent(activity, MergePdfPickerActivity.class));
    }

    public void openMultipleImagePicker(OnFilesPickedListener listener) {
        launchMultiple("image/*", IMAGE_MIME_TYPES, listener);
    }

    private void launch(String type, String[] mimeTypes, OnFilePickedListener listener) {
        this.listener = listener;
        Intent intent = baseIntent(type, mimeTypes);
        launcher.launch(intent);
    }

    private void launchMultiple(String type, String[] mimeTypes, OnFilesPickedListener listener) {
        this.multiListener = listener;
        Intent intent = baseIntent(type, mimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        multiLauncher.launch(intent);
    }

    private Intent baseIntent(String type, String[] mimeTypes) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType(type);
        if (mimeTypes != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }

    private void persistReadPermission(Uri uri) {
        try {
            activity.getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some providers do not support persistent grants.
        }
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }
}
