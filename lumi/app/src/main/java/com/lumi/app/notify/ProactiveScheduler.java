package com.lumi.app.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.lumi.app.data.LumiSettings;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * AlarmManager 로 루미의 다음 "한마디" 시간을 무작위로 예약한다.
 * 사용자가 설정에서 켜두었을 때만 동작.
 *
 * - 기본 간격: 설정한 최소~최대 간격 사이 랜덤
 * - 하루 최소 메시지 수를 맞추기 위해 남은 시간 대비 간격을 약간 당길 수 있음
 * - 단, 직전 1시간 내에 사용자/루미가 메시지를 주고받았다면 한 번 건너뜀(중복 알림 방지)
 * - 23:00 ~ 익일 08:00 사이에는 발송하지 않고, 다음 날 아침으로 미룸
 */
public final class ProactiveScheduler {

    public static final String ACTION_TICK = "com.lumi.app.action.PROACTIVE_TICK";
    private static final int REQUEST_CODE = 0x4C5549; // 'LUI'

    private static final int QUIET_START_HOUR = 23;
    private static final int QUIET_END_HOUR = 8;
    private static final TimeZone SEOUL_TZ = TimeZone.getTimeZone("Asia/Seoul");

    private ProactiveScheduler() {}

    public static void scheduleNext(Context ctx, LumiSettings settings) {
        cancel(ctx);
        if (settings != null && !settings.isProactiveEnabled()) return;

        long triggerAt = pickNextTriggerTime(System.currentTimeMillis(), new Random(), settings);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(ctx);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException ignored) {
            // 일부 OEM 에서 정확 알람 권한 거부 시 조용히 무시
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(ctx));
    }

    private static PendingIntent buildPendingIntent(Context ctx) {
        Intent i = new Intent(ctx, ProactiveReceiver.class);
        i.setAction(ACTION_TICK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, i, flags);
    }

    static long pickNextTriggerTime(long nowMs, Random rng, LumiSettings settings) {
        int minGap = settings != null ? settings.getProactiveIntervalMinMinutes() : 90;
        int maxGap = settings != null ? settings.getProactiveIntervalMaxMinutes() : 240;
        if (maxGap < minGap) maxGap = minGap;

        int gapMin = minGap + rng.nextInt(maxGap - minGap + 1);

        // 하루 최소 목표치가 아직 남았으면 남은 시간 대비 간격 상한을 낮춰 빈도를 보정한다.
        if (settings != null) {
            int target = settings.getDailyProactiveMinimum();
            int sent = settings.getTodayProactiveSentCount();
            int remain = Math.max(0, target - sent);
            if (remain > 0) {
                long quietStartMs = nextQuietStart(nowMs);
                long remainMinutes = Math.max(30L, (quietStartMs - nowMs) / (60L * 1000L));
                int paceMax = (int) Math.max(minGap, remainMinutes / remain);
                int adjustedMax = Math.min(maxGap, paceMax);
                if (adjustedMax >= minGap) {
                    gapMin = minGap + rng.nextInt(adjustedMax - minGap + 1);
                }
            }
        }

        long t = nowMs + gapMin * 60L * 1000L;
        Calendar c = Calendar.getInstance(SEOUL_TZ);
        c.setTimeInMillis(t);
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= QUIET_START_HOUR || h < QUIET_END_HOUR) {
            // 다음 날 아침 8시 ~ 10시 사이로 이동
            if (h >= QUIET_START_HOUR) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            c.set(Calendar.HOUR_OF_DAY, QUIET_END_HOUR);
            c.set(Calendar.MINUTE, rng.nextInt(120));
            c.set(Calendar.SECOND, 0);
            t = c.getTimeInMillis();
        }
        return t;
    }

    private static long nextQuietStart(long nowMs) {
        Calendar c = Calendar.getInstance(SEOUL_TZ);
        c.setTimeInMillis(nowMs);
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= QUIET_START_HOUR) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        c.set(Calendar.HOUR_OF_DAY, QUIET_START_HOUR);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
