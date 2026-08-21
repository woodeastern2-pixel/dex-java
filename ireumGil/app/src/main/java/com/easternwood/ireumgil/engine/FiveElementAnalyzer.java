package com.easternwood.ireumgil.engine;

import com.easternwood.ireumgil.model.HanjaCharacter;
import com.easternwood.ireumgil.model.SajuAnalysis;
import com.easternwood.ireumgil.model.SajuInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FiveElementAnalyzer {

    private static final String[] ELEMENTS = {"목", "화", "토", "금", "수"};
    private final SajuAnalyzer sajuAnalyzer = new SajuAnalyzer();

    public SajuAnalysis analyzeBirth(SajuInput input) {
        return sajuAnalyzer.analyze(input);
    }

    public Map<String, Integer> estimateBirthBalance(SajuInput input) {
        return new LinkedHashMap<>(analyzeBirth(input).elementCounts);
    }

    public int scoreSupplement(Map<String, Integer> birthBalance, List<HanjaCharacter> nameChars) {
        if (birthBalance == null || birthBalance.size() < 5 || nameChars == null || nameChars.size() != 2) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (String element : ELEMENTS) {
            min = Math.min(min, valueOf(birthBalance, element));
        }

        int score = 15;
        List<String> alreadyRewarded = new ArrayList<>();
        for (HanjaCharacter c : nameChars) {
            String element = c == null ? null : c.elementCategory;
            if (!birthBalance.containsKey(element)) continue;
            int count = valueOf(birthBalance, element);
            int addition;
            if (count == 0) addition = 10;
            else if (count == min) addition = 8;
            else if (count <= 1) addition = 6;
            else addition = 3;
            if (alreadyRewarded.contains(element)) addition = Math.max(2, addition / 2);
            score += addition;
            alreadyRewarded.add(element);
        }
        return Math.min(35, score);
    }

    public String summarizeBirthElements(SajuAnalysis analysis) {
        Map<String, Integer> count = analysis.elementCounts;
        return "사주 오행: 목 " + valueOf(count, "목")
                + " · 화 " + valueOf(count, "화")
                + " · 토 " + valueOf(count, "토")
                + " · 금 " + valueOf(count, "금")
                + " · 수 " + valueOf(count, "수");
    }

    public String summarizeNameElements(List<HanjaCharacter> chars) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (String e : ELEMENTS) count.put(e, 0);
        for (HanjaCharacter c : chars) {
            if (c != null && count.containsKey(c.elementCategory)) {
                count.put(c.elementCategory, count.get(c.elementCategory) + 1);
            }
        }
        return "이름 발음 오행: 목 " + count.get("목")
                + " · 화 " + count.get("화")
                + " · 토 " + count.get("토")
                + " · 금 " + count.get("금")
                + " · 수 " + count.get("수");
    }

    public List<String> findMissingElements(Map<String, Integer> birthBalance) {
        List<String> weakest = new ArrayList<>();
        int min = Integer.MAX_VALUE;
        for (String e : ELEMENTS) min = Math.min(min, valueOf(birthBalance, e));
        for (String e : ELEMENTS) {
            if (valueOf(birthBalance, e) == min) weakest.add(e);
        }
        return weakest;
    }

    public String summarizeSupplement(Map<String, Integer> birthBalance, List<HanjaCharacter> nameChars) {
        List<String> weakest = findMissingElements(birthBalance);
        List<String> covered = new ArrayList<>();
        for (String element : weakest) {
            for (HanjaCharacter c : nameChars) {
                if (c != null && element.equals(c.elementCategory)) {
                    covered.add(element);
                    break;
                }
            }
        }
        if (covered.isEmpty()) {
            return "사주에서 가장 적은 오행(" + String.join("·", weakest) + ")을 직접 보완하지 않는 구성입니다.";
        }
        return "사주에서 가장 적은 오행(" + String.join("·", weakest) + ") 중 "
                + String.join("·", covered) + "을 이름 발음 오행으로 보완합니다.";
    }

    private int valueOf(Map<String, Integer> values, String key) {
        Integer value = values == null ? null : values.get(key);
        return value == null ? 0 : value;
    }
}
