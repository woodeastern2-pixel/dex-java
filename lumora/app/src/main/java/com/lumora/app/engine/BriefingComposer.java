package com.lumora.app.engine;

import com.lumora.app.data.AppDatabase;
import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.HabitEntity;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.data.TaskEntity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 아침/저녁 브리핑 한 줄 텍스트 생성기.
 * LLM 미사용 시 템플릿으로 충분한 메시지를 만든다.
 */
public class BriefingComposer {

    public enum Kind { MORNING, EVENING, NUDGE }

    private final AppDatabase db;
    private final LumoraSettings settings;

    public BriefingComposer(AppDatabase db, LumoraSettings settings) {
        this.db = db;
        this.settings = settings;
    }

    public String compose(Kind kind) {
        long[] range = todayRange();
        int doneToday = db.taskDao().countDoneInRange(range[0], range[1]);
        List<TaskEntity> today = db.taskDao().today(range[0], range[1]);
        int habitsTotal = db.habitDao().all().size();
        String name = settings.getUserName();
        String displayName = name == null ? "" : name.trim();
        String addr = displayName.isEmpty() ? "" : displayName + "님, ";

        switch (kind) {
            case MORNING: {
                StringBuilder sb = new StringBuilder();
                sb.append(salutationForNow()).append(displayName.isEmpty() ? "" : ", " + displayName + "님").append(". ");
                if (today.isEmpty()) {
                    sb.append("오늘은 잡힌 일이 없어요. 가볍게 시작해요.");
                } else {
                    sb.append("오늘 할 일 ").append(today.size()).append("건이 있어요");
                    TaskEntity first = today.get(0);
                    if (first.dueAt > 0) {
                        sb.append(". 첫 일정은 ").append(TaskParser.formatDue(first.dueAt))
                                .append(" — ").append(first.title);
                    } else {
                        sb.append(". 먼저 할 일: ").append(first.title);
                    }
                }
                if (habitsTotal > 0) {
                    sb.append(" · 오늘 챙길 습관 ").append(habitsTotal).append("개도 있어요.");
                }
                return sb.toString();
            }
            case EVENING: {
                int created = db.taskDao().countCreatedInRange(range[0], range[1]);
                StringBuilder sb = new StringBuilder();
                sb.append(addr).append("오늘 ").append(doneToday).append("건 마무리했어요.");
                int leftover = today.size();
                if (leftover > 0) {
                    sb.append(" 남은 ").append(leftover).append("건은 내일로 넘길까요?");
                }
                if (created == 0 && doneToday == 0) {
                    sb.append(" 푹 쉬는 것도 좋은 선택이에요.");
                }
                return sb.toString();
            }
            case NUDGE:
            default: {
                if (today.isEmpty()) return addr + "지금은 보류된 일이 없어요.";
                TaskEntity nearest = nearestDue(today);
                if (nearest != null && nearest.dueAt > 0) {
                    long mins = (nearest.dueAt - System.currentTimeMillis()) / 60000L;
                    if (mins >= 0 && mins <= 120) {
                        return String.format(Locale.KOREA, "%s잠깐만요 — %d분 후 \"%s\" 예정이에요.",
                                addr, mins, nearest.title);
                    }
                }
                return addr + "오늘 남은 일 " + today.size() + "건이 있어요.";
            }
        }
    }

    private String salutationForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return "아직 많이 늦었어요";
        if (hour < 11) return "좋은 아침이에요";
        if (hour < 17) return "좋은 오후예요";
        if (hour < 22) return "좋은 저녁이에요";
        return "늦은 시간이에요";
    }

    private TaskEntity nearestDue(List<TaskEntity> tasks) {
        long now = System.currentTimeMillis();
        TaskEntity best = null;
        long bestDelta = Long.MAX_VALUE;
        for (TaskEntity t : tasks) {
            if (t.dueAt <= 0) continue;
            long d = Math.abs(t.dueAt - now);
            if (d < bestDelta) { bestDelta = d; best = t; }
        }
        return best;
    }

    public static long[] todayRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        long end = c.getTimeInMillis();
        return new long[]{start, end};
    }

    /** 컨텍스트 로그를 보고 짧은 인사이트 한 줄을 만든다. */
    public String composeInsightOneLiner(List<ContextLogEntity> recent) {
        long now = System.currentTimeMillis();
        long screenOnMin = 0;
        long lastOn = -1;
        for (ContextLogEntity e : recent) {
            if (ContextLogEntity.T_SCREEN_ON.equals(e.type)) {
                lastOn = e.ts;
            } else if (ContextLogEntity.T_SCREEN_OFF.equals(e.type) && lastOn > 0) {
                screenOnMin += (e.ts - lastOn) / 60000L;
                lastOn = -1;
            }
        }
        if (lastOn > 0) screenOnMin += (now - lastOn) / 60000L;

        if (screenOnMin <= 0) return "최근 활동 데이터가 부족해요.";
        long h = screenOnMin / 60;
        long m = screenOnMin % 60;
        return String.format(Locale.KOREA, "최근 화면을 켜둔 시간은 약 %d시간 %d분이에요.", h, m);
    }
}
