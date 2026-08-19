package com.signpdf.app.util;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** PDFBox-backed tools that are exposed only to SignPDF Pro users. */
public final class PdfProTools {

    private static boolean initialized;

    private PdfProTools() { }

    private static synchronized void ensureInitialized(Context context) {
        if (initialized) return;
        PDFBoxResourceLoader.init(context.getApplicationContext());
        initialized = true;
    }

    public static void merge(Context context, List<File> sources, File output) throws IOException {
        ensureInitialized(context);
        if (sources == null || sources.size() < 2) {
            throw new IOException("At least two PDF files are required.");
        }

        try (PDDocument merged = new PDDocument()) {
            for (File source : sources) {
                try (PDDocument document = PDDocument.load(source)) {
                    if (document.isEncrypted()) {
                        throw new IOException("Password-protected PDFs cannot be merged here.");
                    }
                    for (PDPage page : document.getPages()) {
                        merged.importPage(page);
                    }
                }
            }
            if (merged.getNumberOfPages() == 0) {
                throw new IOException("The selected PDFs contain no pages.");
            }
            merged.save(output);
        }
    }

    public static int getPageCount(Context context, File source) throws IOException {
        ensureInitialized(context);
        try (PDDocument document = PDDocument.load(source)) {
            return document.getNumberOfPages();
        }
    }

    public static void rotatePage(
        Context context,
        File source,
        int pageIndex,
        File output
    ) throws IOException {
        ensureInitialized(context);
        try (PDDocument document = PDDocument.load(source)) {
            validatePage(document, pageIndex);
            PDPage page = document.getPage(pageIndex);
            int rotation = page.getRotation();
            page.setRotation((rotation + 90) % 360);
            document.save(output);
        }
    }

    public static void deletePage(
        Context context,
        File source,
        int pageIndex,
        File output
    ) throws IOException {
        ensureInitialized(context);
        try (PDDocument document = PDDocument.load(source)) {
            validatePage(document, pageIndex);
            if (document.getNumberOfPages() <= 1) {
                throw new IOException("The last page cannot be deleted.");
            }
            document.removePage(pageIndex);
            document.save(output);
        }
    }

    public static void movePage(
        Context context,
        File source,
        int pageIndex,
        int direction,
        File output
    ) throws IOException {
        ensureInitialized(context);
        try (PDDocument document = PDDocument.load(source);
             PDDocument reordered = new PDDocument()) {
            validatePage(document, pageIndex);
            int targetIndex = pageIndex + direction;
            int count = document.getNumberOfPages();
            if (targetIndex < 0 || targetIndex >= count) {
                throw new IOException("The page cannot be moved further.");
            }

            ArrayList<Integer> order = new ArrayList<>();
            for (int i = 0; i < count; i++) order.add(i);
            int moving = order.get(pageIndex);
            order.set(pageIndex, order.get(targetIndex));
            order.set(targetIndex, moving);

            for (int index : order) {
                reordered.importPage(document.getPage(index));
            }
            reordered.save(output);
        }
    }

    public static void extractPage(
        Context context,
        File source,
        int pageIndex,
        File output
    ) throws IOException {
        ensureInitialized(context);
        try (PDDocument document = PDDocument.load(source);
             PDDocument extracted = new PDDocument()) {
            validatePage(document, pageIndex);
            extracted.importPage(document.getPage(pageIndex));
            extracted.save(output);
        }
    }

    public static List<File> splitAll(
        Context context,
        File source,
        File outputDir
    ) throws IOException {
        ensureInitialized(context);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Could not create the split output directory.");
        }

        List<File> outputs = new ArrayList<>();
        try (PDDocument document = PDDocument.load(source)) {
            int count = document.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                File output = new File(outputDir, "page_" + (i + 1) + ".pdf");
                try (PDDocument part = new PDDocument()) {
                    part.importPage(document.getPage(i));
                    part.save(output);
                }
                outputs.add(output);
            }
        }
        return outputs;
    }

    private static void validatePage(PDDocument document, int pageIndex) throws IOException {
        if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            throw new IOException("Invalid page index.");
        }
    }
}
