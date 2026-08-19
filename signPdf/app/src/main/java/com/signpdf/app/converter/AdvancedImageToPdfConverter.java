package com.signpdf.app.converter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Multi-image PDF conversion with page size, margin and quality controls for Pro. */
public final class AdvancedImageToPdfConverter {

    public enum PageSize {
        A4,
        LETTER,
        FIT_IMAGE
    }

    public static final class Options {
        public final PageSize pageSize;
        public final int marginPoints;
        public final int qualityPercent;

        public Options(PageSize pageSize, int marginPoints, int qualityPercent) {
            this.pageSize = pageSize == null ? PageSize.A4 : pageSize;
            this.marginPoints = Math.max(0, marginPoints);
            this.qualityPercent = Math.max(40, Math.min(100, qualityPercent));
        }
    }

    public void convert(List<File> images, File output, Options options)
        throws IOException, DocumentToPdfConverter.ConversionException {
        if (images == null || images.isEmpty()) {
            throw new DocumentToPdfConverter.ConversionException("No images selected.");
        }
        if (options == null) options = new Options(PageSize.A4, 18, 85);

        PdfDocument pdf = new PdfDocument();
        try {
            int pageNumber = 1;
            for (File image : images) {
                Bitmap bitmap = decodeScaled(image, options.qualityPercent);
                if (bitmap == null) {
                    throw new DocumentToPdfConverter.ConversionException(
                        "Could not read image: " + image.getName());
                }

                try {
                    int[] size = resolvePageSize(bitmap, options.pageSize);
                    int pageWidth = size[0];
                    int pageHeight = size[1];
                    int margin = Math.min(options.marginPoints,
                        Math.max(0, Math.min(pageWidth, pageHeight) / 4));

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        pageWidth, pageHeight, pageNumber++).create();
                    PdfDocument.Page page = pdf.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);

                    int availableWidth = Math.max(1, pageWidth - margin * 2);
                    int availableHeight = Math.max(1, pageHeight - margin * 2);
                    float scale = Math.min(
                        availableWidth / (float) bitmap.getWidth(),
                        availableHeight / (float) bitmap.getHeight());
                    int drawWidth = Math.max(1, Math.round(bitmap.getWidth() * scale));
                    int drawHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
                    int left = (pageWidth - drawWidth) / 2;
                    int top = (pageHeight - drawHeight) / 2;

                    Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    Rect dst = new Rect(left, top, left + drawWidth, top + drawHeight);
                    canvas.drawBitmap(bitmap, src, dst, null);
                    pdf.finishPage(page);
                } finally {
                    bitmap.recycle();
                }
            }

            try (FileOutputStream outputStream = new FileOutputStream(output)) {
                pdf.writeTo(outputStream);
            }
        } finally {
            pdf.close();
        }
    }

    private Bitmap decodeScaled(File image, int qualityPercent) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int maxDimension;
        if (qualityPercent >= 90) {
            maxDimension = 2800;
        } else if (qualityPercent >= 75) {
            maxDimension = 2200;
        } else if (qualityPercent >= 60) {
            maxDimension = 1700;
        } else {
            maxDimension = 1300;
        }

        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (largest / (sample * 2) >= maxDimension) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = Math.max(1, sample);
        Bitmap decoded = BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        if (decoded == null) return null;

        int decodedLargest = Math.max(decoded.getWidth(), decoded.getHeight());
        if (decodedLargest <= maxDimension) return decoded;

        float scale = maxDimension / (float) decodedLargest;
        int width = Math.max(1, Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, Math.round(decoded.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    private int[] resolvePageSize(Bitmap bitmap, PageSize pageSize) {
        switch (pageSize) {
            case LETTER:
                return new int[]{612, 792};
            case FIT_IMAGE:
                float ratio = bitmap.getWidth() / (float) bitmap.getHeight();
                if (ratio >= 1f) {
                    return new int[]{842, Math.max(300, Math.round(842 / ratio))};
                }
                return new int[]{Math.max(300, Math.round(842 * ratio)), 842};
            case A4:
            default:
                return new int[]{595, 842};
        }
    }
}
