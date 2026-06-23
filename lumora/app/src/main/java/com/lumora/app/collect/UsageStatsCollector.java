package com.lumora.app.collect;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 앱 사용 통계(특수 권한)를 단말 내에서 읽어 인사이트에만 사용한다.
 * 외부 전송 없음. 사용자가 권한 부여 + 동의 토글 ON 일 때만 동작.
 */
public class UsageStatsCollector {

    public static class AppUsage {
        public final String pkg;
        public final long totalMs;
        public AppUsage(String pkg, long totalMs) { this.pkg = pkg; this.totalMs = totalMs; }
    }

    public static boolean hasPermission(Context ctx) {
        try {
            AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static android.content.Intent settingsIntent() {
        return new android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /** 오늘 0시 ~ 현재까지의 앱별 합계 사용시간 Top N */
    public static List<AppUsage> todayTop(Context ctx, int topN) {
        List<AppUsage> out = new ArrayList<>();
        if (!hasPermission(ctx)) return out;
        UsageStatsManager m = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        if (m == null) return out;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        long end = System.currentTimeMillis();

        List<UsageStats> stats = m.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
        if (stats == null) return out;
        for (UsageStats us : stats) {
            if (us.getTotalTimeInForeground() <= 0) continue;
            out.add(new AppUsage(us.getPackageName(), us.getTotalTimeInForeground()));
        }
        Collections.sort(out, new Comparator<AppUsage>() {
            @Override public int compare(AppUsage a, AppUsage b) {
                return Long.compare(b.totalMs, a.totalMs);
            }
        });
        if (out.size() > topN) return out.subList(0, topN);
        return out;
    }
}
