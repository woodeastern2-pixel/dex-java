package com.ireumgil.engine;

import com.ireumgil.model.HanjaCharacter;
import com.ireumgil.model.SajuInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FiveElementAnalyzer {

    private static final String[] ELEMENTS = new String[]{"목", "화", "토", "금", "수"};

    public Map<String, Integer> estimateBirthBalance(SajuInput input) {
        Map<String, Integer> map = new HashMap<>();
        for (String e : ELEMENTS) {
            map.put(e, 0);
        }

        int seed = input.year + input.month * 3 + input.day * 5 + input.hour * 7 + input.minuteOrZero();
        map.put(ELEMENTS[Math.abs(seed) % 5], map.get(ELEMENTS[Math.abs(seed) % 5]) + 2);
        map.put(ELEMENTS[Math.abs(seed / 2) % 5], map.get(ELEMENTS[Math.abs(seed / 2) % 5]) + 1);
        map.put(ELEMENTS[Math.abs(seed / 3) % 5], map.get(ELEMENTS[Math.abs(seed / 3) % 5]) + 1);

        return map;
    }

    public String summarizeNameElements(List<HanjaCharacter> chars) {
        Map<String, Integer> cnt = new HashMap<>();
        for (String e : ELEMENTS) {
            cnt.put(e, 0);
        }
        for (HanjaCharacter c : chars) {
            if (c.elementCategory != null && cnt.containsKey(c.elementCategory)) {
                cnt.put(c.elementCategory, cnt.get(c.elementCategory) + 1);
            }
        }
        return "이름 오행 분포: 목 " + cnt.get("목") + " / 화 " + cnt.get("화") + " / 토 " + cnt.get("토") + " / 금 " + cnt.get("금") + " / 수 " + cnt.get("수");
    }

    public List<String> findMissingElements(Map<String, Integer> birthBalance) {
        List<String> missing = new ArrayList<>();
        for (String e : ELEMENTS) {
            if (birthBalance.get(e) == null || birthBalance.get(e) == 0) {
                missing.add(e);
            }
        }
        return missing;
    }

    public String summarizeSupplement(Map<String, Integer> birthBalance, List<HanjaCharacter> nameChars) {
        List<String> missing = findMissingElements(birthBalance);
        if (missing.isEmpty()) {
            return "생년월일시 기준 오행이 비교적 고르게 보여, 이름은 안정형 보완으로 제안했습니다.";
        }

        List<String> covered = new ArrayList<>();
        for (String m : missing) {
            for (HanjaCharacter c : nameChars) {
                if (c.elementCategory != null && m.equals(c.elementCategory)) {
                    covered.add(m);
                    break;
                }
            }
        }

        if (covered.isEmpty()) {
            return "부족 오행(" + String.join(", ", missing) + ") 보완이 제한적이어서 의미·획수 중심으로 균형을 맞췄습니다.";
        }

        return "부족 오행(" + String.join(", ", missing) + ") 중 " + String.join(", ", covered) + " 기운 보완을 우선 고려했습니다.";
    }
}
