package com.easternwood.ireumgil.engine;

import static org.junit.Assert.assertEquals;

import com.easternwood.ireumgil.model.SajuAnalysis;
import com.easternwood.ireumgil.model.SajuInput;

import org.junit.Test;

public class SajuAnalyzerTest {

    @Test
    public void solarDateProducesKnownFourPillars() {
        SajuAnalysis result = new SajuAnalyzer().analyze(
                new SajuInput(2005, 12, 23, 8, 37, false, "선택 안 함")
        );

        assertEquals("년주 乙酉 · 월주 戊子 · 일주 辛巳 · 시주 壬辰", result.pillarsSummary);
        assertEquals(8, result.elementCounts.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    public void lunarDateIsConvertedBeforeFourPillars() {
        SajuAnalysis result = new SajuAnalyzer().analyze(
                new SajuInput(1986, 4, 21, 0, 0, true, false, "선택 안 함")
        );

        assertEquals("계산 기준 양력 1986.05.29 00:00", result.solarSummary);
    }
}
