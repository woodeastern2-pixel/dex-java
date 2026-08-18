package com.signpdf.app.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import com.signpdf.app.drawing.DrawingToolManager;
import com.signpdf.app.drawing.SignatureArea;
import com.signpdf.app.drawing.StrokeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 위에 겹쳐지는 투명 필기/서명 레이어입니다.
 *
 * - 펜/연필/형광펜/지우개: PDF 좌표계에 획을 저장
 * - 서명 영역: 사용자가 드래그한 영역을 서명 입력 다이얼로그와 연결
 * - 이동(PAN): PDF 확대/이동 제스처 처리
 */
public class AnnotationLayerView extends View {

    private PdfRenderView mPdfRenderView;
    private DrawingToolManager mToolManager;

    private final Map<Integer, List<StrokeData>> mPageStrokes = new HashMap<>();
    private final Map<StrokeData, Path> mStrokePathCache = new HashMap<>();
    private final Map<StrokeData, Paint> mStrokePaintCache = new HashMap<>();

    private StrokeData mCurrentStroke;
    private final Path mCurrentPath = new Path();

    private float mSelectionStartX, mSelectionStartY;
    private float mSelectionEndX, mSelectionEndY;
    private boolean mIsSelectingArea = false;
    private final List<SignatureArea> mSignatureAreas = new ArrayList<>();

    private int mCurrentPageIndex = 0;

    private final List<StrokeData> mUndoStack = new ArrayList<>();
    private final List<StrokeData> mRedoStack = new ArrayList<>();

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private float mLastMultiTouchFocusX, mLastMultiTouchFocusY;
    private boolean mIsMultiTouchTransform = false;
    private boolean mScaleHandledThisEvent = false;

    private final Paint mSelectionPaint;
    private final Paint mSelectionBorderPaint;
    private Paint mCurrentDrawPaint;

    public interface StrokeChangeListener {
        void onStrokeAdded();
        void onUndoRedoStateChanged(boolean canUndo, boolean canRedo);
    }

    public interface SignatureAreaListener {
        void onSignatureAreaSelected(SignatureArea area);
    }

    private StrokeChangeListener mStrokeListener;
    private SignatureAreaListener mSignatureAreaListener;

    public AnnotationLayerView(Context context) {
        this(context, null);
    }

    public AnnotationLayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);

        mSelectionPaint = new Paint();
        mSelectionPaint.setColor(Color.parseColor("#332196F3"));
        mSelectionPaint.setStyle(Paint.Style.FILL);

        mSelectionBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectionBorderPaint.setColor(Color.parseColor("#2196F3"));
        mSelectionBorderPaint.setStyle(Paint.Style.STROKE);
        mSelectionBorderPaint.setStrokeWidth(3f);
        mSelectionBorderPaint.setPathEffect(new DashPathEffect(new float[]{15, 8}, 0));

        setupGestureDetectors(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private void setupGestureDetectors(Context context) {
        mScaleDetector = new ScaleGestureDetector(context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    mIsMultiTouchTransform = true;
                    mLastMultiTouchFocusX = detector.getFocusX();
                    mLastMultiTouchFocusY = detector.getFocusY();
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (mPdfRenderView == null) return false;
                    panByMultiTouchFocus(detector.getFocusX(), detector.getFocusY());
                    mPdfRenderView.scaleBy(
                        detector.getScaleFactor(),
                        detector.getFocusX(),
                        detector.getFocusY());
                    mScaleHandledThisEvent = true;
                    return true;
                }
            });

        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distanceX, float distanceY) {
                    if (mPdfRenderView == null) return false;
                    mPdfRenderView.panBy(distanceX, distanceY);
                    return true;
                }
            });
    }

    public void setPdfRenderView(PdfRenderView view) {
        mPdfRenderView = view;
    }

    public void setToolManager(DrawingToolManager manager) {
        mToolManager = manager;
    }

    public void setStrokeChangeListener(StrokeChangeListener listener) {
        mStrokeListener = listener;
    }

    public void setSignatureAreaListener(SignatureAreaListener listener) {
        mSignatureAreaListener = listener;
    }

    public void setCurrentPageIndex(int index) {
        mCurrentPageIndex = index;
        clearSignatureArea();
        invalidate();
    }

    public Map<Integer, List<StrokeData>> getAllStrokes() {
        return mPageStrokes;
    }

    public Map<Integer, List<StrokeData>> getAllStrokesSnapshot() {
        Map<Integer, List<StrokeData>> snapshot = new HashMap<>();
        for (Map.Entry<Integer, List<StrokeData>> entry : mPageStrokes.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public List<StrokeData> getCurrentPageStrokes() {
        return mPageStrokes.getOrDefault(mCurrentPageIndex, new ArrayList<>());
    }

    public SignatureArea getSignatureArea() {
        if (mSignatureAreas.isEmpty()) return null;
        return mSignatureAreas.get(mSignatureAreas.size() - 1);
    }

    public List<SignatureArea> getSignatureAreas() {
        return new ArrayList<>(mSignatureAreas);
    }

    public void clearSignatureArea() {
        mSignatureAreas.clear();
        mIsSelectingArea = false;
        postInvalidateOnAnimation();
    }

    /**
     * 서명 패드의 0~1 정규화 좌표를 선택한 PDF 영역 안의 실제 StrokeData로 변환합니다.
     */
    public boolean applySignature(SignatureArea area, List<List<float[]>> normalizedStrokes) {
        if (area == null || normalizedStrokes == null || normalizedStrokes.isEmpty()) {
            return false;
        }

        float horizontalPadding = area.getWidth() * 0.04f;
        float verticalPadding = area.getHeight() * 0.08f;
        float left = area.getLeft() + horizontalPadding;
        float top = area.getTop() + verticalPadding;
        float width = Math.max(1f, area.getWidth() - horizontalPadding * 2f);
        float height = Math.max(1f, area.getHeight() - verticalPadding * 2f);
        float signatureStrokeWidth = Math.max(
            1.4f,
            Math.min(4.5f, Math.min(area.getWidth(), area.getHeight()) * 0.025f));

        List<StrokeData> pageStrokes = mPageStrokes.computeIfAbsent(
            area.getPageIndex(), key -> new ArrayList<>());
        int added = 0;

        for (List<float[]> normalizedStroke : normalizedStrokes) {
            if (normalizedStroke == null || normalizedStroke.size() < 2) continue;

            StrokeData stroke = new StrokeData(
                area.getPageIndex(),
                StrokeData.ToolType.PEN,
                Color.BLACK,
                signatureStrokeWidth);

            for (float[] point : normalizedStroke) {
                if (point == null || point.length < 2) continue;
                float x = left + clamp(point[0], 0f, 1f) * width;
                float y = top + clamp(point[1], 0f, 1f) * height;
                stroke.addPoint(x, y);
            }

            if (!stroke.isEmpty()) {
                pageStrokes.add(stroke);
                mUndoStack.add(stroke);
                added++;
            }
        }

        if (added == 0) return false;

        mRedoStack.clear();
        clearSignatureArea();
        notifyStrokeChanged();
        postInvalidateOnAnimation();
        return true;
    }

    // ==================== Touch 처리 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (mToolManager == null || mPdfRenderView == null) return false;

        DrawingToolManager.Tool tool = mToolManager.getCurrentTool();

        int action = event.getActionMasked();
        boolean multiTouch = event.getPointerCount() > 1
            || action == MotionEvent.ACTION_POINTER_DOWN
            || action == MotionEvent.ACTION_POINTER_UP;
        mScaleHandledThisEvent = false;

        if (multiTouch || mIsMultiTouchTransform || mScaleDetector.isInProgress()) {
            cancelActiveTouch();
            if (multiTouch && (action == MotionEvent.ACTION_POINTER_DOWN
                || !mIsMultiTouchTransform)) {
                startMultiTouchTransform(event, false);
            }

            mScaleDetector.onTouchEvent(event);
            handleMultiTouchTransform(event);
            return true;
        }

        mScaleDetector.onTouchEvent(event);

        switch (tool) {
            case PAN:
                mGestureDetector.onTouchEvent(event);
                return true;
            case SELECT_AREA:
                handleSelectAreaTouch(event);
                return true;
            default:
                if (mToolManager.isDrawingTool()) {
                    handleDrawingTouch(event);
                }
                return true;
        }
    }

    private void startMultiTouchTransform(MotionEvent event, boolean excludeActionPointer) {
        mIsMultiTouchTransform = true;
        mLastMultiTouchFocusX = getMultiTouchFocusX(event, excludeActionPointer);
        mLastMultiTouchFocusY = getMultiTouchFocusY(event, excludeActionPointer);
    }

    private void handleMultiTouchTransform(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() > 1) {
            if (!mScaleHandledThisEvent) {
                panByMultiTouchFocus(
                    getMultiTouchFocusX(event, false),
                    getMultiTouchFocusY(event, false));
            }
            return;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerCount() - 1 > 1) {
                startMultiTouchTransform(event, true);
            } else {
                mIsMultiTouchTransform = false;
            }
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mIsMultiTouchTransform = false;
        }
    }

    private void panByMultiTouchFocus(float focusX, float focusY) {
        if (!mIsMultiTouchTransform || mPdfRenderView == null) return;

        float deltaX = focusX - mLastMultiTouchFocusX;
        float deltaY = focusY - mLastMultiTouchFocusY;
        if (deltaX != 0f || deltaY != 0f) {
            mPdfRenderView.translateBy(deltaX, deltaY);
        }
        mLastMultiTouchFocusX = focusX;
        mLastMultiTouchFocusY = focusY;
    }

    private float getMultiTouchFocusX(MotionEvent event, boolean excludeActionPointer) {
        return getMultiTouchFocus(event, excludeActionPointer, true);
    }

    private float getMultiTouchFocusY(MotionEvent event, boolean excludeActionPointer) {
        return getMultiTouchFocus(event, excludeActionPointer, false);
    }

    private float getMultiTouchFocus(MotionEvent event,
                                     boolean excludeActionPointer,
                                     boolean xAxis) {
        int actionIndex = event.getActionIndex();
        float sum = 0f;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (excludeActionPointer && i == actionIndex) continue;
            sum += xAxis ? event.getX(i) : event.getY(i);
            count++;
        }
        if (count == 0) return 0f;
        return sum / count;
    }

    private void handleDrawingTouch(MotionEvent event) {
        float screenX = event.getX();
        float screenY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentStroke = new StrokeData(
                    mCurrentPageIndex,
                    mToolManager.getStrokeToolType(),
                    mToolManager.getCurrentColor(),
                    mToolManager.getStrokeSize()
                );
                float pdfX = mPdfRenderView.screenXToPdf(screenX);
                float pdfY = mPdfRenderView.screenYToPdf(screenY);
                if (!isPointInsidePage(pdfX, pdfY)) {
                    mCurrentStroke = null;
                    return;
                }
                mCurrentStroke.addPoint(pdfX, pdfY);

                mCurrentPath.reset();
                float sx = mPdfRenderView.pdfXToScreen(pdfX);
                float sy = mPdfRenderView.pdfYToScreen(pdfY);
                mCurrentPath.moveTo(sx, sy);
                updateCurrentPaint();
                break;

            case MotionEvent.ACTION_MOVE:
                if (mCurrentStroke == null) break;
                for (int i = 0; i < event.getHistorySize(); i++) {
                    float hx = mPdfRenderView.screenXToPdf(event.getHistoricalX(i));
                    float hy = mPdfRenderView.screenYToPdf(event.getHistoricalY(i));
                    hx = clampPdfX(hx);
                    hy = clampPdfY(hy);
                    mCurrentStroke.addPoint(hx, hy);
                    mCurrentPath.lineTo(
                        mPdfRenderView.pdfXToScreen(hx),
                        mPdfRenderView.pdfYToScreen(hy));
                }
                float mpdfX = mPdfRenderView.screenXToPdf(screenX);
                float mpdfY = mPdfRenderView.screenYToPdf(screenY);
                mpdfX = clampPdfX(mpdfX);
                mpdfY = clampPdfY(mpdfY);
                mCurrentStroke.addPoint(mpdfX, mpdfY);
                mCurrentPath.lineTo(
                    mPdfRenderView.pdfXToScreen(mpdfX),
                    mPdfRenderView.pdfYToScreen(mpdfY));
                postInvalidateOnAnimation();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrentStroke != null && !mCurrentStroke.isEmpty()) {
                    List<StrokeData> strokes = mPageStrokes.computeIfAbsent(
                        mCurrentPageIndex, k -> new ArrayList<>());
                    strokes.add(mCurrentStroke);
                    mUndoStack.add(mCurrentStroke);
                    mRedoStack.clear();
                    notifyStrokeChanged();
                }
                mCurrentStroke = null;
                mCurrentPath.reset();
                postInvalidateOnAnimation();
                break;
        }
    }

    private void handleSelectAreaTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                clearSignatureArea();
                float startPdfX = mPdfRenderView.screenXToPdf(event.getX());
                float startPdfY = mPdfRenderView.screenYToPdf(event.getY());
                if (!isPointInsidePage(startPdfX, startPdfY)) {
                    mIsSelectingArea = false;
                    return;
                }
                mSelectionStartX = event.getX();
                mSelectionStartY = event.getY();
                mSelectionEndX = mSelectionStartX;
                mSelectionEndY = mSelectionStartY;
                mIsSelectingArea = true;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsSelectingArea) return;
                mSelectionEndX = event.getX();
                mSelectionEndY = event.getY();
                postInvalidateOnAnimation();
                break;

            case MotionEvent.ACTION_UP:
                if (!mIsSelectingArea) return;
                mSelectionEndX = event.getX();
                mSelectionEndY = event.getY();
                mIsSelectingArea = false;

                float pdfLeft = clampPdfX(mPdfRenderView.screenXToPdf(
                    Math.min(mSelectionStartX, mSelectionEndX)));
                float pdfTop = clampPdfY(mPdfRenderView.screenYToPdf(
                    Math.min(mSelectionStartY, mSelectionEndY)));
                float pdfRight = clampPdfX(mPdfRenderView.screenXToPdf(
                    Math.max(mSelectionStartX, mSelectionEndX)));
                float pdfBottom = clampPdfY(mPdfRenderView.screenYToPdf(
                    Math.max(mSelectionStartY, mSelectionEndY)));

                SignatureArea area = new SignatureArea(
                    mCurrentPageIndex, pdfLeft, pdfTop, pdfRight, pdfBottom);
                if (!area.isValid() || area.getWidth() < 24f || area.getHeight() < 14f) {
                    Toast.makeText(getContext(),
                        "서명 영역을 조금 더 크게 지정해 주세요",
                        Toast.LENGTH_SHORT).show();
                    postInvalidateOnAnimation();
                    return;
                }

                mSignatureAreas.clear();
                mSignatureAreas.add(area);
                postInvalidateOnAnimation();

                if (mSignatureAreaListener != null) {
                    mSignatureAreaListener.onSignatureAreaSelected(area);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                mIsSelectingArea = false;
                clearSignatureArea();
                break;
        }
    }

    private void cancelActiveTouch() {
        boolean changed = false;
        if (mCurrentStroke != null) {
            mCurrentStroke = null;
            mCurrentPath.reset();
            changed = true;
        }
        if (mIsSelectingArea) {
            mIsSelectingArea = false;
            changed = true;
        }
        if (changed) {
            postInvalidateOnAnimation();
        }
    }

    // ==================== 그리기 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mPdfRenderView == null) return;

        List<StrokeData> pageStrokes = mPageStrokes.get(mCurrentPageIndex);
        if (pageStrokes != null) {
            for (StrokeData stroke : pageStrokes) {
                drawStroke(canvas, stroke);
            }
        }

        if (mCurrentStroke != null && mCurrentDrawPaint != null) {
            canvas.drawPath(mCurrentPath, mCurrentDrawPaint);
        }

        drawSelectionRect(canvas);
    }

    private void drawStroke(Canvas canvas, StrokeData stroke) {
        List<float[]> points = stroke.getPoints();
        if (points.size() < 2) return;

        float totalScale = mPdfRenderView.getRenderScale() * mPdfRenderView.getUserScale();
        Paint paint = getPaintForStroke(stroke);
        Path path = getPathForStroke(stroke);

        canvas.save();
        canvas.translate(mPdfRenderView.getTranslateX(), mPdfRenderView.getTranslateY());
        canvas.scale(totalScale, totalScale);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private Path getPathForStroke(StrokeData stroke) {
        Path cachedPath = mStrokePathCache.get(stroke);
        if (cachedPath != null) return cachedPath;

        Path path = new Path();
        List<float[]> points = stroke.getPoints();
        float[] first = points.get(0);
        path.moveTo(first[0], first[1]);

        for (int i = 1; i < points.size(); i++) {
            float[] point = points.get(i);
            path.lineTo(point[0], point[1]);
        }

        mStrokePathCache.put(stroke, path);
        return path;
    }

    private Paint getPaintForStroke(StrokeData stroke) {
        Paint cachedPaint = mStrokePaintCache.get(stroke);
        if (cachedPaint != null) return cachedPaint;

        Paint paint = stroke.createPaint(1f);
        mStrokePaintCache.put(stroke, paint);
        return paint;
    }

    private void drawSelectionRect(Canvas canvas) {
        if (mIsSelectingArea) {
            float left = Math.min(mSelectionStartX, mSelectionEndX);
            float top = Math.min(mSelectionStartY, mSelectionEndY);
            float right = Math.max(mSelectionStartX, mSelectionEndX);
            float bottom = Math.max(mSelectionStartY, mSelectionEndY);

            canvas.drawRect(left, top, right, bottom, mSelectionPaint);
            canvas.drawRect(left, top, right, bottom, mSelectionBorderPaint);
            return;
        }

        for (SignatureArea area : mSignatureAreas) {
            if (area.getPageIndex() != mCurrentPageIndex) continue;

            float sl = mPdfRenderView.pdfXToScreen(area.getLeft());
            float st = mPdfRenderView.pdfYToScreen(area.getTop());
            float sr = mPdfRenderView.pdfXToScreen(area.getRight());
            float sb = mPdfRenderView.pdfYToScreen(area.getBottom());

            canvas.drawRect(sl, st, sr, sb, mSelectionPaint);
            canvas.drawRect(sl, st, sr, sb, mSelectionBorderPaint);
        }
    }

    private void updateCurrentPaint() {
        if (mCurrentStroke == null) return;
        float totalScale = mPdfRenderView.getRenderScale() * mPdfRenderView.getUserScale();
        mCurrentDrawPaint = mCurrentStroke.createPaint(totalScale);
    }

    private boolean isPointInsidePage(float pdfX, float pdfY) {
        return pdfX >= 0f
            && pdfY >= 0f
            && pdfX <= mPdfRenderView.getPageWidthPts()
            && pdfY <= mPdfRenderView.getPageHeightPts();
    }

    private float clampPdfX(float pdfX) {
        return Math.max(0f, Math.min(mPdfRenderView.getPageWidthPts(), pdfX));
    }

    private float clampPdfY(float pdfY) {
        return Math.max(0f, Math.min(mPdfRenderView.getPageHeightPts(), pdfY));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Undo / Redo ====================

    public void undo() {
        if (mUndoStack.isEmpty()) return;

        StrokeData last = mUndoStack.remove(mUndoStack.size() - 1);
        List<StrokeData> pageStrokes = mPageStrokes.get(last.getPageIndex());
        if (pageStrokes != null) {
            pageStrokes.remove(last);
        }
        mStrokePathCache.remove(last);
        mStrokePaintCache.remove(last);
        mRedoStack.add(last);
        notifyStrokeChanged();
        postInvalidateOnAnimation();
    }

    public void redo() {
        if (mRedoStack.isEmpty()) return;

        StrokeData stroke = mRedoStack.remove(mRedoStack.size() - 1);
        List<StrokeData> pageStrokes = mPageStrokes.computeIfAbsent(
            stroke.getPageIndex(), k -> new ArrayList<>());
        pageStrokes.add(stroke);
        mUndoStack.add(stroke);
        notifyStrokeChanged();
        postInvalidateOnAnimation();
    }

    public void clearCurrentPage() {
        List<StrokeData> pageStrokes = mPageStrokes.get(mCurrentPageIndex);
        if (pageStrokes != null) {
            mUndoStack.removeAll(pageStrokes);
            for (StrokeData stroke : pageStrokes) {
                mStrokePathCache.remove(stroke);
                mStrokePaintCache.remove(stroke);
            }
            pageStrokes.clear();
        }
        clearSignatureArea();
        mRedoStack.clear();
        notifyStrokeChanged();
        postInvalidateOnAnimation();
    }

    public void clearAllPages() {
        mPageStrokes.clear();
        mUndoStack.clear();
        mRedoStack.clear();
        mStrokePathCache.clear();
        mStrokePaintCache.clear();
        clearSignatureArea();
        notifyStrokeChanged();
        postInvalidateOnAnimation();
    }

    public boolean canUndo() { return !mUndoStack.isEmpty(); }
    public boolean canRedo() { return !mRedoStack.isEmpty(); }

    public boolean hasAnnotations() {
        for (List<StrokeData> strokes : mPageStrokes.values()) {
            if (!strokes.isEmpty()) return true;
        }
        return false;
    }

    private void notifyStrokeChanged() {
        if (mStrokeListener != null) {
            mStrokeListener.onStrokeAdded();
            mStrokeListener.onUndoRedoStateChanged(canUndo(), canRedo());
        }
    }

    public void onTransformChanged() {
        postInvalidateOnAnimation();
    }
}
