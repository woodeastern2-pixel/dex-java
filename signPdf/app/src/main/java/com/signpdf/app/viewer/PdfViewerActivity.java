package com.signpdf.app.viewer;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.signpdf.app.R;
import com.signpdf.app.databinding.ActivityPdfViewerBinding;
import com.signpdf.app.drawing.DrawingToolManager;
import com.signpdf.app.drawing.PdfAnnotationExporter;
import com.signpdf.app.drawing.StrokeData;
import com.signpdf.app.util.SaveManager;
import com.signpdf.app.util.ShareManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.pdf.PdfRenderer;

public class PdfViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "extra_pdf_path";
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";

    private ActivityPdfViewerBinding mBinding;
    private DrawingToolManager mToolManager;
    private PdfRenderer mPdfRenderer;
    private ParcelFileDescriptor mParcelFd;
    private String mPdfPath;
    private String mDisplayName;
    private File mLastSavedFile;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SaveManager mSaveManager;
    private ShareManager mShareManager;

    // 색상 버튼 참조 배열
    private ImageButton[] mColorButtons;
    private int mSelectedColorIndex = 0;
    private boolean mBottomPanelExpanded = true;
    private float mBottomPanelHiddenOffset = 0f;
    private float mBottomPanelDragStartY = 0f;
    private float mBottomPanelDragStartTranslation = 0f;
    private boolean mBottomPanelDragging = false;
    private int mTouchSlop = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityPdfViewerBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mPdfPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        mDisplayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);
        if (mDisplayName == null) mDisplayName = "문서";

        mSaveManager = new SaveManager(this);
        mShareManager = new ShareManager(this);
        mToolManager = new DrawingToolManager();

        setupToolbar();
        setupAnnotationLayer();
        setupToolPanel();
        setupBottomPanelBehavior();
        setupColorPicker();
        setupStrokeSizeSlider();

        if (mPdfPath != null) {
            openPdf(mPdfPath);
        } else {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupToolbar() {
        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(mDisplayName);
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        mBinding.btnSave.setOnClickListener(v -> savePdf());
        mBinding.btnShare.setOnClickListener(v -> sharePdf());
        mBinding.btnSaveBottom.setOnClickListener(v -> savePdf());
        mBinding.btnShareBottom.setOnClickListener(v -> sharePdf());
    }

    private void setupAnnotationLayer() {
        mBinding.annotationLayer.setPdfRenderView(mBinding.pdfRenderView);
        mBinding.annotationLayer.setToolManager(mToolManager);
        mBinding.annotationLayer.setStrokeChangeListener(
            new AnnotationLayerView.StrokeChangeListener() {
                @Override
                public void onStrokeAdded() {
                    // 저장 버튼 활성화
                }

                @Override
                public void onUndoRedoStateChanged(boolean canUndo, boolean canRedo) {
                    mBinding.btnUndo.setEnabled(canUndo);
                    mBinding.btnRedo.setEnabled(canRedo);
                    mBinding.btnUndo.setAlpha(canUndo ? 1.0f : 0.4f);
                    mBinding.btnRedo.setAlpha(canRedo ? 1.0f : 0.4f);
                }
            }
        );

        // 변환 변경 시 AnnotationLayer 다시 그리기
        mBinding.pdfRenderView.setTransformChangeListener(
            (userScale, tx, ty, renderScale, pageW, pageH) -> {
                mBinding.annotationLayer.onTransformChanged();
            }
        );
    }

    private void setupToolPanel() {
        // 도구 버튼들
        mBinding.btnPen.setOnClickListener(v -> selectTool(DrawingToolManager.Tool.PEN));
        mBinding.btnPencil.setOnClickListener(v -> selectTool(DrawingToolManager.Tool.PENCIL));
        mBinding.btnHighlighter.setOnClickListener(v ->
            selectTool(DrawingToolManager.Tool.HIGHLIGHTER));
        mBinding.btnEraser.setOnClickListener(v -> selectTool(DrawingToolManager.Tool.ERASER));
        mBinding.btnPan.setOnClickListener(v -> selectTool(DrawingToolManager.Tool.PAN));
        mBinding.btnSelectArea.setOnClickListener(v ->
            selectTool(DrawingToolManager.Tool.SELECT_AREA));

        // 페이지 이동
        mBinding.btnPrevPage.setOnClickListener(v -> navigatePage(-1));
        mBinding.btnNextPage.setOnClickListener(v -> navigatePage(1));

        // Undo / Redo
        mBinding.btnUndo.setOnClickListener(v -> mBinding.annotationLayer.undo());
        mBinding.btnRedo.setOnClickListener(v -> mBinding.annotationLayer.redo());

        // 초기 비활성화
        mBinding.btnUndo.setAlpha(0.4f);
        mBinding.btnRedo.setAlpha(0.4f);
        mBinding.btnUndo.setEnabled(false);
        mBinding.btnRedo.setEnabled(false);

        // 초기 도구 선택
        selectTool(DrawingToolManager.Tool.PEN);
    }

    private void setupBottomPanelBehavior() {
        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        mBinding.bottomPanel.post(() -> {
            updateBottomPanelHiddenOffset();
            setBottomPanelExpanded(true, false);
        });

        mBinding.bottomPanelHandle.setOnClickListener(v ->
            setBottomPanelExpanded(!mBottomPanelExpanded, true));

        mBinding.bottomPanelHandle.setOnTouchListener((v, event) -> {
            updateBottomPanelHiddenOffset();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mBottomPanelDragStartY = event.getRawY();
                    mBottomPanelDragStartTranslation = mBinding.bottomPanel.getTranslationY();
                    mBottomPanelDragging = false;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = event.getRawY() - mBottomPanelDragStartY;
                    if (Math.abs(deltaY) > mTouchSlop) {
                        mBottomPanelDragging = true;
                    }
                    if (mBottomPanelDragging) {
                        float translationY = clamp(
                            mBottomPanelDragStartTranslation + deltaY,
                            0f,
                            mBottomPanelHiddenOffset);
                        mBinding.bottomPanel.setTranslationY(translationY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (mBottomPanelDragging) {
                        boolean expand = mBinding.bottomPanel.getTranslationY()
                            < mBottomPanelHiddenOffset * 0.5f;
                        setBottomPanelExpanded(expand, true);
                    } else {
                        v.performClick();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    setBottomPanelExpanded(mBottomPanelExpanded, true);
                    return true;

                default:
                    return true;
            }
        });
    }

    private void updateBottomPanelHiddenOffset() {
        int panelHeight = mBinding.bottomPanel.getHeight();
        int handleHeight = mBinding.bottomPanelHandle.getHeight();
        if (panelHeight > 0 && handleHeight > 0) {
            mBottomPanelHiddenOffset = Math.max(0f, panelHeight - handleHeight);
        }
    }

    private void setBottomPanelExpanded(boolean expanded, boolean animate) {
        updateBottomPanelHiddenOffset();
        mBottomPanelExpanded = expanded;
        updatePdfAreaForBottomPanel(expanded);

        float targetTranslation = expanded ? 0f : mBottomPanelHiddenOffset;
        mBinding.bottomPanel.animate().cancel();
        if (animate) {
            mBinding.bottomPanel.animate()
                .translationY(targetTranslation)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        } else {
            mBinding.bottomPanel.setTranslationY(targetTranslation);
        }
    }

    private void updatePdfAreaForBottomPanel(boolean panelExpanded) {
        ConstraintLayout root = mBinding.getRoot();
        ConstraintSet constraints = new ConstraintSet();
        constraints.clone(root);

        constraints.clear(R.id.pdf_container, ConstraintSet.BOTTOM);
        constraints.clear(R.id.page_nav_bar, ConstraintSet.BOTTOM);

        if (panelExpanded) {
            constraints.connect(
                R.id.pdf_container,
                ConstraintSet.BOTTOM,
                R.id.bottom_panel,
                ConstraintSet.TOP);
            constraints.connect(
                R.id.page_nav_bar,
                ConstraintSet.BOTTOM,
                R.id.bottom_panel,
                ConstraintSet.TOP,
                dpToPx(8));
        } else {
            constraints.connect(
                R.id.pdf_container,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM);
            constraints.connect(
                R.id.page_nav_bar,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                mBinding.bottomPanelHandle.getHeight() + dpToPx(8));
        }

        constraints.applyTo(root);
        mBinding.pdfRenderView.postInvalidateOnAnimation();
        mBinding.annotationLayer.postInvalidateOnAnimation();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void selectTool(DrawingToolManager.Tool tool) {
        mToolManager.setCurrentTool(tool);

        // 모든 도구 버튼 해제
        mBinding.btnPen.setSelected(false);
        mBinding.btnPencil.setSelected(false);
        mBinding.btnHighlighter.setSelected(false);
        mBinding.btnEraser.setSelected(false);
        mBinding.btnPan.setSelected(false);
        mBinding.btnSelectArea.setSelected(false);

        mBinding.btnPen.setBackground(getDrawable(R.drawable.tool_button_bg));
        mBinding.btnPencil.setBackground(getDrawable(R.drawable.tool_button_bg));
        mBinding.btnHighlighter.setBackground(getDrawable(R.drawable.tool_button_bg));
        mBinding.btnEraser.setBackground(getDrawable(R.drawable.tool_button_bg));
        mBinding.btnPan.setBackground(getDrawable(R.drawable.tool_button_bg));
        mBinding.btnSelectArea.setBackground(getDrawable(R.drawable.tool_button_bg));

        // 선택된 도구 표시
        ImageButton selectedBtn = null;
        switch (tool) {
            case PEN: selectedBtn = mBinding.btnPen; break;
            case PENCIL: selectedBtn = mBinding.btnPencil; break;
            case HIGHLIGHTER: selectedBtn = mBinding.btnHighlighter; break;
            case ERASER: selectedBtn = mBinding.btnEraser; break;
            case PAN: selectedBtn = mBinding.btnPan; break;
            case SELECT_AREA: selectedBtn = mBinding.btnSelectArea; break;
        }
        if (selectedBtn != null) {
            selectedBtn.setBackground(getDrawable(R.drawable.tool_button_selected_bg));
        }
    }

    private void setupColorPicker() {
        mColorButtons = new ImageButton[]{
            mBinding.colorBtn0,
            mBinding.colorBtn1,
            mBinding.colorBtn2,
            mBinding.colorBtn3,
            mBinding.colorBtn4,
            mBinding.colorBtn5,
            mBinding.colorBtn6,
        };

        for (int i = 0; i < mColorButtons.length; i++) {
            final int colorIndex = i;
            int color = DrawingToolManager.PRESET_COLORS[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            if (color == Color.WHITE) {
                bg.setStroke(2, Color.LTGRAY);
            }
            mColorButtons[i].setBackground(bg);
            mColorButtons[i].setOnClickListener(v -> selectColor(colorIndex));
        }

        selectColor(0); // 기본 검정
    }

    private void selectColor(int index) {
        mSelectedColorIndex = index;
        mToolManager.setCurrentColor(DrawingToolManager.PRESET_COLORS[index]);

        // 선택 표시 업데이트
        for (int i = 0; i < mColorButtons.length; i++) {
            int color = DrawingToolManager.PRESET_COLORS[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);

            if (i == index) {
                // 선택된 색상: 테두리 표시
                bg.setStroke(4, Color.parseColor("#1A3A5C"));
            } else if (color == Color.WHITE) {
                bg.setStroke(2, Color.LTGRAY);
            }
            mColorButtons[i].setBackground(bg);
        }

        // 펜 크기 미리보기 색상 업데이트
        updatePenPreview();
    }

    private void setupStrokeSizeSlider() {
        mBinding.seekStrokeSize.setMin(1);
        mBinding.seekStrokeSize.setMax(30);
        mBinding.seekStrokeSize.setProgress(4);
        mBinding.tvStrokeSizeValue.setText("4");

        mBinding.seekStrokeSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float size = Math.max(1f, progress);
                mToolManager.setStrokeSize(size);
                mBinding.tvStrokeSizeValue.setText(String.valueOf(progress));
                updatePenPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updatePenPreview();
    }

    private void updatePenPreview() {
        float sizeDp = mToolManager.getStrokeSize();
        float density = getResources().getDisplayMetrics().density;
        // 미리보기 크기: 슬라이더 값의 2배 dp (최대 30dp)
        int previewSizePx = (int) Math.min(sizeDp * 2 * density, 30 * density);
        previewSizePx = Math.max(previewSizePx, (int)(4 * density));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(mToolManager.getCurrentColor());

        android.view.ViewGroup.LayoutParams params = mBinding.viewPenPreview.getLayoutParams();
        params.width = previewSizePx;
        params.height = previewSizePx;
        mBinding.viewPenPreview.setLayoutParams(params);
        mBinding.viewPenPreview.setBackground(circle);
    }

    // ==================== PDF 열기 ====================

    private void openPdf(String path) {
        try {
            File file = new File(path);
            mParcelFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            mPdfRenderer = new PdfRenderer(mParcelFd);
            mBinding.pdfRenderView.setPdfRenderer(mPdfRenderer);

            mBinding.pdfRenderView.post(() -> {
                mBinding.pdfRenderView.renderPage(0);
                mBinding.annotationLayer.setCurrentPageIndex(0);
                updatePageIndicator();
            });

        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void navigatePage(int delta) {
        if (mPdfRenderer == null) return;
        int current = mBinding.pdfRenderView.getCurrentPageIndex();
        int next = current + delta;
        int count = mPdfRenderer.getPageCount();
        if (next < 0 || next >= count) return;

        mBinding.pdfRenderView.renderPage(next);
        mBinding.annotationLayer.setCurrentPageIndex(next);
        updatePageIndicator();
    }

    private void updatePageIndicator() {
        if (mPdfRenderer == null) return;
        int current = mBinding.pdfRenderView.getCurrentPageIndex() + 1;
        int total = mPdfRenderer.getPageCount();
        mBinding.tvPageIndicator.setText(
            getString(R.string.page_indicator, current, total));
        mBinding.btnPrevPage.setEnabled(current > 1);
        mBinding.btnNextPage.setEnabled(current < total);
        mBinding.btnPrevPage.setAlpha(current > 1 ? 1.0f : 0.4f);
        mBinding.btnNextPage.setAlpha(current < total ? 1.0f : 0.4f);
    }

    // ==================== 저장 ====================

    private void savePdf() {
        if (mPdfRenderer == null) return;

        setSavingState(true);

        Map<Integer, List<StrokeData>> strokes = mBinding.annotationLayer.getAllStrokesSnapshot();

        mExecutor.execute(() -> {
            try {
                // 임시 파일 생성
                File tempFile = File.createTempFile("signpdf_export_",
                    ".pdf", getCacheDir());

                // 필기 합성
                new PdfAnnotationExporter().export(mPdfRenderer, strokes, tempFile);

                // 최종 저장
                Uri savedUri = mSaveManager.save(tempFile, mDisplayName);
                mLastSavedFile = tempFile;

                String displayPath = mSaveManager.getDisplayPath(savedUri);

                runOnUiThread(() -> {
                    setSavingState(false);

                    new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.save_success))
                        .setMessage(getString(R.string.saved_path, displayPath))
                        .setPositiveButton("확인", null)
                        .setNeutralButton(getString(R.string.share), (d, w) -> shareSavedFile())
                        .show();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    setSavingState(false);
                    Toast.makeText(this, getString(R.string.save_failed) + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setSavingState(boolean saving) {
        mBinding.progressSaving.setVisibility(saving ? View.VISIBLE : View.GONE);
        mBinding.btnSave.setEnabled(!saving);
        mBinding.btnShare.setEnabled(!saving);
        mBinding.btnSaveBottom.setEnabled(!saving);
        mBinding.btnShareBottom.setEnabled(!saving);
        mBinding.annotationLayer.setEnabled(!saving);

        if (saving) {
            mBinding.btnPrevPage.setEnabled(false);
            mBinding.btnNextPage.setEnabled(false);
            mBinding.btnPrevPage.setAlpha(0.4f);
            mBinding.btnNextPage.setAlpha(0.4f);
        } else {
            updatePageIndicator();
        }
    }

    // ==================== 공유 ====================

    private void sharePdf() {
        if (mLastSavedFile != null && mLastSavedFile.exists()) {
            shareSavedFile();
        } else {
            // 저장 후 공유
            new AlertDialog.Builder(this)
                .setTitle("저장 후 공유")
                .setMessage("공유하려면 먼저 저장이 필요합니다. 저장하시겠습니까?")
                .setPositiveButton("저장 후 공유", (d, w) -> savePdf())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        }
    }

    private void shareSavedFile() {
        if (mLastSavedFile == null || !mLastSavedFile.exists()) return;
        startActivity(mShareManager.createShareIntentFromFile(mLastSavedFile));
    }

    // ==================== 뒤로가기 ====================

    @Override
    public void onBackPressed() {
        if (mBinding.annotationLayer.hasAnnotations()) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.discard_changes_title))
                .setMessage(getString(R.string.discard_changes_message))
                .setPositiveButton(getString(R.string.discard), (d, w) -> finish())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding.pdfRenderView.recyclePage();

        if (mPdfRenderer != null) {
            mPdfRenderer.close();
        }
        if (mParcelFd != null) {
            try {
                mParcelFd.close();
            } catch (IOException ignored) {}
        }
        mExecutor.shutdown();
    }
}
