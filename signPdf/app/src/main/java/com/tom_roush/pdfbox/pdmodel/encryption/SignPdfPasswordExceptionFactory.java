package com.tom_roush.pdfbox.pdmodel.encryption;

/**
 * Small bridge for SignPDF's Android 15+ native PdfRenderer path.
 * PDFBox exposes InvalidPasswordException publicly but keeps its constructor
 * package-private, so this same-package helper lets the app translate Android's
 * SecurityException into the existing password-error type without invoking PDFBox.
 */
public final class SignPdfPasswordExceptionFactory {
    private SignPdfPasswordExceptionFactory() {
    }

    public static InvalidPasswordException create(String message) {
        return new InvalidPasswordException(message);
    }
}
