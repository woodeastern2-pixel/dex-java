package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/** Android SAF based file picker with intent-specific entry points. */
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

    private final ActivityResultLauncher<Intent> launcher;
    private OnFilePickedListener listener;

    public FilePickerHelper(AppCompatActivity activity) {
        launcher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (listener == null) return;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            activity.getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException ignored) {
                            // Some providers do not support persistent grants.
                        }
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

    private void launch(String type, String[] mimeTypes, OnFilePickedListener listener) {
        this.listener = listener;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType(type);
        if (mimeTypes != null) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        launcher.launch(intent);
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }
}
