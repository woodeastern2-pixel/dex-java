package com.signpdf.app.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PdfEncryptionDetectorTest {

    @Test
    public void detectsEncryptEntryAnywhereInLargeFile() throws Exception {
        File file = File.createTempFile("signpdf_encrypted_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("%PDF-1.7\ntrailer << /Encrypt 5 0 R >>\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
                byte[] filler = new byte[3 * 1024 * 1024];
                output.write(filler);
                output.write("\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1));
            }

            assertTrue(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void detectsEncryptTokenAcrossBufferBoundary() throws Exception {
        File file = File.createTempFile("signpdf_boundary_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1));

                int bytesWritten = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1).length;
                int target = (64 * 1024) - 3;
                byte[] filler = new byte[target - bytesWritten];
                for (int i = 0; i < filler.length; i++) filler[i] = 'A';
                output.write(filler);
                output.write("/Encrypt 9 0 R\n%%EOF\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            }

            assertTrue(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void detectsHexEscapedEncryptName() throws Exception {
        File file = File.createTempFile("signpdf_escaped_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(("%PDF-1.7\n"
                    + "trailer << /En#63rypt 9 0 R >>\n"
                    + "%%EOF\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            }

            assertTrue(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void detectsEscapedEncryptNameAcrossBufferBoundary() throws Exception {
        File file = File.createTempFile("signpdf_escaped_boundary_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1));
                int bytesWritten = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1).length;
                int target = (64 * 1024) - 4;
                byte[] filler = new byte[target - bytesWritten];
                for (int i = 0; i < filler.length; i++) filler[i] = 'A';
                output.write(filler);
                output.write("/En#63rypt 9 0 R\n%%EOF\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            }

            assertTrue(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void encryptMetadataNameAloneDoesNotRequirePassword() throws Exception {
        File file = File.createTempFile("signpdf_encrypt_metadata_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(("%PDF-1.7\n"
                    + "1 0 obj << /EncryptMetadata false >> endobj\n"
                    + "trailer << /Root 1 0 R >>\n%%EOF\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            }

            assertFalse(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void ordinaryPdfDoesNotRequirePassword() throws Exception {
        File file = File.createTempFile("signpdf_plain_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(("%PDF-1.4\n"
                    + "1 0 obj << /Type /Catalog >> endobj\n"
                    + "trailer << /Root 1 0 R >>\n%%EOF\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            }

            assertFalse(PdfEncryptionDetector.requiresPassword(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void rejectsNonPdfInput() throws Exception {
        File file = File.createTempFile("signpdf_invalid_", ".pdf");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write("not-a-pdf /Encrypt".getBytes(StandardCharsets.ISO_8859_1));
            }

            try {
                PdfEncryptionDetector.requiresPassword(file);
                fail("Expected IOException");
            } catch (IOException expected) {
                // expected
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
