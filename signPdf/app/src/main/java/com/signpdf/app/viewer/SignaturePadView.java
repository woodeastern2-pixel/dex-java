package com.signpdf.app.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 간단한 손서명 입력 패드입니다.
 * 서명 좌표는 View 좌표로 보관하고, PDF에 삽입할 때 0~1 정규화 좌표로 변환합니다.
 */
public class SignaturePadView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<List<float[]>> mStrokes = new ArrayList<>();
    private final List<Path> mPaths = new ArrayList<>();

    private List<float[]> mCurrentStroke;
    private Path mCurrentPath;

    public SignaturePadView(Context context) {
        this(context, null);
    }

    public SignaturePadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.WHITE);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeWidth(dpToPx(3f));
        setMinimumHeight((int) dpToPx(180f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = clamp(event.getX(), 0f, getWidth());
        float y = clamp(event.getY(), 0f, getHeight());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                mCurrentStroke = new ArrayList<>();
                mCurrentPath = new Path();
                mCurrentStroke.add(new float[]{x, y});
                mCurrentPath.moveTo(x, y);
                mStrokes.add(mCurrentStroke);
                mPaths.add(mCurrentPath);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mCurrentStroke == null || mCurrentPath == null) return true;
                for (int i = 0; i < event.getHistorySize(); i++) {
                    addPoint(
                        clamp(event.getHistoricalX(i), 0f, getWidth()),
                        clamp(event.getHistoricalY(i), 0f, getHeight()));
                }
                addPoint(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrentStroke != null && mCurrentStroke.size() == 1) {
                    // 점 하나만 찍어도 짧은 획으로 저장되도록 보정합니다.
                    float[] first = mCurrentStroke.get(0);
                    addPoint(first[0] + 0.5f, first[1] + 0.5f);
                }
                mCurrentStroke = null;
                mCurrentPath = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;

            default:
                return true;
        }
    }

    private void addPoint(float x, float y) {
        if (mCurrentStroke == null || mCurrentPath == null) return;
        mCurrentStroke.add(new float[]{x, y});
        mCurrentPath.lineTo(x, y);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Path path : mPaths) {
            canvas.drawPath(path, mPaint);
        }
    }

    public void clear() {
        mStrokes.clear();
        mPaths.clear();
        mCurrentStroke = null;
        mCurrentPath = null;
        invalidate();
    }

    public boolean isEmpty() {
        for (List<float[]> stroke : mStrokes) {
            if (stroke.size() >= 2) return false;
        }
        return true;
    }

    /**
     * 서명을 0~1 범위의 정규화 좌표로 반환합니다.
     * 선택한 PDF 영역 크기에 관계없이 동일한 비율로 배치할 수 있습니다.
     */
    public List<List<float[]>> getNormalizedStrokes() {
        List<List<float[]>> result = new ArrayList<>();
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());

        for (List<float[]> stroke : mStrokes) {
            if (stroke.size() < 2) continue;
            List<float[]> normalized = new ArrayList<>();
            for (float[] point : stroke) {
                normalized.add(new float[]{
                    clamp(point[0] / width, 0f, 1f),
                    clamp(point[1] / height, 0f, 1f)
                });
            }
            result.add(normalized);
        }
        return result;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
