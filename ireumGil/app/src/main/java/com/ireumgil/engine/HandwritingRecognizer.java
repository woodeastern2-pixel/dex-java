package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;

import java.util.List;

public interface HandwritingRecognizer {
    List<HanjaCharacter> recognizeStub(String drawnData);
}
