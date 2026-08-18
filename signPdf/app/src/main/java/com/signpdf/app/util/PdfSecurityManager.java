package com.signpdf.app.util;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Handles password-protected PDFs entirely inside the app's private cache.
 * Passwords are never persisted. When a password is accepted, the cached copy
 * is replaced with an unlocked working copy while the user's original file is untouched.
 */
public final class PdfSecurityManager {

    private PdfSecurityManager() {
    }

    public static void initialize(Context context) {
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    /**
     * Returns true only when the PDF specifically requires a password.
     * Other parse errors are surfaced to the caller instead of being mistaken for encryption.
     */
    public static boolean requiresPassword(File pdfFile) throws IOException {
        try (PDDocument ignored = PDDocument.load(pdfFile)) {
            return false;
        } catch (InvalidPasswordException e) {
            return true;
        }
    }

    /**
     * Validates the supplied password and replaces only the app-private cached copy
     * with an unlocked PDF that Android PdfRenderer can open on all supported API levels.
     */
    public static void unlockCachedCopy(File cachedPdf, String password)
        throws IOException, InvalidPasswordException {

        File unlockedTemp = File.createTempFile(
            "signpdf_unlocked_", ".pdf", cachedPdf.getParentFile());
        boolean unlockedSaved = false;

        try (PDDocument document = PDDocument.load(cachedPdf, password)) {
            document.setAllSecurityToBeRemoved(true);
            document.save(unlockedTemp);
            unlockedSaved = true;
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
