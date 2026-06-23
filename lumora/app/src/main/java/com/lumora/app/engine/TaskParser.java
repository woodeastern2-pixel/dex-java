package com.lumora.app.engine;

import androidx.annotation.NonNull;

import com.lumora.app.data.TaskEntity;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자가 입력한 한 줄을 TaskEntity 로 변환한다.
 * LLM 없이도 잘 동작하도록 정규식 기반 한국어 시간 파서를 내장.
 *
 * 처리 가능한 표현 예시:
 *   "내일 3시에 약속"           → 다음날 15:00 (오후 추정)
 *   "오늘 오전 11시 회의"       → 오늘 11:00
 *   "월요일 9시 운동"           → 다가오는 월요일 09:00
 *   "10분 뒤 알람"              → 현재+10분
 *   "30일 9시 결제 확인"        → 이번/다음달 30일 09:00
 *   "매일 8시 약 먹기"          → repeat=DAILY, 08:00
 */
public class TaskParser {

    private static final Pattern P_REL_MIN = Pattern.compile("(\\d{1,3})\\s*분\\s*(뒤|후)");
    private static final Pattern P_REL_HOUR = Pattern.compile("(\\d{1,2})\\s*시간\\s*(뒤|후)");
    private static final Pattern P_TIME = Pattern.compile("(오전|오후|아침|점심|저녁|밤|새벽)?\\s*(\\d{1,2})(?::|\\s*시)\\s*(?:(\\d{1,2})\\s*분?)?");
    private static final Pattern P_DAY_OF_MONTH = Pattern.compile("(\\d{1,2})\\s*일");

    @NonNull
    public TaskEntity parse(@NonNull String raw) {
        TaskEntity t = new TaskEntity();
        t.createdAt = System.currentTimeMillis();
        String s = raw.trim();
        if (s.isEmpty()) {
            t.title = raw;
            return t;
        }

        // 반복
        if (s.contains("매일")) t.repeatRule = TaskEntity.REPEAT_DAILY;
        else if (s.contains("평일")) t.repeatRule = TaskEntity.REPEAT_WEEKDAYS;
        else if (s.contains("매주")) t.repeatRule = TaskEntity.REPEAT_WEEKLY;
        else if (s.contains("매월") || s.contains("매달")) t.repeatRule = TaskEntity.REPEAT_MONTHLY;

        Calendar cal = Calendar.getInstance();
        boolean hasDate = false;
        boolean hasTime = false;

        // 1) 상대 분/시간
        Matcher m = P_REL_MIN.matcher(s);
        if (m.find()) {
            cal.add(Calendar.MINUTE, Integer.parseInt(m.group(1)));
            hasDate = hasTime = true;
            t.dueAt = cal.getTimeInMillis();
            t.title = stripScheduleTokens(s);
            return t;
        }
        m = P_REL_HOUR.matcher(s);
        if (m.find()) {
            cal.add(Calendar.HOUR_OF_DAY, Integer.parseInt(m.group(1)));
            hasDate = hasTime = true;
            t.dueAt = cal.getTimeInMillis();
            t.title = stripScheduleTokens(s);
            return t;
        }

        // 2) 날짜 키워드
        if (s.contains("내일")) { cal.add(Calendar.DAY_OF_MONTH, 1); hasDate = true; }
        else if (s.contains("모레")) { cal.add(Calendar.DAY_OF_MONTH, 2); hasDate = true; }
        else if (s.contains("오늘")) { hasDate = true; }
        else {
            int dow = parseWeekday(s);
            if (dow > 0) {
                cal = nextWeekdayFromNow(dow);
                hasDate = true;
            } else {
                Matcher dm = P_DAY_OF_MONTH.matcher(s);
                if (dm.find() && !s.contains("매일") && !s.contains("매달")) {
                    int day = Integer.parseInt(dm.group(1));
                    if (day >= 1 && day <= 31) {
                        Calendar c2 = Calendar.getInstance();
                        c2.set(Calendar.DAY_OF_MONTH, Math.min(day, c2.getActualMaximum(Calendar.DAY_OF_MONTH)));
                        if (c2.getTimeInMillis() < System.currentTimeMillis()) {
                            c2.add(Calendar.MONTH, 1);
                            c2.set(Calendar.DAY_OF_MONTH, Math.min(day, c2.getActualMaximum(Calendar.DAY_OF_MONTH)));
                        }
                        cal = c2;
                        hasDate = true;
                    }
                }
            }
        }

        // 3) 시각
        Matcher tm = P_TIME.matcher(s);
        if (tm.find()) {
            String period = tm.group(1);
            int hour = Integer.parseInt(tm.group(2));
            int min = tm.group(3) == null ? 0 : Integer.parseInt(tm.group(3));
            hour = applyPeriod(period, hour);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            hasTime = true;
            // 시각만 있고 날짜가 없는데 이미 지난 시각이면 내일로
            if (!hasDate && cal.getTimeInMillis() < System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        if (hasTime || hasDate) {
            if (!hasTime) {
                // 날짜만 있을 때 기본 9시
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }
            t.dueAt = cal.getTimeInMillis();
        }

        t.title = stripScheduleTokens(s);
        if (t.title.isEmpty()) t.title = raw;
        return t;
    }

    private static int applyPeriod(String period, int hour) {
        if (period == null) {
            // 1~7시는 보통 오후(13~19)일 가능성이 더 높지만, 사용자 명시 없으면 24h 그대로
            return hour;
        }
        switch (period) {
            case "오후":
            case "저녁":
                return hour < 12 ? hour + 12 : hour;
            case "밤":
                return hour < 12 ? hour + 12 : hour;
            case "새벽":
            case "오전":
            case "아침":
                return hour == 12 ? 0 : hour;
            case "점심":
                return hour < 12 ? hour + 12 : hour;
            default:
                return hour;
        }
    }

    private static int parseWeekday(String s) {
        if (s.contains("월요일")) return Calendar.MONDAY;
        if (s.contains("화요일")) return Calendar.TUESDAY;
        if (s.contains("수요일")) return Calendar.WEDNESDAY;
        if (s.contains("목요일")) return Calendar.THURSDAY;
        if (s.contains("금요일")) return Calendar.FRIDAY;
        if (s.contains("토요일")) return Calendar.SATURDAY;
        if (s.contains("일요일")) return Calendar.SUNDAY;
        return -1;
    }

    private static Calendar nextWeekdayFromNow(int targetDow) {
        Calendar c = Calendar.getInstance();
        int today = c.get(Calendar.DAY_OF_WEEK);
        int diff = (targetDow - today + 7) % 7;
        if (diff == 0) diff = 7;
        c.add(Calendar.DAY_OF_MONTH, diff);
        return c;
    }

    private static String stripScheduleTokens(String s) {
        String r = s
                .replaceAll("(매일|매주|매달|매월|평일|오늘|내일|모레)", "")
                .replaceAll("(월|화|수|목|금|토|일)요일", "")
                .replaceAll("(오전|오후|아침|점심|저녁|밤|새벽)", "")
                .replaceAll("\\d{1,3}\\s*분\\s*(뒤|후)", "")
                .replaceAll("\\d{1,2}\\s*시간\\s*(뒤|후)", "")
            .replaceAll("\\d{1,2}(?::|\\s*시)\\s*(?:\\d{1,2}\\s*분?)?", "")
                .replaceAll("\\d{1,2}\\s*일", "")
            .replaceAll("(일정\\s*(추가|넣어|등록|기록)\\s*해\\s*줘|일정\\s*(추가|넣어|등록|기록)\\s*해주세요)", "")
            .replaceAll("(추가|등록|기록|정리)\\s*해\\s*줘", "")
            .replaceAll("(추가|등록|기록|정리)\\s*해\\s*주세요", "")
            .replaceAll("^\\s*[은는이가을를에로와과도만]?\\s+", "")
            .replaceAll("[에는을를이가도의]\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return r;
    }

    /** "5월 9일 14:30" 형태로 사람이 읽기 쉬운 시각. */
    public static String formatDue(long ts) {
        if (ts <= 0) return "";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return String.format(Locale.KOREA, "%d월 %d일 %02d:%02d",
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE));
    }
}
