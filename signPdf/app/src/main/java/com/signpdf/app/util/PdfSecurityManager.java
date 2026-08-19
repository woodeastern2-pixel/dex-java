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
 * Password-protected PDF support.
 *
 * Encrypted-PDF detection deliberately does NOT instantiate PdfRenderer or PDFBox.
 * Some Android/vendor PDF stacks can terminate the app process while probing a
 * protected or unsupported document. Detection is delegated to a lightweight byte
 * scanner, then PDFBox is used only after the app has asked the user for a password.
 *
 * The user's original file is never modified. Successful decryption replaces only an
 * app-private cached working copy.
 */
public final class PdfSecurityManager {

    private PdfSecurityManager() {
    }

    public static void initialize(Context context) {
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    public static boolean requiresPassword(File pdfFile) throws IOException {
        try {
            return PdfEncryptionDetector.requiresPassword(pdfFile);
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("PDF 보안 정보를 확인할 수 없습니다", e);
        }
    }

    /**
     * Validates the password with PDFBox and replaces only the private cached copy with
     * an unlocked PDF. Android PdfRenderer never sees the protected source.
     */
    public static void unlockCachedCopy(File cachedPdf, String password)
        throws IOException, InvalidPasswordException {

        if (cachedPdf == null || !cachedPdf.isFile()) {
            throw new IOException("PDF 파일을 읽을 수 없습니다");
        }

        File parent = cachedPdf.getParentFile();
        if (parent == null) {
            throw new IOException("PDF 임시 저장 위치를 사용할 수 없습니다");
        }

        File unlockedTemp = File.createTempFile(
            "signpdf_unlocked_", ".pdf", parent);
        boolean unlockedSaved = false;

        try (PDDocument document = PDDocument.load(cachedPdf, password == null ? "" : password)) {
            document.setAllSecurityToBeRemoved(true);
            document.save(unlockedTemp);
            unlockedSaved = true;
        } catch (InvalidPasswordException e) {
            throw e;
        } catch (VirtualMachineError | ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            // PDFBox/BouncyCastle can surface vendor-specific runtime/linkage failures.
            // Convert recoverable library failures into a normal UI error rather than
            // allowing an executor-thread exception to terminate the Android process.
            throw new IOException("이 PDF의 암호화 방식을 처리할 수 없습니다", e);
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
