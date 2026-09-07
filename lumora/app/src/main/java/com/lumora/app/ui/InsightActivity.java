package com.lumora.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.collect.UsageStatsCollector;
import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.engine.BriefingComposer;
import com.lumora.app.engine.RoutineDetector;

import java.util.List;
import java.util.Locale;

public class InsightActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight);

        LumoraRepository repo = ((LumoraApplication) getApplication()).repository();
        LumoraSettings settings = new LumoraSettings(this);

        TextView completion = findViewById(R.id.completionValue);
        TextView screenTime = findViewById(R.id.screenTimeValue);
        TextView topApps = findViewById(R.id.topApps);
        TextView routines = findViewById(R.id.routines);
        TextView llmComment = findViewById(R.id.llmComment);
        TextView permNotice = findViewById(R.id.usagePermNotice);
        Button openPerm = findViewById(R.id.openUsagePerm);

        // 완료율 (오늘)
        long[] r = LumoraRepository.todayRange();
        int done = repo.database().taskDao().countDoneInRange(r[0], r[1]);
        int created = repo.database().taskDao().countCreatedInRange(r[0], r[1]);
        int totalDenominator = Math.max(1, done + repo.tasksToday().size());
        int pct = (int) Math.round(100.0 * done / totalDenominator);
        completion.setText(String.format(Locale.KOREA, "%d%%", pct));

        // 화면 사용 시간
        long since = System.currentTimeMillis() - 24L * 60 * 60 * 1000L;
        List<ContextLogEntity> logs = repo.logsSince(since);
        long screenMin = computeScreenOnMinutes(logs);
        screenTime.setText(getString(R.string.insight_screen_time_value,
                (int) (screenMin / 60), (int) (screenMin % 60)));

        // Usage Stats
        if (settings.isUsageConsented() && UsageStatsCollector.hasPermission(this)) {
            List<UsageStatsCollector.AppUsage> top = UsageStatsCollector.todayTop(this, 5);
            if (top.isEmpty()) {
                topApps.setText(R.string.insight_no_data);
            } else {
                StringBuilder sb = new StringBuilder();
                for (UsageStatsCollector.AppUsage u : top) {
                    long m = u.totalMs / 60000L;
                    sb.append("• ").append(shortPkg(u.pkg)).append(" — ")
                            .append(m / 60).append("h ").append(m % 60).append("m\n");
                }
                topApps.setText(sb.toString().trim());
            }
            permNotice.setVisibility(View.GONE);
            openPerm.setVisibility(View.GONE);
        } else {
            topApps.setText("");
            permNotice.setVisibility(View.VISIBLE);
            openPerm.setVisibility(View.VISIBLE);
            openPerm.setOnClickListener(v -> startActivity(UsageStatsCollector.settingsIntent()));
        }

        // 루틴 감지
        RoutineDetector detector = new RoutineDetector();
        List<ContextLogEntity> wide = repo.logsSince(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000L);
        List<RoutineDetector.Candidate> cands = detector.detect(wide);
        if (cands.isEmpty()) {
            routines.setText(R.string.insight_no_data);
        } else {
            StringBuilder sb = new StringBuilder();
            for (RoutineDetector.Candidate c : cands) {
                sb.append("• ").append(c.timeOfDay()).append(" — ")
                        .append(c.suggestedHabitName())
                        .append(" (").append(c.occurrences).append("회)\n");
            }
            routines.setText(sb.toString().trim());
        }

        // 한 줄 코멘트
        BriefingComposer composer = new BriefingComposer(repo.database(), settings);
        llmComment.setText(composer.composeInsightOneLiner(logs));
    }

    private static long computeScreenOnMinutes(List<ContextLogEntity> logs) {
        long total = 0;
        long lastOn = -1;
        for (ContextLogEntity e : logs) {
            if (ContextLogEntity.T_SCREEN_ON.equals(e.type)) lastOn = e.ts;
            else if (ContextLogEntity.T_SCREEN_OFF.equals(e.type) && lastOn > 0) {
                total += (e.ts - lastOn) / 60000L;
                lastOn = -1;
            }
        }
        if (lastOn > 0) total += (System.currentTimeMillis() - lastOn) / 60000L;
        return total;
    }

    private static String shortPkg(String p) {
        int dot = p.lastIndexOf('.');
        return dot >= 0 ? p.substring(dot + 1) : p;
    }
}
