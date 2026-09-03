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
 * The only SignPDF class that touches PDFBox for encrypted PDFs.
 *
 * This class is intentionally not referenced until the user has already seen the
 * password prompt and pressed Open. That keeps PDFBox class loading/parsing completely
 * out of the pre-dialog path on devices where a malformed/unsupported encrypted PDF can
 * trigger vendor/library failures.
 */
final class PdfBoxUnlocker {

    private PdfBoxUnlocker() {
    }

    static void unlock(Context context, File cachedPdf, String password)
        throws IOException, PdfSecurityManager.WrongPasswordException {

        if (context == null) {
            throw new IOException("PDF 보안 모듈을 초기화할 수 없습니다");
        }
        if (cachedPdf == null || !cachedPdf.isFile()) {
            throw new IOException("PDF 파일을 읽을 수 없습니다");
        }

        PDFBoxResourceLoader.init(context.getApplicationContext());

        File parent = cachedPdf.getParentFile();
        if (parent == null) {
            throw new IOException("PDF 임시 저장 위치를 사용할 수 없습니다");
        }

        File unlockedTemp = File.createTempFile("signpdf_unlocked_", ".pdf", parent);
        boolean saved = false;

        try (PDDocument document = PDDocument.load(
            cachedPdf,
            password == null ? "" : password,
            MemoryUsageSetting.setupTempFileOnly())) {

            document.setAllSecurityToBeRemoved(true);
            document.save(unlockedTemp);
            saved = true;
        } catch (InvalidPasswordException e) {
            throw new PdfSecurityManager.WrongPasswordException(e);
        } catch (VirtualMachineError | ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("이 PDF의 암호화 방식을 처리할 수 없습니다", e);
        } finally {
            if (!saved) {
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
