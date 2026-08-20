package com.ireumgil.engine;

import com.ireumgil.model.SajuAnalysis;
import com.ireumgil.model.SajuInput;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.util.LinkedHashMap;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Converts the entered calendar date and calculates the four pillars at the
 * actual solar-term boundary. No network response or pseudo-random seed is used.
 */
public class SajuAnalyzer {

    private static final String[] ELEMENTS = {"목", "화", "토", "금", "수"};

    public SajuAnalysis analyze(SajuInput input) {
        validate(input);

        Solar solar;
        if (input.lunar) {
            int lunarMonth = input.lunarLeapMonth ? -input.month : input.month;
            Lunar lunar = Lunar.fromYmdHms(
                    input.year,
                    lunarMonth,
                    input.day,
                    input.hour,
                    input.minuteOrZero(),
                    0
            );
            solar = lunar.getSolar();
        } else {
            solar = Solar.fromYmdHms(
                    input.year,
                    input.month,
                    input.day,
                    input.hour,
                    input.minuteOrZero(),
                    0
            );
        }

        EightChar localEightChar = solar.getLunar().getEightChar();
        localEightChar.setSect(2);
        // lunar-java publishes solar-term instants in China Standard Time. Convert
        // the entered Korean wall-clock time to the same instant in that zone for
        // year/month boundaries, while retaining Korean local time for day/hour.
        EightChar boundaryEightChar = toSolarTermReferenceTime(solar).getLunar().getEightChar();
        boundaryEightChar.setSect(2);

        Map<String, Integer> counts = emptyCounts();
        addWuXing(counts, boundaryEightChar.getYearWuXing());
        addWuXing(counts, boundaryEightChar.getMonthWuXing());
        addWuXing(counts, localEightChar.getDayWuXing());
        addWuXing(counts, localEightChar.getTimeWuXing());

        String calendarLabel = input.lunar
                ? (input.lunarLeapMonth ? "음력 윤달" : "음력 평달")
                : "양력";
        String inputSummary = String.format(
                Locale.KOREA,
                "%s %04d.%02d.%02d %02d:%02d",
                calendarLabel,
                input.year,
                input.month,
                input.day,
                input.hour,
                input.minuteOrZero()
        );
        String solarSummary = String.format(
                Locale.KOREA,
                "계산 기준 양력 %04d.%02d.%02d %02d:%02d",
                solar.getYear(),
                solar.getMonth(),
                solar.getDay(),
                solar.getHour(),
                solar.getMinute()
        );
        String pillars = "년주 " + boundaryEightChar.getYear()
                + " · 월주 " + boundaryEightChar.getMonth()
                + " · 일주 " + localEightChar.getDay()
                + " · 시주 " + localEightChar.getTime();

        return new SajuAnalysis(
                inputSummary,
                solarSummary,
                pillars,
                counts,
                "대한민국 표준시(Asia/Seoul)와 입춘·절기 교접 시각을 반영했으며, 야자시는 당일 일주 방식으로 계산했습니다."
        );
    }

    private Solar toSolarTermReferenceTime(Solar koreanSolar) {
        Calendar koreanTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        koreanTime.clear();
        koreanTime.set(
                koreanSolar.getYear(),
                koreanSolar.getMonth() - 1,
                koreanSolar.getDay(),
                koreanSolar.getHour(),
                koreanSolar.getMinute(),
                koreanSolar.getSecond()
        );
        Calendar referenceTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        referenceTime.setTimeInMillis(koreanTime.getTimeInMillis());
        return Solar.fromYmdHms(
                referenceTime.get(Calendar.YEAR),
                referenceTime.get(Calendar.MONTH) + 1,
                referenceTime.get(Calendar.DAY_OF_MONTH),
                referenceTime.get(Calendar.HOUR_OF_DAY),
                referenceTime.get(Calendar.MINUTE),
                referenceTime.get(Calendar.SECOND)
        );
    }

    private void validate(SajuInput input) {
        if (input == null) {
            throw new IllegalArgumentException("생년월일시가 없습니다.");
        }
        if (input.year < 1900 || input.year > 2100) {
            throw new IllegalArgumentException("지원하는 출생 연도는 1900년부터 2100년까지입니다.");
        }
        if (input.month < 1 || input.month > 12 || input.day < 1 || input.day > 31) {
            throw new IllegalArgumentException("생년월일을 다시 확인해 주세요.");
        }
        if (input.hour < 0 || input.hour > 23 || input.minuteOrZero() < 0 || input.minuteOrZero() > 59) {
            throw new IllegalArgumentException("태어난 시간을 다시 확인해 주세요.");
        }
    }

    private Map<String, Integer> emptyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String element : ELEMENTS) {
            counts.put(element, 0);
        }
        return counts;
    }

    private void addWuXing(Map<String, Integer> counts, String wuXing) {
        if (wuXing == null) return;
        for (int i = 0; i < wuXing.length(); i++) {
            String element = translateElement(wuXing.charAt(i));
            if (element != null) {
                counts.put(element, counts.get(element) + 1);
            }
        }
    }

    private String translateElement(char value) {
        switch (value) {
            case '木': return "목";
            case '火': return "화";
            case '土': return "토";
            case '金': return "금";
            case '水': return "수";
            default: return null;
        }
    }
}
