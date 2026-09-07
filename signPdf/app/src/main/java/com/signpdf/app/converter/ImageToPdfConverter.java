package com.signpdf.app.converter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 이미지 파일(JPG, PNG)을 PDF로 변환합니다.
 * Android 기본 Bitmap + PdfDocument API를 사용하므로 추가 라이브러리가 필요 없습니다.
 */
public class ImageToPdfConverter implements DocumentToPdfConverter {

    /** A4 크기 (PDF points, 72dpi 기준) */
    private static final int A4_WIDTH_PTS = 595;
    private static final int A4_HEIGHT_PTS = 842;

    @Override
    public void convert(File sourceFile, File outputFile) throws IOException, ConversionException {
        // 이미지 로드
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), options);

        if (bitmap == null) {
            throw new ConversionException("이미지를 불러올 수 없습니다: " + sourceFile.getName());
        }

        try {
            // 이미지 비율에 맞춰 PDF 페이지 크기 결정
            int pageWidth = A4_WIDTH_PTS;
            int pageHeight = A4_HEIGHT_PTS;

            float imageAspect = (float) bitmap.getWidth() / bitmap.getHeight();
            float pageAspect = (float) A4_WIDTH_PTS / A4_HEIGHT_PTS;

            int drawWidth, drawHeight, offsetX, offsetY;

            if (imageAspect > pageAspect) {
                // 이미지가 더 넓음 → 가로 꽉 채움
                drawWidth = pageWidth;
                drawHeight = (int) (pageWidth / imageAspect);
                offsetX = 0;
                offsetY = (pageHeight - drawHeight) / 2;
            } else {
                // 이미지가 더 높음 → 세로 꽉 채움
                drawHeight = pageHeight;
                drawWidth = (int) (pageHeight * imageAspect);
                offsetX = (pageWidth - drawWidth) / 2;
                offsetY = 0;
            }

            PdfDocument pdfDocument = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // 흰색 배경
            canvas.drawColor(Color.WHITE);

            // 이미지 그리기
            android.graphics.Rect srcRect = new android.graphics.Rect(
                0, 0, bitmap.getWidth(), bitmap.getHeight());
            android.graphics.Rect dstRect = new android.graphics.Rect(
                offsetX, offsetY, offsetX + drawWidth, offsetY + drawHeight);
            canvas.drawBitmap(bitmap, srcRect, dstRect, null);

            pdfDocument.finishPage(page);

            FileOutputStream fos = new FileOutputStream(outputFile);
            try {
                pdfDocument.writeTo(fos);
            } finally {
                fos.close();
                pdfDocument.close();
            }

        } finally {
            bitmap.recycle();
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"jpg", "jpeg", "png", "bmp", "webp"};
    }
}
