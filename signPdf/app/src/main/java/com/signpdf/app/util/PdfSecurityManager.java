package com.signpdf.app.util;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/**
 * Password-protected PDF coordinator.
 *
 * Safety rule: selecting a PDF and deciding whether to show the password UI must not
 * load PDFBox or Android PdfRenderer. Only the lightweight byte-level detector runs
 * before the password prompt. PDFBox is loaded lazily after the user enters a password.
 */
public final class PdfSecurityManager {

    private static volatile Context sAppContext;

    private PdfSecurityManager() {
    }

    /** Stores only the application context. PDFBox is NOT initialized here. */
    public static void initialize(Context context) {
        if (context != null) {
            sAppContext = context.getApplicationContext();
        }
    }

    /** Lightweight syntax scan only. No parser/renderer is invoked. */
    public static boolean requiresPassword(File pdfFile) throws IOException {
        try {
            return PdfEncryptionDetector.requiresPassword(pdfFile);
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("PDF 보안 정보를 확인할 수 없습니다", e);
        }
    }

    /** Called only after the password UI is already visible and the user presses Open. */
    public static void unlockCachedCopy(File cachedPdf, String password)
        throws IOException, WrongPasswordException {

        Context context = sAppContext;
        if (context == null) {
            throw new IOException("PDF 보안 모듈을 초기화할 수 없습니다");
        }
        PdfBoxUnlocker.unlock(context, cachedPdf, password);
    }

    public static final class WrongPasswordException extends Exception {
        WrongPasswordException(Throwable cause) {
            super(cause);
        }
    }
}
