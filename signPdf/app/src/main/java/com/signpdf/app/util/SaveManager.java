package com.signpdf.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves generated PDFs as new files without overwriting the original document. */
public class SaveManager {

    private final Context context;

    public SaveManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Saves an edited PDF using the regular signed_ filename convention. */
    public Uri save(File tempFile, String originalName) throws IOException {
        String baseName = removeExtension(originalName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        return saveGenerated(tempFile, "signed_" + baseName + "_" + timeStamp + ".pdf");
    }

    /**
     * Saves any generated PDF with a caller-provided filename.
     * Free-tier successful output operations consume one monthly action; Pro bypasses the quota.
     */
    public Uri saveGenerated(File tempFile, String fileName) throws IOException {
        if (!UsageQuotaManager.canUseAction()) {
            throw new IOException(UsageQuotaManager.getLimitReachedMessage());
        }
        if (tempFile == null || !tempFile.exists() || tempFile.length() <= 0L) {
            throw new IOException("Generated PDF is empty.");
        }

        String safeName = sanitizePdfFileName(fileName);
        Uri savedUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savedUri = saveViaMediaStore(tempFile, safeName);
        } else {
            savedUri = saveToExternalFiles(tempFile, safeName);
        }

        UsageQuotaManager.recordSuccessfulAction();
        return savedUri;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri saveViaMediaStore(File tempFile, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/SignPDF");

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = context.getContentResolver().insert(collection, values);
        if (fileUri == null) {
            throw new IOException("MediaStore file creation failed.");
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(fileUri);
             FileInputStream fis = new FileInputStream(tempFile)) {
            if (os == null) throw new IOException("Could not open output stream.");
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

    private Uri saveToExternalFiles(File tempFile, String fileName) throws IOException {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create output directory.");
        }

        File outputFile = uniqueFile(dir, fileName);
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

    public String getDisplayPath(Uri savedUri) {
        if (savedUri == null) return "";
        if ("file".equals(savedUri.getScheme())) return savedUri.getPath();
        return "Download/SignPDF";
    }

    public String timestampedName(String prefix, String originalName) {
        String baseName = removeExtension(originalName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        return prefix + baseName + "_" + timeStamp + ".pdf";
    }

    private String sanitizePdfFileName(String fileName) {
        String name = fileName == null ? "SignPDF.pdf" : fileName.trim();
        if (name.isEmpty()) name = "SignPDF.pdf";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        return name;
    }

    private File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;

        String base = removeExtension(fileName);
        int index = 2;
        do {
            candidate = new File(dir, base + "_" + index + ".pdf");
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private String removeExtension(String filename) {
        if (filename == null) return "document";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) return filename.substring(0, lastDot);
        return filename;
    }
}
