package com.signpdf.app.drawing;

import android.graphics.RectF;

/**
 * 사용자가 PDF 위에서 지정한 서명/필기 영역을 나타냅니다.
 * 좌표는 PDF point 단위입니다.
 */
public class SignatureArea {

    private final int pageIndex;
    private final RectF rectPdf; // PDF point 좌표

    public SignatureArea(int pageIndex, float left, float top, float right, float bottom) {
        this.pageIndex = pageIndex;
        this.rectPdf = new RectF(
            Math.min(left, right),
            Math.min(top, bottom),
            Math.max(left, right),
            Math.max(top, bottom)
        );
    }

    public int getPageIndex() { return pageIndex; }

    public RectF getRectPdf() { return new RectF(rectPdf); }

    /** 주어진 PDF 좌표가 이 영역 안에 있는지 확인합니다. */
    public boolean contains(float pdfX, float pdfY) {
        return rectPdf.contains(pdfX, pdfY);
    }

    public float getLeft() { return rectPdf.left; }
    public float getTop() { return rectPdf.top; }
    public float getRight() { return rectPdf.right; }
    public float getBottom() { return rectPdf.bottom; }
    public float getWidth() { return rectPdf.width(); }
    public float getHeight() { return rectPdf.height(); }

    public boolean isValid() {
        return rectPdf.width() > 5f && rectPdf.height() > 5f;
    }
}
