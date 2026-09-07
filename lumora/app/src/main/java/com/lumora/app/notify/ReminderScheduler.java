package com.lumora.app.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.data.TaskEntity;

import java.util.Calendar;
import java.util.List;

/**
 * - 각 Task 의 dueAt 시각에 정시 알림
 * - 매일 아침/저녁 브리핑 정시 알림
 * - 부팅/업데이트 시 BOOT_COMPLETED 받으면 ReminderReceiver 가 rescheduleAll 호출
 */
public class ReminderScheduler {

    public static final String ACTION_REMINDER = "com.lumora.app.action.REMINDER_TICK";
    public static final String ACTION_BRIEFING = "com.lumora.app.action.BRIEFING_TICK";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_BRIEFING_KIND = "kind"; // morning/evening

    public static void scheduleTask(Context ctx, TaskEntity t) {
        if (t.dueAt <= 0 || t.dueAt < System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, ReminderReceiver.class).setAction(ACTION_REMINDER);
        i.putExtra(EXTRA_TASK_ID, t.id);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (t.id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, t.dueAt, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.dueAt, pi);
            }
        } catch (SecurityException ignored) {
            am.set(AlarmManager.RTC_WAKEUP, t.dueAt, pi);
        }
    }

    public static void cancelTask(Context ctx, long taskId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, ReminderReceiver.class).setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (taskId & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    public static void scheduleBriefings(Context ctx) {
        LumoraSettings s = new LumoraSettings(ctx);
        scheduleBriefingAt(ctx, s.getMorningTime(), "morning");
        scheduleBriefingAt(ctx, s.getEveningTime(), "evening");
    }

    private static void scheduleBriefingAt(Context ctx, String hhmm, String kind) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long when = nextOccurrence(hhmm);
        Intent i = new Intent(ctx, ReminderReceiver.class).setAction(ACTION_BRIEFING);
        i.putExtra(EXTRA_BRIEFING_KIND, kind);
        int code = "morning".equals(kind) ? 9001 : 9002;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setRepeating(AlarmManager.RTC_WAKEUP, when, AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
            am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static long nextOccurrence(String hhmm) {
        int hour = 7, min = 30;
        try {
            String[] p = hhmm.split(":");
            hour = Integer.parseInt(p[0]);
            min = Integer.parseInt(p[1]);
        } catch (Exception ignored) {}
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTimeInMillis();
    }

    public static void rescheduleAll(Context ctx) {
        LumoraRepository repo = new LumoraRepository(ctx);
        scheduleBriefings(ctx);
        List<TaskEntity> upcoming = repo.tasksUpcoming();
        for (TaskEntity t : upcoming) scheduleTask(ctx, t);
    }

    public static boolean inQuietHours(LumoraSettings s) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int qs = s.getQuietStartHour();
        int qe = s.getQuietEndHour();
        if (qs == qe) return false;
        if (qs < qe) return h >= qs && h < qe;
        return h >= qs || h < qe;
    }
}
