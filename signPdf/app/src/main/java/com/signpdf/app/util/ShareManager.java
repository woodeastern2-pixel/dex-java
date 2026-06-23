package com.signpdf.app.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * 저장된 PDF 파일을 다른 앱과 공유합니다.
 * FileProvider를 통해 안전한 Uri를 생성합니다.
 */
public class ShareManager {

    private final Context context;

    public ShareManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Uri로 PDF 공유 (MediaStore Uri 또는 FileProvider Uri)
     */
    public Intent createShareIntent(Uri pdfUri) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(shareIntent, "PDF 공유");
    }

    /**
     * File 객체로 PDF 공유 (FileProvider를 통한 안전한 공유)
     */
    public Intent createShareIntentFromFile(File pdfFile) {
        Uri fileUri = FileProvider.getUriForFile(
            context,
            context.getPackageName() + ".fileprovider",
            pdfFile
        );
        return createShareIntent(fileUri);
    }

    /**
     * 공유 후 파일 열기 (뷰어 앱에서 바로 열기)
     */
    public Intent createViewIntent(Uri pdfUri) {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(pdfUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return viewIntent;
    }
}
