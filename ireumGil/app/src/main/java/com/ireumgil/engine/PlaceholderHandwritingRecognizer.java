package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderHandwritingRecognizer implements HandwritingRecognizer {

    @Override
    public List<HanjaCharacter> recognizeStub(String drawnData) {
        // MVP placeholder:
        // 실제 서비스에서는 ML Kit Digital Ink 또는 외부 한자 인식 API를 연결해
        // drawnData(스트로크 좌표)를 인식 결과 후보 리스트로 변환해야 한다.
        // 현재는 인식을 수행하지 않고 빈 결과를 반환하며,
        // UI에서 수동 후보 선택(음 검색/전체 목록)으로 안전하게 대체한다.
        return new ArrayList<>();
    }
}
