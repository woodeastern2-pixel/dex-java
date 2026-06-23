package com.lumora.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.lumora.app.data.AppDatabase;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.data.TaskEntity;
import com.lumora.app.engine.BriefingComposer;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                ReminderScheduler.rescheduleAll(context);
                return;
            case ReminderScheduler.ACTION_REMINDER:
                fireTask(context, intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1));
                return;
            case ReminderScheduler.ACTION_BRIEFING:
                fireBriefing(context, intent.getStringExtra(ReminderScheduler.EXTRA_BRIEFING_KIND));
                return;
            default:
        }
    }

    private void fireTask(Context ctx, long id) {
        if (id <= 0) return;
        LumoraRepository repo = new LumoraRepository(ctx);
        TaskEntity t = repo.database().taskDao().getById(id);
        if (t == null || TaskEntity.STATUS_DONE.equals(t.status)) return;
        LumoraSettings s = new LumoraSettings(ctx);
        if (ReminderScheduler.inQuietHours(s)) return;
        Notifier.show(ctx, (int) (id & 0x7fffffff),
                NotificationChannels.CH_REMINDER,
                "할 일 알림", t.title);
    }

    private void fireBriefing(Context ctx, String kind) {
        LumoraSettings s = new LumoraSettings(ctx);
        AppDatabase db = AppDatabase.get(ctx);
        BriefingComposer composer = new BriefingComposer(db, s);
        BriefingComposer.Kind k = "evening".equals(kind)
                ? BriefingComposer.Kind.EVENING
                : BriefingComposer.Kind.MORNING;
        if (k == BriefingComposer.Kind.MORNING && ReminderScheduler.inQuietHours(s)) return;
        String body = composer.compose(k);
        Notifier.show(ctx, "evening".equals(kind) ? 7102 : 7101,
                NotificationChannels.CH_BRIEFING,
                k == BriefingComposer.Kind.MORNING ? "오늘의 브리핑" : "오늘 정리",
                body);
    }
}
