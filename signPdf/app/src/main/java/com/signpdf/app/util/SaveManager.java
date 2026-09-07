package com.signpdf.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 수정된 PDF를 기기에 저장합니다.
 * 원본 파일은 절대 덮어쓰지 않으며, 새 파일로 저장합니다.
 *
 * 파일명 형식: signed_원본파일명_yyyyMMdd_HHmmss.pdf
 * 저장 위치: 앱 외부 Documents 디렉터리 (권한 불필요)
 */
public class SaveManager {

    private final Context context;

    public SaveManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 임시 파일을 최종 저장 위치로 복사합니다.
     *
     * @param tempFile     필기가 반영된 임시 PDF 파일
     * @param originalName 원본 파일명 (확장자 포함 가능)
     * @return 저장된 파일의 Uri
     * @throws IOException 저장 실패 시
     */
    public Uri save(File tempFile, String originalName) throws IOException {
        String baseName = removeExtension(originalName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        String fileName = "signed_" + baseName + "_" + timeStamp + ".pdf";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveViaMediaStore(tempFile, fileName);
        } else {
            return saveToExternalFiles(tempFile, fileName);
        }
    }

    /** Android 10+ : MediaStore.Downloads 사용 */
    private Uri saveViaMediaStore(File tempFile, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/SignPDF");

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = context.getContentResolver().insert(collection, values);
        if (fileUri == null) {
            throw new IOException("MediaStore 파일 생성 실패");
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(fileUri);
             FileInputStream fis = new FileInputStream(tempFile)) {
            if (os == null) throw new IOException("출력 스트림 열기 실패");
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                os.write(buf, 0, read);
            }
        } catch (IOException e) {
            context.getContentResolver().delete(fileUri, null, null);
            throw e;
        }
        return fileUri;
    }

    /** Android 9 이하 : 앱 외부 파일 디렉터리 사용 */
    private Uri saveToExternalFiles(File tempFile, String fileName) throws IOException {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File outputFile = new File(dir, fileName);
        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
        }

        return Uri.fromFile(outputFile);
    }

    /**
     * 저장 경로를 사람이 읽기 쉬운 문자열로 반환합니다.
     */
    public String getDisplayPath(Uri savedUri) {
        if (savedUri == null) return "";
        if ("file".equals(savedUri.getScheme())) {
            return savedUri.getPath();
        }
        return "다운로드/SignPDF 폴더";
    }

    private String removeExtension(String filename) {
        if (filename == null) return "document";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}
