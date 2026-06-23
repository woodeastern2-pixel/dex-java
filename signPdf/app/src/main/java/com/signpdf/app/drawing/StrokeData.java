package com.signpdf.app.drawing;

import android.graphics.Paint;
import java.util.ArrayList;
import java.util.List;

/**
 * 단일 필기 획(stroke) 데이터를 PDF 좌표계로 저장합니다.
 * 모든 좌표는 PDF point 단위 (1 point = 1/72 inch) 입니다.
 */
public class StrokeData {

    public enum ToolType {
        PEN,
        PENCIL,
        HIGHLIGHTER,
        ERASER
    }

    /** PDF 페이지 인덱스 (0부터) */
    private final int pageIndex;
    /** 필기 도구 종류 */
    private final ToolType toolType;
    /** 색상 (ARGB) */
    private final int color;
    /** 획 굵기 (PDF points) */
    private final float strokeWidth;
    /**
     * 획 좌표 목록.
     * 각 원소는 float[] {x, y} - PDF point 좌표.
     */
    private final List<float[]> points;

    public StrokeData(int pageIndex, ToolType toolType, int color, float strokeWidth) {
        this.pageIndex = pageIndex;
        this.toolType = toolType;
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.points = new ArrayList<>();
    }

    public void addPoint(float pdfX, float pdfY) {
        points.add(new float[]{pdfX, pdfY});
    }

    public int getPageIndex() { return pageIndex; }
    public ToolType getToolType() { return toolType; }
    public int getColor() { return color; }
    public float getStrokeWidth() { return strokeWidth; }
    public List<float[]> getPoints() { return points; }

    public boolean isEmpty() {
        return points.size() < 2;
    }

    /**
     * 이 획을 위한 Paint 객체를 생성합니다.
     * @param scaleFactor PDF point → 화면 픽셀 변환 비율 (renderScale * userScale)
     */
    public Paint createPaint(float scaleFactor) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        switch (toolType) {
            case PEN:
                paint.setColor(color);
                paint.setAlpha(255);
                paint.setStrokeWidth(strokeWidth * scaleFactor);
                break;

            case PENCIL:
                paint.setColor(color);
                paint.setAlpha(180);
                paint.setStrokeWidth(strokeWidth * scaleFactor * 0.8f);
                break;

            case HIGHLIGHTER:
                paint.setColor(color);
                paint.setAlpha(100);
                paint.setStrokeWidth(strokeWidth * scaleFactor * 3f);
                paint.setStrokeCap(Paint.Cap.SQUARE);
                break;

            case ERASER:
                // 지우개는 AnnotationLayerView에서 별도 처리
                paint.setColor(0xFFFFFFFF);
                paint.setAlpha(255);
                paint.setStrokeWidth(strokeWidth * scaleFactor * 2f);
                break;
        }

        return paint;
    }

    /**
     * PDF 저장 시 사용되는 Paint (scaleFactor = renderScale만 적용)
     */
    public Paint createExportPaint(float exportRenderScale) {
        return createPaint(exportRenderScale);
    }
}
