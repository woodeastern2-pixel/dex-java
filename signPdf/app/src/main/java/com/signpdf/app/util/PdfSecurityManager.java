package com.signpdf.app.util;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Password-protected PDF support.
 *
 * The fast detector handles normal and escaped /Encrypt names without invoking the
 * Android renderer. If that detector does not find an encryption entry, PDFBox performs
 * a second Java-side probe so unusual PDFs cannot slip through to PdfRenderer and cause
 * an uncaught SecurityException.
 *
 * The user's original file is never modified. Successful decryption replaces only an
 * app-private cached working copy, and that output is reopened without a password before
 * it is allowed to reach the editor.
 */
public final class PdfSecurityManager {

    private PdfSecurityManager() {
    }

    public static void initialize(Context context) {
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    public static boolean requiresPassword(File pdfFile) throws IOException {
        try {
            if (PdfEncryptionDetector.requiresPassword(pdfFile)) {
                return true;
            }

            // A valid PDF name can encode characters as #xx, and unusual producers can
            // arrange security metadata in ways a byte scanner should not be trusted to
            // classify by itself. PDFBox is pure Java here and gives us a safe second gate.
            try (PDDocument document = PDDocument.load(
                pdfFile,
                "",
                MemoryUsageSetting.setupTempFileOnly())) {
                return document.isEncrypted();
            } catch (InvalidPasswordException e) {
                return true;
            }
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("PDF 보안 정보를 확인할 수 없습니다", e);
        }
    }

    /**
     * Validates the password with PDFBox and replaces only the private cached copy with
     * an unlocked PDF. The result is reopened without a password before Android
     * PdfRenderer is allowed to see it.
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

        try (PDDocument document = PDDocument.load(
            cachedPdf,
            password == null ? "" : password,
            MemoryUsageSetting.setupTempFileOnly())) {

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
            verifyUnlockedCopy(unlockedTemp);
            replaceFile(unlockedTemp, cachedPdf);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            unlockedTemp.delete();
        }
    }

    private static void verifyUnlockedCopy(File unlockedPdf) throws IOException {
        if (unlockedPdf == null || !unlockedPdf.isFile() || unlockedPdf.length() == 0L) {
            throw new IOException("복호화된 PDF를 만들지 못했습니다");
        }

        try (PDDocument verification = PDDocument.load(
            unlockedPdf,
            "",
            MemoryUsageSetting.setupTempFileOnly())) {
            if (verification.isEncrypted()) {
                throw new IOException("PDF 암호 보호가 완전히 제거되지 않았습니다");
            }
        } catch (InvalidPasswordException e) {
            throw new IOException("PDF 암호 보호가 완전히 제거되지 않았습니다", e);
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("복호화된 PDF를 검증할 수 없습니다", e);
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
