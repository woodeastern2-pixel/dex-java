package com.signpdf.app.util;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Password-protected PDF support.
 *
 * Important: encrypted-PDF detection deliberately does NOT instantiate PdfRenderer
 * or PDFBox. Some vendor PDF stacks can terminate the app process while probing an
 * encrypted/unsupported document. Instead we inspect only the PDF trailer bytes for
 * the standard /Encrypt entry, then ask for the password before any renderer sees it.
 *
 * The user's original file is never modified. Successful decryption replaces only an
 * app-private cached working copy.
 */
public final class PdfSecurityManager {

    private static final byte[] ENCRYPT_TOKEN =
        "/Encrypt".getBytes(StandardCharsets.ISO_8859_1);
    private static final long TRAILER_SCAN_BYTES = 2L * 1024L * 1024L;

    private PdfSecurityManager() {
    }

    public static void initialize(Context context) {
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    /**
     * Detects password protection without parsing or rendering the PDF.
     * PDF encryption is referenced by the /Encrypt entry in the trailer/xref dictionary,
     * which is intentionally left readable so a PDF reader can discover the security handler.
     */
    public static boolean requiresPassword(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.isFile() || pdfFile.length() < 5) {
            throw new IOException("PDF 파일을 읽을 수 없습니다");
        }

        long fileLength = pdfFile.length();
        long scanLength = Math.min(fileLength, TRAILER_SCAN_BYTES);
        long startOffset = fileLength - scanLength;

        try (RandomAccessFile input = new RandomAccessFile(pdfFile, "r")) {
            input.seek(startOffset);
            byte[] buffer = new byte[32 * 1024];
            int matched = 0;
            long remaining = scanLength;

            while (remaining > 0) {
                int requested = (int) Math.min(buffer.length, remaining);
                int read = input.read(buffer, 0, requested);
                if (read < 0) break;
                remaining -= read;

                for (int i = 0; i < read; i++) {
                    byte value = buffer[i];
                    if (value == ENCRYPT_TOKEN[matched]) {
                        matched++;
                        if (matched == ENCRYPT_TOKEN.length) {
                            return true;
                        }
                    } else {
                        matched = value == ENCRYPT_TOKEN[0] ? 1 : 0;
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("PDF 보안 정보를 확인할 수 없습니다", e);
        }

        return false;
    }

    /**
     * Validates the password with PDFBox and replaces only the private cached copy with
     * an unlocked PDF. No Android PdfRenderer is allowed to touch the protected source.
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
        } catch (InvalidPasswordException e) {
            throw e;
        } catch (VirtualMachineError | ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            // PDFBox/BouncyCastle can surface vendor-specific runtime/linkage failures.
            // Convert every recoverable library failure into a normal UI error instead of
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
