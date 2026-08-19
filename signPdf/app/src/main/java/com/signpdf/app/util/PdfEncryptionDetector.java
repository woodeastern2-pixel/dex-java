package com.signpdf.app.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight encrypted-PDF detector that never invokes PdfRenderer or PDFBox.
 *
 * PDF incremental updates can leave the active /Encrypt entry far away from the
 * physical end of the file, so the whole file is scanned instead of only a tail window.
 * The scan runs off the UI thread in MainActivity.
 */
public final class PdfEncryptionDetector {

    private static final byte[] PDF_HEADER =
        "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ENCRYPT_TOKEN =
        "/Encrypt".getBytes(StandardCharsets.ISO_8859_1);
    private static final int BUFFER_SIZE = 64 * 1024;

    private PdfEncryptionDetector() {
    }

    public static boolean requiresPassword(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.isFile() || pdfFile.length() < PDF_HEADER.length) {
            throw new IOException("PDF 파일을 읽을 수 없습니다");
        }

        try (RandomAccessFile input = new RandomAccessFile(pdfFile, "r")) {
            verifyPdfHeader(input);
            input.seek(0L);

            byte[] buffer = new byte[BUFFER_SIZE];
            int matched = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
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
        }

        return false;
    }

    private static void verifyPdfHeader(RandomAccessFile input) throws IOException {
        byte[] header = new byte[PDF_HEADER.length];
        int read = input.read(header);
        if (read != PDF_HEADER.length) {
            throw new IOException("PDF 파일을 읽을 수 없습니다");
        }

        for (int i = 0; i < PDF_HEADER.length; i++) {
            if (header[i] != PDF_HEADER[i]) {
                throw new IOException("올바른 PDF 파일이 아닙니다");
            }
        }
    }
}
