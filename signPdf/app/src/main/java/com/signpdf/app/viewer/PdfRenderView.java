package com.signpdf.app.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * PDF 페이지를 렌더링하고 핀치 줌/팬 제스처를 처리합니다.
 *
 * 좌표 체계:
 *  - mRenderScale: PDF point → 비트맵 픽셀 (고정, 렌더링 품질)
 *  - mUserScale:   사용자 줌 배율 (핀치 제스처로 변경)
 *  - mTranslateX/Y: 팬 오프셋 (픽셀)
 *
 * 화면 좌표 → PDF point:
 *   pdfX = (screenX - mTranslateX) / (mRenderScale * mUserScale)
 */
public class PdfRenderView extends View {

    /** 렌더링 해상도 (DPI). 높을수록 선명하나 메모리 사용 증가 */
    private static final float RENDER_DPI = 120f;
    private static final float PDF_DPI = 72f;
    public static final float RENDER_SCALE = RENDER_DPI / PDF_DPI;

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 8.0f;

    private PdfRenderer mPdfRenderer;
    private Bitmap mPageBitmap;
    private int mCurrentPageIndex = 0;
    private int mPageWidthPts = 0;
    private int mPageHeightPts = 0;

    // 현재 변환 상태
    private float mUserScale = 1.0f;
    private float mMinUserScale = MIN_SCALE;
    private float mTranslateX = 0f;
    private float mTranslateY = 0f;

    // 제스처 감지기
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private boolean mGestureEnabled = false;

    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix mDrawMatrix = new Matrix();
    private final Paint mShadowPaint;
    private final Paint mBgPaint;
    private boolean mHasInitialFit = false;

    public interface TransformChangeListener {
        void onTransformChanged(float userScale, float translateX, float translateY,
                                float renderScale, float pageWidthPts, float pageHeightPts);
    }

    private TransformChangeListener mTransformListener;

    public PdfRenderView(Context context) {
        this(context, null);
    }

    public PdfRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mShadowPaint = new Paint();
        mShadowPaint.setColor(0x40000000);

        mBgPaint = new Paint();
        mBgPaint.setColor(Color.parseColor("#9E9E9E"));

        mBitmapPaint.setFilterBitmap(true);
        mBitmapPaint.setDither(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        mScaleDetector = new ScaleGestureDetector(context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scaleBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                    return true;
                }
            });

        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distanceX, float distanceY) {
                    panBy(distanceX, distanceY);
                    return true;
                }
            });
    }

    public void setPdfRenderer(PdfRenderer renderer) {
        this.mPdfRenderer = renderer;
    }

    public void setTransformChangeListener(TransformChangeListener listener) {
        this.mTransformListener = listener;
    }

    /** 제스처 처리를 활성화/비활성화합니다. AnnotationLayerView가 제스처를 위임할 때 사용 */
    public void setGestureEnabled(boolean enabled) {
        mGestureEnabled = enabled;
    }

    /** AnnotationLayerView에서 직접 변환을 주입합니다 */
    public void applyTransform(float userScale, float tx, float ty) {
        float oldScale = mUserScale;
        float oldTranslateX = mTranslateX;
        float oldTranslateY = mTranslateY;

        mUserScale = clampScale(userScale);
        mTranslateX = tx;
        mTranslateY = ty;
        clampTranslation();

        if (oldScale != mUserScale
            || oldTranslateX != mTranslateX
            || oldTranslateY != mTranslateY) {
            notifyTransformChanged();
            postInvalidateOnAnimation();
        }
    }

    public void scaleBy(float scaleFactor, float focusX, float focusY) {
        if (mPageBitmap == null || mUserScale == 0f) return;

        float newScale = clampScale(mUserScale * scaleFactor);
        float scaleRatio = newScale / mUserScale;
        float newTranslateX = focusX - (focusX - mTranslateX) * scaleRatio;
        float newTranslateY = focusY - (focusY - mTranslateY) * scaleRatio;
        applyTransform(newScale, newTranslateX, newTranslateY);
    }

    public void panBy(float distanceX, float distanceY) {
        if (mPageBitmap == null) return;
        applyTransform(mUserScale, mTranslateX - distanceX, mTranslateY - distanceY);
    }

    public void translateBy(float deltaX, float deltaY) {
        if (mPageBitmap == null) return;
        applyTransform(mUserScale, mTranslateX + deltaX, mTranslateY + deltaY);
    }

    /** 현재 페이지를 렌더링합니다 */
    public void renderPage(int pageIndex) {
        if (mPdfRenderer == null) return;
        if (pageIndex < 0 || pageIndex >= mPdfRenderer.getPageCount()) return;

        mCurrentPageIndex = pageIndex;
        mHasInitialFit = false;
        mMinUserScale = MIN_SCALE;

        // 이전 비트맵 해제
        if (mPageBitmap != null && !mPageBitmap.isRecycled()) {
            mPageBitmap.recycle();
        }

        PdfRenderer.Page page = mPdfRenderer.openPage(pageIndex);
        mPageWidthPts = page.getWidth();
        mPageHeightPts = page.getHeight();

        int bmpW = (int) (mPageWidthPts * RENDER_SCALE);
        int bmpH = (int) (mPageHeightPts * RENDER_SCALE);

        mPageBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
        mPageBitmap.eraseColor(Color.WHITE);
        page.render(mPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        // 뷰 크기에 맞게 초기 스케일 설정
        if (getWidth() > 0 && getHeight() > 0) {
            fitToView();
        }

        invalidate();
    }

    /** 뷰 크기에 맞게 초기 배율을 설정합니다 */
    public void fitToView() {
        if (mPageBitmap == null || getWidth() == 0 || getHeight() == 0) return;

        float scaleX = (float) getWidth() / mPageBitmap.getWidth();
        float scaleY = (float) getHeight() / mPageBitmap.getHeight();
        mUserScale = Math.min(scaleX, scaleY);
        mMinUserScale = Math.min(MIN_SCALE, mUserScale);

        mTranslateX = (getWidth() - mPageBitmap.getWidth() * mUserScale) / 2f;
        mTranslateY = Math.max(0f, (getHeight() - mPageBitmap.getHeight() * mUserScale) / 2f);

        mHasInitialFit = true;
        notifyTransformChanged();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mPageBitmap != null) {
            if (mHasInitialFit && oldw > 0 && oldh > 0) {
                preserveVisibleCenterOnResize(w, h, oldw, oldh);
                return;
            }
            fitToView();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPaint(mBgPaint);

        if (mPageBitmap == null || mPageBitmap.isRecycled()) return;

        float bmpW = mPageBitmap.getWidth() * mUserScale;
        float bmpH = mPageBitmap.getHeight() * mUserScale;

        // 그림자
        canvas.drawRect(mTranslateX + 6, mTranslateY + 6,
            mTranslateX + bmpW + 6, mTranslateY + bmpH + 6, mShadowPaint);

        // 페이지 비트맵
        mDrawMatrix.reset();
        mDrawMatrix.postScale(mUserScale, mUserScale);
        mDrawMatrix.postTranslate(mTranslateX, mTranslateY);
        canvas.drawBitmap(mPageBitmap, mDrawMatrix, mBitmapPaint);
    }

    private void preserveVisibleCenterOnResize(int w, int h, int oldw, int oldh) {
        float centerPdfX = (oldw * 0.5f - mTranslateX) / (RENDER_SCALE * mUserScale);
        float centerPdfY = (oldh * 0.5f - mTranslateY) / (RENDER_SCALE * mUserScale);

        mTranslateX = w * 0.5f - centerPdfX * RENDER_SCALE * mUserScale;
        mTranslateY = h * 0.5f - centerPdfY * RENDER_SCALE * mUserScale;
        clampTranslation();
        notifyTransformChanged();
        postInvalidateOnAnimation();
    }

    /** 화면 밖으로 너무 많이 이동하지 않도록 제한 */
    private void clampTranslation() {
        if (mPageBitmap == null) return;
        float bmpW = mPageBitmap.getWidth() * mUserScale;
        float bmpH = mPageBitmap.getHeight() * mUserScale;
        float margin = 50f;

        mTranslateX = Math.min(mTranslateX, getWidth() - margin);
        mTranslateX = Math.max(mTranslateX, margin - bmpW);
        mTranslateY = Math.min(mTranslateY, getHeight() - margin);
        mTranslateY = Math.max(mTranslateY, margin - bmpH);
    }

    private float clampScale(float scale) {
        return Math.max(mMinUserScale, Math.min(MAX_SCALE, scale));
    }

    private void notifyTransformChanged() {
        if (mTransformListener != null) {
            mTransformListener.onTransformChanged(
                mUserScale, mTranslateX, mTranslateY,
                RENDER_SCALE, mPageWidthPts, mPageHeightPts);
        }
    }

    // --- 외부에서 상태를 읽기 위한 Getter ---

    public float getUserScale() { return mUserScale; }
    public float getTranslateX() { return mTranslateX; }
    public float getTranslateY() { return mTranslateY; }
    public float getRenderScale() { return RENDER_SCALE; }
    public int getPageWidthPts() { return mPageWidthPts; }
    public int getPageHeightPts() { return mPageHeightPts; }
    public int getCurrentPageIndex() { return mCurrentPageIndex; }
    public int getPageCount() {
        return mPdfRenderer != null ? mPdfRenderer.getPageCount() : 0;
    }

    /** 화면 좌표 → PDF point 변환 */
    public float screenXToPdf(float screenX) {
        return (screenX - mTranslateX) / (RENDER_SCALE * mUserScale);
    }

    public float screenYToPdf(float screenY) {
        return (screenY - mTranslateY) / (RENDER_SCALE * mUserScale);
    }

    /** PDF point → 화면 좌표 변환 */
    public float pdfXToScreen(float pdfX) {
        return pdfX * RENDER_SCALE * mUserScale + mTranslateX;
    }

    public float pdfYToScreen(float pdfY) {
        return pdfY * RENDER_SCALE * mUserScale + mTranslateY;
    }

    /** 현재 PDF 페이지의 화면 상 영역을 반환합니다 */
    public RectF getPageScreenRect() {
        if (mPageBitmap == null) return new RectF();
        float bmpW = mPageBitmap.getWidth() * mUserScale;
        float bmpH = mPageBitmap.getHeight() * mUserScale;
        return new RectF(mTranslateX, mTranslateY,
                         mTranslateX + bmpW, mTranslateY + bmpH);
    }

    public void recyclePage() {
        if (mPageBitmap != null && !mPageBitmap.isRecycled()) {
            mPageBitmap.recycle();
            mPageBitmap = null;
        }
        mHasInitialFit = false;
    }
}
