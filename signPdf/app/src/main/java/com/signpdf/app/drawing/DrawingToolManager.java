package com.signpdf.app.drawing;

import android.graphics.Color;

/**
 * 현재 선택된 필기 도구, 색상, 굵기를 관리합니다.
 */
public class DrawingToolManager {

    public enum Tool {
        PEN,
        PENCIL,
        HIGHLIGHTER,
        ERASER,
        SELECT_AREA,
        PAN
    }

    private Tool currentTool = Tool.PEN;
    private int currentColor = Color.BLACK;
    private float strokeSize = 4f; // PDF points

    // 기본 색상 팔레트
    public static final int[] PRESET_COLORS = {
        Color.BLACK,
        Color.parseColor("#1565C0"),  // 파랑
        Color.parseColor("#C62828"),  // 빨강
        Color.parseColor("#2E7D32"),  // 초록
        Color.GRAY,
        Color.WHITE,
        Color.parseColor("#F9A825"),  // 노랑
    };

    public static final String[] PRESET_COLOR_NAMES = {
        "검정", "파랑", "빨강", "초록", "회색", "흰색", "노랑"
    };

    public Tool getCurrentTool() { return currentTool; }
    public void setCurrentTool(Tool tool) { this.currentTool = tool; }

    public int getCurrentColor() { return currentColor; }
    public void setCurrentColor(int color) { this.currentColor = color; }

    public float getStrokeSize() { return strokeSize; }
    public void setStrokeSize(float size) { this.strokeSize = Math.max(1f, Math.min(30f, size)); }

    public boolean isDrawingTool() {
        return currentTool == Tool.PEN
            || currentTool == Tool.PENCIL
            || currentTool == Tool.HIGHLIGHTER
            || currentTool == Tool.ERASER;
    }

    public StrokeData.ToolType getStrokeToolType() {
        switch (currentTool) {
            case PENCIL: return StrokeData.ToolType.PENCIL;
            case HIGHLIGHTER: return StrokeData.ToolType.HIGHLIGHTER;
            case ERASER: return StrokeData.ToolType.ERASER;
            default: return StrokeData.ToolType.PEN;
        }
    }
}
