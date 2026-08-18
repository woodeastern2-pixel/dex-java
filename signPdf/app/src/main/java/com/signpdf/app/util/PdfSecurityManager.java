package com.signpdf.app.util;

import android.content.Context;
import android.graphics.pdf.LoadParams;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.encryption.SignPdfPasswordExceptionFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Password-protected PDF support.
 *
 * Detection uses Android's PdfRenderer instead of PDFBox so merely selecting an
 * encrypted file cannot crash the app on devices with unsupported PDFBox crypto paths.
 * On Android 15/API 35+ the platform renderer also validates the password and writes an
 * unlocked private working copy. Older Android releases fall back to PDFBox only after
 * the user has explicitly supplied a password.
 */
public final class PdfSecurityManager {

    private PdfSecurityManager() {
    }

    public static void initialize(Context context) {
        // Required only for the API 24-34 fallback path.
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    /** Returns true when Android reports that the PDF needs a password. */
    public static boolean requiresPassword(File pdfFile) throws IOException {
        ParcelFileDescriptor fd = null;
        PdfRenderer renderer = null;
        try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
            // PdfRenderer owns the descriptor after successful construction.
            fd = null;
            return false;
        } catch (SecurityException passwordRequired) {
            return true;
        } catch (IllegalArgumentException e) {
            throw new IOException("PDF 파일을 읽을 수 없습니다", e);
        } finally {
            if (renderer != null) {
                renderer.close();
            } else if (fd != null) {
                fd.close();
            }
        }
    }

    /**
     * Validates the password and replaces only the app-private cached PDF with an
     * unlocked working copy. The original user document is never modified.
     */
    public static void unlockCachedCopy(File cachedPdf, String password)
        throws IOException, InvalidPasswordException {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            unlockWithPlatformRenderer(cachedPdf, password);
        } else {
            unlockWithPdfBox(cachedPdf, password);
        }
    }

    private static void unlockWithPlatformRenderer(File cachedPdf, String password)
        throws IOException, InvalidPasswordException {

        File unlockedTemp = File.createTempFile(
            "signpdf_unlocked_", ".pdf", cachedPdf.getParentFile());

        ParcelFileDescriptor inputFd = null;
        PdfRenderer renderer = null;
        boolean written = false;
        try {
            inputFd = ParcelFileDescriptor.open(cachedPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            LoadParams params = new LoadParams.Builder()
                .setPassword(password)
                .build();

            try {
                renderer = new PdfRenderer(inputFd, params);
                // Renderer now owns the input descriptor.
                inputFd = null;
            } catch (SecurityException incorrectPassword) {
                throw SignPdfPasswordExceptionFactory.create("Incorrect PDF password");
            }

            ParcelFileDescriptor outputFd = ParcelFileDescriptor.open(
                unlockedTemp,
                ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE
                    | ParcelFileDescriptor.MODE_READ_WRITE);
            // write() closes outputFd. true removes password protection from the working copy.
            renderer.write(outputFd, true);
            written = true;
        } catch (IllegalArgumentException e) {
            throw new IOException("암호화된 PDF를 열 수 없습니다", e);
        } finally {
            if (renderer != null) {
                renderer.close();
            } else if (inputFd != null) {
                inputFd.close();
            }
            if (!written) {
                //noinspection ResultOfMethodCallIgnored
                unlockedTemp.delete();
            }
        }

        try {
            replaceFile(unlockedTemp, cachedPdf);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            unlockedTemp.delete();
        }
    }

    private static void unlockWithPdfBox(File cachedPdf, String password)
        throws IOException, InvalidPasswordException {

        File unlockedTemp = File.createTempFile(
            "signpdf_unlocked_", ".pdf", cachedPdf.getParentFile());
        boolean unlockedSaved = false;

        try (PDDocument document = PDDocument.load(cachedPdf, password)) {
            document.setAllSecurityToBeRemoved(true);
            document.save(unlockedTemp);
            unlockedSaved = true;
        } catch (InvalidPasswordException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            // Some vendor/JCA combinations can fail inside PDFBox crypto. Never let an
            // uncaught library failure terminate the Android process.
            throw new IOException("이 기기에서 해당 PDF 암호 방식을 처리할 수 없습니다", e);
        } finally {
            if (!unlockedSaved) {
                //noinspection ResultOfMethodCallIgnored
                unlockedTemp.delete();
            }
        }

        try {
            replaceFile(unlockedTemp, cachedPdf);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            unlockedTemp.delete();
        }
    }

    private static void replaceFile(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
    }
}
