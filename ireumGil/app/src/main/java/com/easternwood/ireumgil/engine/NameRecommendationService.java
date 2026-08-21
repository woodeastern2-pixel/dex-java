package com.easternwood.ireumgil.engine;

import com.easternwood.ireumgil.data.HanjaRepository;
import com.easternwood.ireumgil.model.HanjaCharacter;
import com.easternwood.ireumgil.model.NameCandidate;
import com.easternwood.ireumgil.model.NameFortuneReport;
import com.easternwood.ireumgil.model.SajuInput;
import com.easternwood.ireumgil.model.SajuAnalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
            addSeedNames(seeds, "서준", "민준", "도윤", "시우", "하준", "예준", "지호", "주원", "도현", "지후");
        } else if ("여자".equals(gender)) {
            addSeedNames(seeds, "서윤", "서연", "지우", "하윤", "서현", "하은", "민서", "지유", "윤서", "지아");
        } else {
            addSeedNames(seeds, "지우", "서우", "도윤", "지안", "시우", "하윤", "연우", "수현", "민서", "주원");
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

    public List<NameCandidate> generateForHangulName(
            HanjaCharacter surnameHanja,
            SajuInput saju,
            String surnameHangul,
            String gender,
            String givenName
    ) {
        String normalized = givenName == null ? "" : givenName.trim();
        if (normalized.length() != 2) {
            return new ArrayList<>();
        }
        String firstReading = normalized.substring(0, 1);
        String secondReading = normalized.substring(1, 2);
        Map<String, Integer> birthBalance = fiveElementAnalyzer.estimateBirthBalance(saju);
        List<String> missing = fiveElementAnalyzer.findMissingElements(birthBalance);
        List<HanjaCharacter> firstPool = rankCharacters(
                repository.searchByReading(firstReading), gender, missing, 40);
        List<HanjaCharacter> secondPool = rankCharacters(
                repository.searchByReading(secondReading), gender, missing, 40);
        List<ScoredCombination> combinations = evaluateCombinations(
                surnameHanja, birthBalance, firstPool, secondPool, gender, new HashSet<>());
        return selectAndBuild(
                combinations,
                surnameHangul,
                surnameHanja,
                saju,
                "원하는 한글 이름의 공식 인명용 한자 조합"
        );
    }

    public List<NameCandidate> generateDetailed(HanjaCharacter surnameHanja, SajuInput saju, String surnameHangul, String gender) {
        Map<String, Integer> birthBalance = fiveElementAnalyzer.estimateBirthBalance(saju);
        List<String> missing = fiveElementAnalyzer.findMissingElements(birthBalance);

        List<String> readingPool = buildReadingPoolForGender(gender);
        Map<String, List<HanjaCharacter>> rankedByReading = new java.util.LinkedHashMap<>();
        for (String reading : readingPool) {
            rankedByReading.put(reading, rankCharacters(
                    repository.searchByReading(reading), gender, missing, 14));
        }

        List<ScoredCombination> generated = new ArrayList<>();
        Set<String> seenCombination = new HashSet<>();
        for (String firstReading : readingPool) {
            for (String secondReading : readingPool) {
                if (firstReading.equals(secondReading)) {
                    continue;
                }
                generated.addAll(evaluateCombinations(
                        surnameHanja,
                        birthBalance,
                        rankedByReading.get(firstReading),
                        rankedByReading.get(secondReading),
                        gender,
                        seenCombination
                ));
            }
        }
        return selectAndBuild(
                generated,
                surnameHangul,
                surnameHanja,
                saju,
                "생년월일시 기반 부족 오행 보완 중심"
        );
    }

    private List<String> buildReadingPoolForGender(String gender) {
        LinkedHashSet<String> readings = new LinkedHashSet<>();
        if ("남자".equals(gender)) {
            addNameReadings(readings, "서준", "민준", "도윤", "시우", "하준", "예준", "지호", "주원", "도현", "지후", "이안", "은우", "선우", "유준", "수호");
        } else if ("여자".equals(gender)) {
            addNameReadings(readings, "서윤", "서연", "지우", "하윤", "서현", "하은", "민서", "지유", "윤서", "지아", "서아", "하린", "아윤", "유나", "채아");
        } else {
            addNameReadings(readings, "지우", "서우", "도윤", "지안", "시우", "하윤", "연우", "수현", "민서", "주원");
        }
        return new ArrayList<>(readings);
    }

    private List<ScoredCombination> evaluateCombinations(
            HanjaCharacter surname,
            Map<String, Integer> birthBalance,
            List<HanjaCharacter> firstPool,
            List<HanjaCharacter> secondPool,
            String gender,
            Set<String> seen
    ) {
        List<ScoredCombination> out = new ArrayList<>();
        if (surname == null || firstPool == null || secondPool == null) {
            return out;
        }
        for (HanjaCharacter first : firstPool) {
            if (!isGenderAllowed(gender, first)) {
                continue;
            }
            for (HanjaCharacter second : secondPool) {
                if (!isGenderAllowed(gender, second)) {
                    continue;
                }
                String key = surname.character + first.character + second.character;
                if (!seen.add(key)) {
                    continue;
                }
                List<HanjaCharacter> chars = Arrays.asList(surname, first, second);
                int strokeScore = strokeAnalyzer.scoreOnly(chars);
                if (strokeScore <= 0) {
                    continue;
                }
                int score = scoreCalculator.calculateTotal(
                        fiveElementAnalyzer.scoreSupplement(birthBalance, Arrays.asList(first, second)),
                        strokeScore,
                        yinYangAnalyzer.score(chars),
                        dataCompletenessScore(surname, first, second)
                );
                out.add(new ScoredCombination(first, second, score));
            }
        }
        return out;
    }

    private List<HanjaCharacter> rankCharacters(
            List<HanjaCharacter> source,
            String gender,
            List<String> preferredElements,
            int limit
    ) {
        List<HanjaCharacter> ranked = new ArrayList<>();
        for (HanjaCharacter character : source) {
            if (isGenderAllowed(gender, character)
                    && character.strokeCount != null
                    && character.strokeCount > 0
                    && hasSuitableMeaning(character)) {
                ranked.add(character);
            }
        }
        ranked.sort((left, right) -> Integer.compare(
                characterQuality(right, gender, preferredElements),
                characterQuality(left, gender, preferredElements)));

        List<HanjaCharacter> diverse = new ArrayList<>();
        Set<Integer> strokeCounts = new HashSet<>();
        for (HanjaCharacter character : ranked) {
            if (strokeCounts.add(character.strokeCount)) {
                diverse.add(character);
                if (diverse.size() >= limit) {
                    return diverse;
                }
            }
        }
        for (HanjaCharacter character : ranked) {
            if (!diverse.contains(character)) {
                diverse.add(character);
                if (diverse.size() >= limit) {
                    break;
                }
            }
        }
        return diverse;
    }

    private int characterQuality(HanjaCharacter character, String gender, List<String> preferredElements) {
        int score = 0;
        if (Boolean.TRUE.equals(character.allowedForName)) score += 8;
        if (character.meaning != null && !character.meaning.trim().isEmpty()) score += 4;
        if (preferredElements != null && preferredElements.contains(character.elementCategory)) score += 6;
        if ("공용".equals(character.genderPreference)) score += 2;
        if ("남자".equals(gender) && "남".equals(character.genderPreference)) score += 3;
        if ("여자".equals(gender) && "여".equals(character.genderPreference)) score += 3;
        return score;
    }

    private boolean hasSuitableMeaning(HanjaCharacter character) {
        if (character.meaning == null || character.meaning.trim().isEmpty()) {
            return false;
        }
        String meaning = character.meaning;
        String[] discouraged = {
                "죽", "썩", "시체", "귀신", "병들", "질병", "악할", "흉할",
                "재앙", "죄", "벌", "도둑", "굶", "가난", "괴로", "슬플",
                "해칠", "상처", "독", "미칠", "어리석", "다툴", "원수",
                "못쓸", "물어뜯", "무너뜨", "속일", "꺼릴"
        };
        for (String word : discouraged) {
            if (meaning.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private List<NameCandidate> selectAndBuild(
            List<ScoredCombination> combinations,
            String surnameHangul,
            HanjaCharacter surname,
            SajuInput saju,
            String reason
    ) {
        combinations.sort(Comparator.comparingInt((ScoredCombination item) -> item.score).reversed());
        List<ScoredCombination> selected = new ArrayList<>();
        addScoreBand(selected, combinations, 95, 101, 3);
        addScoreBand(selected, combinations, 85, 95, 3);
        addScoreBand(selected, combinations, 70, 85, 3);
        addScoreBand(selected, combinations, 0, 70, 3);

        if (selected.isEmpty()) {
            for (int index = 0; index < combinations.size() && index < 12; index++) {
                selected.add(combinations.get(index));
            }
        }

        List<NameCandidate> out = new ArrayList<>();
        for (ScoredCombination item : selected) {
            out.add(buildCandidate(surnameHangul, surname, item.first, item.second, saju, reason));
        }
        out.sort(Comparator.comparingInt((NameCandidate candidate) -> candidate.score).reversed());
        return out;
    }

    private void addScoreBand(
            List<ScoredCombination> selected,
            List<ScoredCombination> source,
            int minScore,
            int maxScoreExclusive,
            int limit
    ) {
        int count = 0;
        for (ScoredCombination item : source) {
            if (item.score >= minScore && item.score < maxScoreExclusive) {
                selected.add(item);
                count++;
                if (count >= limit) {
                    return;
                }
            }
        }
    }

    private static class ScoredCombination {
        final HanjaCharacter first;
        final HanjaCharacter second;
        final int score;

        ScoredCombination(HanjaCharacter first, HanjaCharacter second, int score) {
            this.first = first;
            this.second = second;
            this.score = score;
        }
    }

    private void addSeedNames(List<String[]> seeds, String... names) {
        for (String name : names) {
            if (name.length() == 2) seeds.add(new String[]{name.substring(0, 1), name.substring(1, 2)});
        }
    }

    private void addNameReadings(Set<String> output, String... names) {
        for (String name : names) {
            for (int i = 0; i < name.length(); i++) output.add(name.substring(i, i + 1));
        }
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
        SajuAnalysis sajuAnalysis = fiveElementAnalyzer.analyzeBirth(saju);
        Map<String, Integer> birthBalance = sajuAnalysis.elementCounts;

        StrokeAnalyzer.Analysis strokeAnalysis = strokeAnalyzer.analyze(chars);
        if (!strokeAnalysis.valid) {
            throw new IllegalArgumentException(strokeAnalysis.detail);
        }
        int strokeScore = strokeAnalysis.score;
        int yinYangScore = yinYangAnalyzer.score(chars);
        int elementScore = fiveElementAnalyzer.scoreSupplement(birthBalance, Arrays.asList(first, second));
        int dataScore = dataCompletenessScore(surname, first, second);
        int score = scoreCalculator.calculateTotal(elementScore, strokeScore, yinYangScore, dataScore);

        NameFortuneReport report = new NameFortuneReport();
        report.fullName = surnameHangul + " " + first.reading + second.reading + " (" + surname.character + first.character + second.character + ")";
        report.meaningInterpretation = "한자 뜻: " + surname.character + "(" + surname.meaning + "), "
                + first.character + "(" + first.meaning + "), " + second.character + "(" + second.meaning + ")";
        report.inputBasis = sajuAnalysis.inputSummary + "\n" + sajuAnalysis.solarSummary;
        report.fourPillars = sajuAnalysis.pillarsSummary;
        report.strokeAnalysis = strokeAnalysis.detail;
        report.yinYangAnalysis = yinYangAnalyzer.summary(chars);
        report.fiveElementAnalysis = fiveElementAnalyzer.summarizeBirthElements(sajuAnalysis)
                + "\n" + fiveElementAnalyzer.summarizeNameElements(chars);
        report.complementAnalysis = fiveElementAnalyzer.summarizeSupplement(birthBalance, Arrays.asList(first, second));
        report.scoreBreakdown = "사주·이름 오행 " + elementScore + "/35 · 수리 " + strokeScore
                + "/30 · 음양 " + yinYangScore + "/15 · 한자 데이터 " + dataScore + "/20";
        report.calculationBasis = sajuAnalysis.calculationNote
                + " 수리는 3글자 이름의 원격·형격·이격·정격, 음양은 원획 홀짝을 기준으로 했습니다.";
        report.strength = makeStrengthText(score, strokeScore, yinYangScore, elementScore);
        report.weakness = makeWeaknessText(score, strokeScore, yinYangScore, elementScore);
        report.caution = "이 점수는 전통 성명학 기준을 투명하게 수치화한 참고값이며 과학적 예측이나 운명 판정이 아닙니다.";
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

    private int dataCompletenessScore(HanjaCharacter surname, HanjaCharacter first, HanjaCharacter second) {
        int score = 8;
        for (HanjaCharacter c : Arrays.asList(first, second)) {
            if (Boolean.TRUE.equals(c.allowedForName)) score += 3;
            if (c.meaning != null && !c.meaning.trim().isEmpty()) score += 2;
        }
        if (surname.strokeCount != null && first.strokeCount != null && second.strokeCount != null) score += 2;
        return Math.min(20, score);
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
