package com.signpdf.app.drawing;

/**
 * PDF 좌표 ↔ 화면 좌표 변환을 담당합니다.
 *
 * 좌표 체계:
 *  - PDF point: 1/72 inch 단위, PdfRenderer가 반환하는 원본 페이지 크기
 *  - Bitmap pixel: PDF 페이지를 renderScale 배율로 렌더링한 비트맵 픽셀
 *  - Screen pixel: 뷰에 표시되는 화면 픽셀 (userScale, translate 적용)
 *
 * 공식:
 *  screenX = pdfX * renderScale * userScale + translateX
 *  pdfX    = (screenX - translateX) / (renderScale * userScale)
 */
public class PdfCoordinateMapper {

    /** PDF 페이지를 비트맵으로 렌더링할 때의 확대 비율 (비트맵픽셀/PDF포인트) */
    private float renderScale;
    /** 사용자 줌 배율 */
    private float userScale;
    /** 화면에서의 X 오프셋 (translate) */
    private float translateX;
    /** 화면에서의 Y 오프셋 (translate) */
    private float translateY;

    public PdfCoordinateMapper() {
        renderScale = 2.0f;
        userScale = 1.0f;
        translateX = 0f;
        translateY = 0f;
    }

    public void update(float renderScale, float userScale, float translateX, float translateY) {
        this.renderScale = renderScale;
        this.userScale = userScale;
        this.translateX = translateX;
        this.translateY = translateY;
    }

    /** 화면 좌표 → PDF point 좌표 */
    public float screenXToPdf(float screenX) {
        return (screenX - translateX) / (renderScale * userScale);
    }

    public float screenYToPdf(float screenY) {
        return (screenY - translateY) / (renderScale * userScale);
    }

    public float[] screenToPdf(float screenX, float screenY) {
        return new float[]{screenXToPdf(screenX), screenYToPdf(screenY)};
    }

    /** PDF point 좌표 → 화면 좌표 */
    public float pdfXToScreen(float pdfX) {
        return pdfX * renderScale * userScale + translateX;
    }

    public float pdfYToScreen(float pdfY) {
        return pdfY * renderScale * userScale + translateY;
    }

    public float[] pdfToScreen(float pdfX, float pdfY) {
        return new float[]{pdfXToScreen(pdfX), pdfYToScreen(pdfY)};
    }

    /**
     * PDF point 단위의 획 굵기를 화면 픽셀 단위로 변환합니다.
     */
    public float pdfStrokeWidthToScreen(float pdfWidth) {
        return pdfWidth * renderScale * userScale;
    }

    /** PDF point 단위의 획 굵기를 비트맵 픽셀 단위로 변환합니다. (Export 용) */
    public float pdfStrokeWidthToBitmap(float pdfWidth) {
        return pdfWidth * renderScale;
    }

    public float getRenderScale() { return renderScale; }
    public float getUserScale() { return userScale; }
    public float getTranslateX() { return translateX; }
    public float getTranslateY() { return translateY; }
    public float getTotalScale() { return renderScale * userScale; }
}
