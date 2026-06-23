package com.signpdf.app.converter;

import java.io.File;
import java.io.IOException;

/**
 * 문서를 PDF로 변환하는 인터페이스입니다.
 * 다양한 문서 형식에 대해 이 인터페이스를 구현하여 사용합니다.
 */
public interface DocumentToPdfConverter {

    /**
     * 주어진 소스 파일을 PDF로 변환합니다.
     *
     * @param sourceFile 변환할 원본 파일
     * @param outputFile 생성할 PDF 파일 경로
     * @throws IOException          파일 읽기/쓰기 실패 시
     * @throws ConversionException  변환 과정에서 오류 발생 시
     */
    void convert(File sourceFile, File outputFile) throws IOException, ConversionException;

    /**
     * 이 변환기가 지원하는 파일 확장자 목록을 반환합니다.
     * 예: ["jpg", "jpeg", "png"]
     */
    String[] getSupportedExtensions();

    /**
     * 변환 예외 클래스
     */
    class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }
        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
