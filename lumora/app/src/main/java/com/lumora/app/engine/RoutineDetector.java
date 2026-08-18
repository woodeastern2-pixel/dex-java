package com.lumora.app.engine;

import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.HabitEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 비슷한 시간대에 반복하는 행동(예: 매일 23시 화면 OFF, 매일 07:30 화면 ON)을 보고
 * 습관 후보를 제안한다.
 *
 * 단순 휴리스틱: 최근 14일간 같은 type 의 이벤트가 비슷한 시각(±30분)에 7번 이상 반복되면 후보.
 */
public class RoutineDetector {

    public static class Candidate {
        public final String type;
        public final int hour;
        public final int minute;
        public final int occurrences;

        public Candidate(String type, int hour, int minute, int occurrences) {
            this.type = type;
            this.hour = hour;
            this.minute = minute;
            this.occurrences = occurrences;
        }

        public String suggestedHabitName() {
            switch (type) {
                case ContextLogEntity.T_USER_PRESENT: return "기상 후 첫 점검";
                case ContextLogEntity.T_SCREEN_OFF: return "취침 준비";
                case ContextLogEntity.T_POWER_CONNECTED: return "충전 시작 시 정리";
                case ContextLogEntity.T_HABIT_CHECK: return "습관 체크";
                default: return "루틴";
            }
        }

        public String timeOfDay() {
            return String.format(java.util.Locale.KOREA, "%02d:%02d", hour, minute);
        }
    }

    public List<Candidate> detect(List<ContextLogEntity> events) {
        Map<String, int[]> bucket = new HashMap<>(); // key: type|hourBucket → [count, sumMin]
        for (ContextLogEntity e : events) {
            if (!isInteresting(e.type)) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(e.ts);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);
            String key = e.type + "|" + hour;
            int[] v = bucket.get(key);
            if (v == null) { v = new int[]{0, 0}; bucket.put(key, v); }
            v[0]++;
            v[1] += minute;
        }
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, int[]> en : bucket.entrySet()) {
            if (en.getValue()[0] >= 7) {
                String[] parts = en.getKey().split("\\|");
                int hour = Integer.parseInt(parts[1]);
                int avgMin = en.getValue()[1] / en.getValue()[0];
                out.add(new Candidate(parts[0], hour, avgMin, en.getValue()[0]));
            }
        }
        return out;
    }

    private boolean isInteresting(String type) {
        return ContextLogEntity.T_USER_PRESENT.equals(type)
                || ContextLogEntity.T_SCREEN_OFF.equals(type)
                || ContextLogEntity.T_POWER_CONNECTED.equals(type);
    }
}
