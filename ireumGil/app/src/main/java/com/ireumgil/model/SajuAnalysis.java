package com.ireumgil.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SajuAnalysis {
    public final String inputSummary;
    public final String solarSummary;
    public final String pillarsSummary;
    public final Map<String, Integer> elementCounts;
    public final String calculationNote;

    public SajuAnalysis(
            String inputSummary,
            String solarSummary,
            String pillarsSummary,
            Map<String, Integer> elementCounts,
            String calculationNote
    ) {
        this.inputSummary = inputSummary;
        this.solarSummary = solarSummary;
        this.pillarsSummary = pillarsSummary;
        this.elementCounts = Collections.unmodifiableMap(new LinkedHashMap<>(elementCounts));
        this.calculationNote = calculationNote;
    }
}
