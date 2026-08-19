package com.signpdf.app.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight encrypted-PDF detector that never invokes Android PdfRenderer.
 *
 * PDF names can escape bytes using #xx (for example /En#63rypt), and incremental
 * updates can place the active encryption entry far from the physical end of the file.
 * This scanner therefore walks the whole file and decodes PDF name escapes while
 * looking for the Encrypt name.
 */
public final class PdfEncryptionDetector {

    private static final byte[] PDF_HEADER =
        "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ENCRYPT_NAME =
        "Encrypt".getBytes(StandardCharsets.ISO_8859_1);
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
            return containsEncryptName(input);
        }
    }

    private static boolean containsEncryptName(RandomAccessFile input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];

        // -1: not reading a PDF name, -2: reading another name that cannot match.
        int nameIndex = -1;
        int hexState = 0; // 0=normal, 1=expect high nibble, 2=expect low nibble
        int hexHigh = 0;
        int read;

        while ((read = input.read(buffer)) != -1) {
            for (int i = 0; i < read; i++) {
                int value = buffer[i] & 0xFF;

                if (nameIndex == -1) {
                    if (value == '/') {
                        nameIndex = 0;
                        hexState = 0;
                    }
                    continue;
                }

                if (nameIndex == -2) {
                    if (isNameDelimiter(value)) {
                        nameIndex = value == '/' ? 0 : -1;
                        hexState = 0;
                    }
                    continue;
                }

                if (hexState == 1) {
                    int high = hexValue(value);
                    if (high < 0) {
                        nameIndex = -2;
                        hexState = 0;
                    } else {
                        hexHigh = high;
                        hexState = 2;
                    }
                    continue;
                }

                if (hexState == 2) {
                    int low = hexValue(value);
                    if (low < 0) {
                        nameIndex = -2;
                    } else {
                        nameIndex = consumeCandidateByte(nameIndex, (hexHigh << 4) | low);
                    }
                    hexState = 0;
                    continue;
                }

                if (value == '#') {
                    hexState = 1;
                    continue;
                }

                if (isNameDelimiter(value)) {
                    if (nameIndex == ENCRYPT_NAME.length) {
                        return true;
                    }
                    nameIndex = value == '/' ? 0 : -1;
                    hexState = 0;
                    continue;
                }

                nameIndex = consumeCandidateByte(nameIndex, value);
            }
        }

        return hexState == 0 && nameIndex == ENCRYPT_NAME.length;
    }

    private static int consumeCandidateByte(int nameIndex, int value) {
        if (nameIndex < 0 || nameIndex >= ENCRYPT_NAME.length) {
            return -2;
        }
        return value == (ENCRYPT_NAME[nameIndex] & 0xFF) ? nameIndex + 1 : -2;
    }

    private static boolean isNameDelimiter(int value) {
        return value <= 0x20
            || value == 0x00
            || value == '('
            || value == ')'
            || value == '<'
            || value == '>'
            || value == '['
            || value == ']'
            || value == '{'
            || value == '}'
            || value == '/'
            || value == '%';
    }

    private static int hexValue(int value) {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'A' && value <= 'F') return value - 'A' + 10;
        if (value >= 'a' && value <= 'f') return value - 'a' + 10;
        return -1;
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
