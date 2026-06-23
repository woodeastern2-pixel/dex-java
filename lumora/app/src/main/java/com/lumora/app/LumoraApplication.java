package com.lumora.app;

import android.app.Application;

import com.lumora.app.collect.ScreenEventReceiver;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.notify.NotificationChannels;
import com.lumora.app.notify.ReminderScheduler;

public class LumoraApplication extends Application {

    private LumoraRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new LumoraRepository(this);
        NotificationChannels.ensure(this);

        // 화면/충전 이벤트 동적 등록 (SCREEN_ON/OFF는 manifest 등록 불가)
        try { ScreenEventReceiver.register(this); } catch (Throwable ignored) {}

        // 동의된 사용자만 스케줄링 (최초 동의 후 MainActivity 가 다시 호출)
        if (new LumoraSettings(this).isLegalAccepted()) {
            ReminderScheduler.scheduleBriefings(this);
        }

        // 30일 이전 로그 자동 삭제
        try { repository.purgeOldLogs(30L * 24 * 60 * 60 * 1000L); } catch (Throwable ignored) {}
    }

    public LumoraRepository repository() { return repository; }
}
