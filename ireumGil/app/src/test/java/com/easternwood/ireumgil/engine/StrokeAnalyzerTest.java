package com.easternwood.ireumgil.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.easternwood.ireumgil.model.HanjaCharacter;

import org.junit.Test;

import java.util.Arrays;

public class StrokeAnalyzerTest {

    @Test
    public void calculatesFourGridsFromAllThreeCharacters() {
        StrokeAnalyzer.Analysis result = new StrokeAnalyzer().analyze(Arrays.asList(
                character("韓", "한", 17),
                character("柳", "유", 9),
                character("丁", "정", 2)
        ));

        assertTrue(result.valid);
        assertEquals(28, result.total);
        assertTrue(result.detail.contains("원격 11(길)"));
        assertTrue(result.detail.contains("형격 26(주의)"));
        assertTrue(result.detail.contains("이격 19(주의)"));
        assertTrue(result.detail.contains("정격 28(주의)"));
    }

    @Test
    public void refusesScoreWhenStrokeSourceIsMissing() {
        HanjaCharacter missing = new HanjaCharacter(
                0L, "𠮷", "길", "길할", null, null, "목",
                true, false, false, false, null, false, "공용",
                "test", "1", "test", "test"
        );
        StrokeAnalyzer.Analysis result = new StrokeAnalyzer().analyze(Arrays.asList(
                character("韓", "한", 17), missing, character("丁", "정", 2)
        ));

        assertFalse(result.valid);
        assertEquals(0, result.score);
    }

    private HanjaCharacter character(String value, String reading, int strokes) {
        return new HanjaCharacter(value, reading, "뜻", strokes, "목", true, "공용", "test");
    }
}
