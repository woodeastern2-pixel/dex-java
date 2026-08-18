package com.ireumgil.engine;

import com.ireumgil.data.HanjaRepository;
import com.ireumgil.model.HanjaCharacter;
import com.ireumgil.model.NameCandidate;
import com.ireumgil.model.NameFortuneReport;
import com.ireumgil.model.SajuInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NameRecommendationService {

    private final HanjaRepository repository;
    private final FiveElementAnalyzer fiveElementAnalyzer;
    private final StrokeAnalyzer strokeAnalyzer;
    private final YinYangAnalyzer yinYangAnalyzer;
    private final NameScoreCalculator scoreCalculator;

    public NameRecommendationService(HanjaRepository repository) {
        this.repository = repository;
        this.fiveElementAnalyzer = new FiveElementAnalyzer();
        this.strokeAnalyzer = new StrokeAnalyzer();
        this.yinYangAnalyzer = new YinYangAnalyzer();
        this.scoreCalculator = new NameScoreCalculator();
    }

    public List<NameCandidate> recommendBasic(String surnameHangul, String gender) {
        List<String[]> seeds = new ArrayList<>();
        if ("남자".equals(gender)) {
            seeds.add(new String[]{"준", "우"});
            seeds.add(new String[]{"민", "현"});
            seeds.add(new String[]{"지", "우"});
        } else if ("여자".equals(gender)) {
            seeds.add(new String[]{"서", "윤"});
            seeds.add(new String[]{"하", "은"});
            seeds.add(new String[]{"지", "아"});
        } else {
            seeds.add(new String[]{"민", "서"});
            seeds.add(new String[]{"준", "하"});
            seeds.add(new String[]{"지", "윤"});
        }

        SajuInput defaultSaju = new SajuInput(2023, 6, 15, 12, 0, false, gender);
        HanjaCharacter surnameHanja = pickDefaultSurnameHanja(surnameHangul);

        List<NameCandidate> out = new ArrayList<>();
        for (String[] pair : seeds) {
            HanjaCharacter first = pickBestByReading(pair[0], gender, null);
            HanjaCharacter second = pickBestByReading(pair[1], gender, null);
            if (first == null || second == null) {
                continue;
            }
            out.add(buildCandidate(surnameHangul, surnameHanja, first, second, defaultSaju, "사주 미입력 기본 균형형 추천"));
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }

    public List<NameCandidate> generateDetailed(HanjaCharacter surnameHanja, SajuInput saju, String surnameHangul, String gender) {
        Map<String, Integer> birthBalance = fiveElementAnalyzer.estimateBirthBalance(saju);
        List<String> missing = fiveElementAnalyzer.findMissingElements(birthBalance);

        List<NameCandidate> generated = new ArrayList<>();
        Set<String> seenCombination = new HashSet<>();

        List<String> readingPool = buildReadingPoolForGender(gender);
        addCandidatesFromPool(generated, seenCombination, readingPool, gender, missing, surnameHangul, surnameHanja, saju, 24);

        if (generated.size() < 9) {
            addCandidatesFromPool(generated, seenCombination, buildReadingPoolForGender("선택 안 함"), "선택 안 함", missing, surnameHangul, surnameHanja, saju, 36);
        }

        Collections.sort(generated, Comparator.comparingInt((NameCandidate c) -> c.score).reversed());

        List<NameCandidate> out = new ArrayList<>();
        for (int i = 0; i < generated.size() && i < 9; i++) {
            NameCandidate adjusted = copyOf(generated.get(i));
            if (i < 3) {
                adjusted.score = Math.max(adjusted.score, 90);
            } else if (i < 6) {
                adjusted.score = Math.max(adjusted.score, 80);
            } else {
                adjusted.score = Math.max(adjusted.score, 70);
            }
            out.add(adjusted);
        }

        Collections.sort(out, Comparator.comparingInt((NameCandidate c) -> c.score).reversed());
        return out;
    }

    private void addCandidatesFromPool(
            List<NameCandidate> generated,
            Set<String> seenCombination,
            List<String> readingPool,
            String gender,
            List<String> missing,
            String surnameHangul,
            HanjaCharacter surnameHanja,
            SajuInput saju,
            int maxCount
    ) {
        for (String firstReading : readingPool) {
            for (String secondReading : readingPool) {
                if (firstReading.equals(secondReading)) {
                    continue;
                }
                HanjaCharacter first = pickBestByReading(firstReading, gender, missing);
                HanjaCharacter second = pickBestByReading(secondReading, gender, missing);
                if (first == null || second == null) {
                    continue;
                }
                NameCandidate candidate = buildCandidate(surnameHangul, surnameHanja, first, second, saju, "생년월일시 기반 부족 오행 보완 중심");
                if (seenCombination.add(candidate.hanjaCombination)) {
                    generated.add(candidate);
                }
                if (generated.size() >= maxCount) {
                    return;
                }
            }
        }
    }

    private List<String> buildReadingPoolForGender(String gender) {
        if ("남자".equals(gender)) {
            return Arrays.asList("민", "준", "지", "현", "우", "윤");
        }
        if ("여자".equals(gender)) {
            return Arrays.asList("서", "하", "지", "윤", "아", "은", "민");
        }
        return Arrays.asList("민", "서", "준", "지", "현", "하", "윤", "우", "아", "은");
    }

    private NameCandidate copyOf(NameCandidate source) {
        NameCandidate copied = new NameCandidate();
        copied.hangulName = source.hangulName;
        copied.hanjaCombination = source.hanjaCombination;
        copied.hanjaMeaning = source.hanjaMeaning;
        copied.reason = source.reason;
        copied.fiveElementSummary = source.fiveElementSummary;
        copied.strokeSummary = source.strokeSummary;
        copied.supplementSummary = source.supplementSummary;
        copied.caution = source.caution;
        copied.score = source.score;
        copied.grade = source.grade;
        return copied;
    }

    public NameFortuneReport buildFortuneReport(String surnameHangul, HanjaCharacter surname, HanjaCharacter first, HanjaCharacter second, SajuInput saju) {
        List<HanjaCharacter> chars = Arrays.asList(surname, first, second);
        Map<String, Integer> birthBalance = fiveElementAnalyzer.estimateBirthBalance(saju);

        int strokeTotal = strokeAnalyzer.totalStrokes(chars);
        int strokeScore = strokeAnalyzer.scoreByStrokes(strokeTotal);
        int yinYangScore = yinYangAnalyzer.score(chars);

        int elementScore = 12;
        for (HanjaCharacter c : Arrays.asList(first, second)) {
            if (c.elementCategory != null && birthBalance.containsKey(c.elementCategory) && birthBalance.get(c.elementCategory) == 0) {
                elementScore += 7;
            } else {
                elementScore += 3;
            }
        }

        int meaningScore = meaningScore(first, second);
        int score = scoreCalculator.calculateTotal(elementScore, strokeScore, yinYangScore, meaningScore);

        NameFortuneReport report = new NameFortuneReport();
        report.fullName = surnameHangul + " " + first.reading + second.reading + " (" + surname.character + first.character + second.character + ")";
        report.meaningInterpretation = "한자 뜻: " + surname.character + "(" + surname.meaning + "), "
                + first.character + "(" + first.meaning + "), " + second.character + "(" + second.meaning + ")";
        report.strokeAnalysis = strokeAnalyzer.summary(strokeTotal);
        report.yinYangAnalysis = yinYangAnalyzer.summary(chars);
        report.fiveElementAnalysis = fiveElementAnalyzer.summarizeNameElements(chars);
        report.complementAnalysis = fiveElementAnalyzer.summarizeSupplement(birthBalance, Arrays.asList(first, second));
        report.strength = makeStrengthText(score, strokeScore, yinYangScore, elementScore);
        report.weakness = makeWeaknessText(score, strokeScore, yinYangScore, elementScore);
        report.caution = "전통 작명 기준에 따른 참고 결과입니다. 실제 작명 시에는 가족 선호, 법적 표기, 전문가 상담을 함께 고려하세요.";
        report.score = score;
        report.grade = scoreCalculator.grade(score);
        return report;
    }

    private NameCandidate buildCandidate(
            String surnameHangul,
            HanjaCharacter surnameHanja,
            HanjaCharacter first,
            HanjaCharacter second,
            SajuInput saju,
            String reasonPrefix
    ) {
        NameFortuneReport report = buildFortuneReport(surnameHangul, surnameHanja, first, second, saju);
        NameCandidate c = new NameCandidate();
        c.hangulName = surnameHangul + first.reading + second.reading;
        c.hanjaCombination = surnameHanja.character + first.character + second.character;
        c.hanjaMeaning = first.meaning + " / " + second.meaning;
        c.reason = reasonPrefix + ": 의미 조화와 호흡이 부드러운 조합입니다.";
        c.fiveElementSummary = report.fiveElementAnalysis;
        c.strokeSummary = report.strokeAnalysis;
        c.supplementSummary = report.complementAnalysis;
        c.caution = report.caution;
        c.score = report.score;
        c.grade = report.grade;
        return c;
    }

    private int meaningScore(HanjaCharacter first, HanjaCharacter second) {
        int base = 15;
        String firstMeaning = first.meaning == null ? "" : first.meaning;
        String secondMeaning = second.meaning == null ? "" : second.meaning;
        if (firstMeaning.contains("지혜") || firstMeaning.contains("빛") || firstMeaning.contains("은혜")) {
            base += 3;
        }
        if (secondMeaning.contains("도울") || secondMeaning.contains("어질") || secondMeaning.contains("상서")) {
            base += 2;
        }
        return base;
    }

    private String makeStrengthText(int score, int strokeScore, int yinYangScore, int elementScore) {
        if (score >= 85) {
            return "의미, 음양, 획수의 균형이 고르게 맞아 종합 흐름이 안정적입니다.";
        }
        if (elementScore >= 20) {
            return "생년월일시 기준 부족 오행 보완 효과가 비교적 뚜렷합니다.";
        }
        if (strokeScore >= 14) {
            return "획수 흐름이 무난하여 이름 리듬이 안정적으로 해석됩니다.";
        }
        return "의미 중심 해석에서 긍정 요소가 확인됩니다.";
    }

    private String makeWeaknessText(int score, int strokeScore, int yinYangScore, int elementScore) {
        if (score < 55) {
            return "오행/음양 보완이 제한적이라 추가 후보와 비교 검토가 필요합니다.";
        }
        if (yinYangScore <= 10) {
            return "획수 홀짝 편중이 있어 음양 균형이 다소 아쉽습니다.";
        }
        if (strokeScore <= 10) {
            return "수리 해석에서 기복 가능성이 있어 의미 보완이 권장됩니다.";
        }
        if (elementScore <= 15) {
            return "생년월일시 부족 오행과 이름 오행 연결이 아주 강하진 않습니다.";
        }
        return "큰 약점은 없지만, 가문 선호 한자와 실제 사용감을 함께 확인하세요.";
    }

    private HanjaCharacter pickDefaultSurnameHanja(String surnameHangul) {
        List<HanjaCharacter> list = repository.getSurnameCandidates(surnameHangul);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        HanjaCharacter fallback = repository.getByCharacter("金");
        return fallback == null ? repository.getAllAllowed().get(0) : fallback;
    }

    private HanjaCharacter pickBestByReading(String reading, String gender, List<String> preferredElements) {
        List<HanjaCharacter> list = repository.searchByReading(reading);
        HanjaCharacter best = null;
        int bestScore = -1;
        for (HanjaCharacter c : list) {
            if (!isGenderAllowed(gender, c)) {
                continue;
            }
            int s = 0;
            if ("공용".equals(c.genderPreference)) {
                s += 2;
            } else if ("남자".equals(gender) && "남".equals(c.genderPreference)) {
                s += 5;
            } else if ("여자".equals(gender) && "여".equals(c.genderPreference)) {
                s += 5;
            }
            if (preferredElements != null && preferredElements.contains(c.elementCategory)) {
                s += 6;
            }
            if (c.strokeCount != null && c.strokeCount % 2 == 1) {
                s += 1;
            }
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    private boolean isGenderAllowed(String selectedGender, HanjaCharacter character) {
        if ("선택 안 함".equals(selectedGender)) {
            return true;
        }
        if (character.genderPreference == null || "공용".equals(character.genderPreference) || "NEUTRAL".equalsIgnoreCase(character.genderPreference)) {
            return true;
        }
        if ("남자".equals(selectedGender)) {
            return "남".equals(character.genderPreference);
        }
        if ("여자".equals(selectedGender)) {
            return "여".equals(character.genderPreference);
        }
        return true;
    }
}
