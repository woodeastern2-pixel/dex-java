package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Android SAF(Storage Access Framework)를 이용한 파일 선택 도우미.
 * PDF와 이미지 파일을 선택할 수 있습니다.
 */
public class FilePickerHelper {

    private static final String[] SUPPORTED_MIME_TYPES = {
        "application/pdf",
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
                            // 일부 파일 제공자는 영구 읽기 권한을 지원하지 않습니다.
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
        this.listener = listener;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES);
        intent.setType("*/*");
        launcher.launch(intent);
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }
}
