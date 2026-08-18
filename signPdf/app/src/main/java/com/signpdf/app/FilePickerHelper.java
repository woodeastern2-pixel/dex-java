package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Android SAF(Storage Access Framework)를 이용한 파일 선택 도우미.
 * PDF, 이미지, Word 파일을 선택할 수 있습니다.
 */
public class FilePickerHelper {

    /** 지원하는 MIME 타입 목록 */
    private static final String[] SUPPORTED_MIME_TYPES = {
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/bmp",
        "image/webp",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
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
                        // 영구적 읽기 권한 유지
                        try {
                            activity.getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (SecurityException ignored) {
                            // 일부 제공자는 영구 권한 미지원
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

    /**
     * 파일 선택 다이얼로그를 엽니다.
     */
    public void openFilePicker(OnFilePickedListener listener) {
        this.listener = listener;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES);
        intent.setType("*/*");
        launcher.launch(intent);
    }

    /**
     * 파일 Uri가 이미지인지 확인합니다.
     */
    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * 파일 Uri가 PDF인지 확인합니다.
     */
    public static boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }

    /**
     * 파일 Uri가 Word 문서인지 확인합니다.
     */
    public static boolean isWord(String mimeType) {
        return "application/msword".equals(mimeType)
            || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
               .equals(mimeType);
    }
}
