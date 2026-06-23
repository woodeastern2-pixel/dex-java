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
 * 방식:
 *  1. 원본 PDF 각 페이지를 고해상도 비트맵으로 렌더링
 *  2. 해당 페이지의 StrokeData를 비트맵 좌표계로 변환하여 Canvas에 그리기
 *  3. Android PdfDocument로 새 PDF 생성 (비트맵을 페이지에 배치)
 *  4. 파일로 저장
 */
public class PdfAnnotationExporter {

    /** 내보내기 시 렌더링 해상도 (DPI). 높을수록 화질 좋음. */
    private static final float EXPORT_DPI = 150f;
    /** PDF point 단위 기준 DPI */
    private static final float PDF_DPI = 72f;
    /** 비트맵 렌더링 배율 (bitmap pixels / pdf points) */
    private static final float EXPORT_RENDER_SCALE = EXPORT_DPI / PDF_DPI;

    /**
     * PDF에 필기를 합성하여 새 파일로 저장합니다.
     *
     * @param renderer   원본 PDF의 PdfRenderer
     * @param pageStrokes 페이지 인덱스 → 해당 페이지의 StrokeData 목록
     * @param outputFile 저장할 파일
     * @throws IOException 저장 실패 시
     */
    public void export(PdfRenderer renderer,
                       Map<Integer, List<StrokeData>> pageStrokes,
                       File outputFile) throws IOException {

        PdfDocument pdfDocument = new PdfDocument();
        int pageCount = renderer.getPageCount();

        for (int i = 0; i < pageCount; i++) {
            PdfRenderer.Page page = renderer.openPage(i);
            int pageWidthPts = page.getWidth();
            int pageHeightPts = page.getHeight();

            // 고해상도 비트맵으로 렌더링
            int bitmapWidth = (int) (pageWidthPts * EXPORT_RENDER_SCALE);
            int bitmapHeight = (int) (pageHeightPts * EXPORT_RENDER_SCALE);

            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            page.close();

            // 필기 스트로크를 비트맵에 그리기
            Canvas bitmapCanvas = new Canvas(bitmap);
            List<StrokeData> strokes = pageStrokes.get(i);
            if (strokes != null) {
                for (StrokeData stroke : strokes) {
                    if (!stroke.isEmpty()) {
                        drawStrokeOnBitmap(bitmapCanvas, stroke);
                    }
                }
            }

            // PdfDocument 페이지 생성 (PDF point 단위)
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                pageWidthPts, pageHeightPts, i + 1).create();
            PdfDocument.Page pdfPage = pdfDocument.startPage(pageInfo);
            Canvas pageCanvas = pdfPage.getCanvas();

            // 비트맵을 페이지 크기에 맞게 스케일하여 그리기
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale((float) pageWidthPts / bitmapWidth,
                            (float) pageHeightPts / bitmapHeight);
            pageCanvas.drawBitmap(bitmap, matrix, null);

            pdfDocument.finishPage(pdfPage);
            bitmap.recycle();
        }

        // 파일로 저장
        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            pdfDocument.writeTo(fos);
        } finally {
            fos.close();
            pdfDocument.close();
        }
    }

    /**
     * StrokeData를 비트맵 캔버스에 그립니다.
     * PDF point 좌표를 EXPORT_RENDER_SCALE로 변환하여 비트맵 픽셀 좌표로 사용합니다.
     */
    private void drawStrokeOnBitmap(Canvas canvas, StrokeData stroke) {
        List<float[]> points = stroke.getPoints();
        if (points.size() < 2) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float strokeWidthBitmap = stroke.getStrokeWidth() * EXPORT_RENDER_SCALE;

        switch (stroke.getToolType()) {
            case PEN:
                paint.setColor(stroke.getColor());
                paint.setAlpha(255);
                paint.setStrokeWidth(strokeWidthBitmap);
                break;
            case PENCIL:
                paint.setColor(stroke.getColor());
                paint.setAlpha(180);
                paint.setStrokeWidth(strokeWidthBitmap * 0.8f);
                break;
            case HIGHLIGHTER:
                paint.setColor(stroke.getColor());
                paint.setAlpha(100);
                paint.setStrokeWidth(strokeWidthBitmap * 3f);
                paint.setStrokeCap(Paint.Cap.SQUARE);
                break;
            case ERASER:
                // 지우개는 흰색으로 덮어씀
                paint.setColor(Color.WHITE);
                paint.setAlpha(255);
                paint.setStrokeWidth(strokeWidthBitmap * 2f);
                break;
        }

        Path path = buildPath(points, EXPORT_RENDER_SCALE);
        canvas.drawPath(path, paint);
    }

    /**
     * PDF point 좌표 목록에서 Path를 생성합니다.
     * @param scale PDF point → 대상 좌표계 변환 비율
     */
    private Path buildPath(List<float[]> points, float scale) {
        Path path = new Path();
        float[] first = points.get(0);
        path.moveTo(first[0] * scale, first[1] * scale);

        for (int i = 1; i < points.size(); i++) {
            float[] pt = points.get(i);
            path.lineTo(pt[0] * scale, pt[1] * scale);
        }
        return path;
    }
}
