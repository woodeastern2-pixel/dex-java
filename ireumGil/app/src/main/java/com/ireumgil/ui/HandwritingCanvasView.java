package com.ireumgil.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

public class HandwritingCanvasView extends View {

    public static class DrawingSignature {
        public final int strokeCount;
        public final float aspectRatio;
        public final float normalizedLength;

        public DrawingSignature(int strokeCount, float aspectRatio, float normalizedLength) {
            this.strokeCount = strokeCount;
            this.aspectRatio = aspectRatio;
            this.normalizedLength = normalizedLength;
        }
    }

    private static class Stroke {
        Path path;
        float length;

        Stroke(Path path, float length) {
            this.path = path;
            this.length = length;
        }
    }

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Stroke> strokes = new ArrayList<>();

    private Path currentPath;
    private float lastX;
    private float lastY;
    private float currentLength;

    public HandwritingCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(7f);
        strokePaint.setColor(Color.parseColor("#1F2E3D"));
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1.5f);
        guidePaint.setColor(Color.parseColor("#339E7B3F"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        canvas.drawLine(w / 2f, 0, w / 2f, h, guidePaint);
        canvas.drawLine(0, h / 2f, w, h / 2f, guidePaint);

        for (Stroke s : strokes) {
            canvas.drawPath(s.path, strokePaint);
        }
        if (currentPath != null) {
            canvas.drawPath(currentPath, strokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                requestParentsDisallowIntercept(true);
                currentPath = new Path();
                currentPath.moveTo(x, y);
                lastX = x;
                lastY = y;
                currentLength = 0f;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                requestParentsDisallowIntercept(true);
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    currentLength += distance(lastX, lastY, x, y);
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                requestParentsDisallowIntercept(false);
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    currentLength += distance(lastX, lastY, x, y);
                    strokes.add(new Stroke(currentPath, currentLength));
                    currentPath = null;
                    currentLength = 0f;
                    invalidate();
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                requestParentsDisallowIntercept(false);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void undoLastStroke() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public void clearAll() {
        strokes.clear();
        currentPath = null;
        invalidate();
    }

    public DrawingSignature createSignature() {
        if (strokes.isEmpty()) {
            return new DrawingSignature(0, 1f, 0f);
        }

        RectF total = new RectF();
        boolean initialized = false;
        float len = 0f;

        for (Stroke s : strokes) {
            RectF b = new RectF();
            s.path.computeBounds(b, true);
            if (!initialized) {
                total.set(b);
                initialized = true;
            } else {
                total.union(b);
            }
            len += s.length;
        }

        float w = Math.max(total.width(), 1f);
        float h = Math.max(total.height(), 1f);
        float diag = (float) Math.sqrt(w * w + h * h);
        float aspect = w / h;
        float normalizedLength = len / Math.max(diag, 1f);

        return new DrawingSignature(strokes.size(), aspect, normalizedLength);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void requestParentsDisallowIntercept(boolean disallow) {
        ViewParent p = getParent();
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
            p = p.getParent();
        }
    }
}
