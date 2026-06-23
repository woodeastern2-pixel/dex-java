package com.lumi.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.lumi.app.LumiApplication;
import com.lumi.app.data.CharacterRepository;
import com.lumi.app.data.LumiSettings;
import com.lumi.app.model.ConversationMessage;

import java.util.concurrent.Executors;

public class ProactiveReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (intent == null) {
            ProactiveScheduler.scheduleNext(app, ((LumiApplication) app).getSettings());
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // 부팅/업데이트 후 재예약
            ProactiveScheduler.scheduleNext(app, ((LumiApplication) app).getSettings());
            return;
        }
        // 일반 tick: 백그라운드에서 메시지 생성 + 알림 + 다음 예약
        final PendingResult pr = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                LumiApplication la = (LumiApplication) app;
                LumiSettings settings = la.getSettings();
                CharacterRepository repo = la.getRepository();

                if (settings == null || !settings.isProactiveEnabled()) {
                    return;
                }
                long now = System.currentTimeMillis();
                long lastTs = repo.lastInteractionTimestamp();
                // 최근 30분 이내 활동했다면 침묵
                if (lastTs > 0 && now - lastTs < 30L * 60L * 1000L) {
                    return;
                }
                ConversationMessage msg = repo.createProactiveMessage();
                settings.recordProactiveSentNow();
                new Handler(Looper.getMainLooper()).post(() -> {
                    LumiNotifier.post(app, msg.content);
                });
            } catch (Throwable ignored) {
            } finally {
                ProactiveScheduler.scheduleNext(app, ((LumiApplication) app).getSettings());
                pr.finish();
            }
        });
    }
}
