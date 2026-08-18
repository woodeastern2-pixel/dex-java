package com.signpdf.app.drawing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 원본 PDF에 필기 데이터를 합성하여 새 PDF로 저장합니다.
 *
 * 원본 페이지와 필기 레이어를 분리해 렌더링하므로 지우개는
 * 원본 문서를 훼손하지 않고 사용자가 추가한 필기만 지웁니다.
 */
public class PdfAnnotationExporter {

    private static final float EXPORT_DPI = 150f;
    private static final float PDF_DPI = 72f;
    private static final float EXPORT_RENDER_SCALE = EXPORT_DPI / PDF_DPI;

    public void export(PdfRenderer renderer,
                       Map<Integer, List<StrokeData>> pageStrokes,
                       File outputFile) throws IOException {

        PdfDocument pdfDocument = new PdfDocument();
        int pageCount = renderer.getPageCount();

        try {
            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int pageWidthPts = page.getWidth();
                int pageHeightPts = page.getHeight();

                int bitmapWidth = Math.max(1, (int) (pageWidthPts * EXPORT_RENDER_SCALE));
                int bitmapHeight = Math.max(1, (int) (pageHeightPts * EXPORT_RENDER_SCALE));

                Bitmap pageBitmap = Bitmap.createBitmap(
                    bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                pageBitmap.eraseColor(Color.WHITE);

                try {
                    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                } finally {
                    page.close();
                }

                // 필기는 투명 레이어에 별도로 렌더링합니다.
                // ERASER의 CLEAR 모드는 이 레이어에만 적용되므로 원본 PDF가 지워지지 않습니다.
                Bitmap annotationBitmap = Bitmap.createBitmap(
                    bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                annotationBitmap.eraseColor(Color.TRANSPARENT);
                Canvas annotationCanvas = new Canvas(annotationBitmap);

                List<StrokeData> strokes = pageStrokes.get(i);
                if (strokes != null) {
                    for (StrokeData stroke : strokes) {
                        if (!stroke.isEmpty()) {
                            drawStrokeOnBitmap(annotationCanvas, stroke);
                        }
                    }
                }

                Canvas pageBitmapCanvas = new Canvas(pageBitmap);
                pageBitmapCanvas.drawBitmap(annotationBitmap, 0f, 0f, null);
                annotationBitmap.recycle();

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    pageWidthPts, pageHeightPts, i + 1).create();
                PdfDocument.Page pdfPage = pdfDocument.startPage(pageInfo);
                Canvas pageCanvas = pdfPage.getCanvas();

                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.setScale(
                    (float) pageWidthPts / bitmapWidth,
                    (float) pageHeightPts / bitmapHeight);
                pageCanvas.drawBitmap(pageBitmap, matrix, null);

                pdfDocument.finishPage(pdfPage);
                pageBitmap.recycle();
            }

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                pdfDocument.writeTo(fos);
            }
        } finally {
            pdfDocument.close();
        }
    }

    private void drawStrokeOnBitmap(Canvas canvas, StrokeData stroke) {
        List<float[]> points = stroke.getPoints();
        if (points.size() < 2) return;

        Paint paint = stroke.createExportPaint(EXPORT_RENDER_SCALE);
        Path path = buildPath(points, EXPORT_RENDER_SCALE);
        canvas.drawPath(path, paint);
        paint.setXfermode(null);
    }

    private Path buildPath(List<float[]> points, float scale) {
        Path path = new Path();
        float[] first = points.get(0);
        path.moveTo(first[0] * scale, first[1] * scale);

        for (int i = 1; i < points.size(); i++) {
            float[] point = points.get(i);
            path.lineTo(point[0] * scale, point[1] * scale);
        }
        return path;
    }
}
