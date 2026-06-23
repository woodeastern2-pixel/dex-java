package com.signpdf.app.converter;

import java.io.File;
import java.io.IOException;

/**
 * Word 문서(DOC, DOCX)를 PDF로 변환하는 클래스입니다.
 *
 * ⚠️ 현재 구현 상태:
 * Android에서 Word 문서를 완벽하게 PDF로 변환하는 것은 기술적으로 매우 어렵습니다.
 * (Microsoft Office 포맷은 복잡하고, 오픈소스 라이브러리의 한계가 있습니다.)
 *
 * 현재는 구조만 준비되어 있으며, 다음 방법으로 확장 가능합니다:
 *  1. 서버 변환 API 연동 (예: Google Drive API, Microsoft Graph API)
 *  2. LibreOffice 서버 연동
 *  3. 앱 내 WebView를 이용한 제한적 렌더링
 *
 * 사용자에게는 명확한 안내 메시지를 표시합니다.
 */
public class WordToPdfConverter implements DocumentToPdfConverter {

    public static final String LIMITATION_MESSAGE =
        "Word 문서 변환은 일부 서식이 제한될 수 있습니다.\n" +
        "정확한 변환은 서버 변환 기능에서 지원 예정입니다.\n\n" +
        "현재는 PDF 파일 또는 이미지 파일을 직접 불러오시거나,\n" +
        "Word 파일을 PDF로 변환 후 불러오시기 바랍니다.";

    @Override
    public void convert(File sourceFile, File outputFile)
        throws IOException, ConversionException {
        // TODO: 서버 API 연동 또는 LibreOffice 연동 구현 예정
        throw new ConversionException(LIMITATION_MESSAGE);
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"doc", "docx"};
    }

    /**
     * 서버 기반 변환을 구현할 때의 메서드 시그니처 (향후 확장용)
     * @param serverUrl 변환 서버 엔드포인트
     * @param apiKey    인증 키
     */
    public void convertViaServer(File sourceFile, File outputFile,
                                  String serverUrl, String apiKey)
        throws IOException, ConversionException {
        // 향후 구현 예정: HTTP multipart 업로드 → PDF 다운로드
        throw new ConversionException("서버 변환 기능은 아직 지원되지 않습니다.");
    }
}
