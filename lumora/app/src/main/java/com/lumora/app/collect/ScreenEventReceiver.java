package com.lumora.app.collect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;

/**
 * 화면 ON/OFF, USER_PRESENT, 충전 시작/종료를 단말 내부에 기록.
 * 권한 불필요. 동의/토글 OFF 시 기록을 건너뛴다.
 *
 * Manifest 등록은 안전상 제한적이라(SCREEN_ON/OFF 는 매니페스트로 못 받음),
 * Application 시작 시 동적으로 registerReceiver 한다.
 */
public class ScreenEventReceiver extends BroadcastReceiver {

    public static void register(Context appCtx) {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        appCtx.registerReceiver(new ScreenEventReceiver(), f);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LumoraSettings s = new LumoraSettings(context);
        if (!s.isCollectScreen()) return;
        if (intent == null || intent.getAction() == null) return;
        String type;
        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_ON: type = ContextLogEntity.T_SCREEN_ON; break;
            case Intent.ACTION_SCREEN_OFF: type = ContextLogEntity.T_SCREEN_OFF; break;
            case Intent.ACTION_USER_PRESENT: type = ContextLogEntity.T_USER_PRESENT; break;
            case Intent.ACTION_POWER_CONNECTED: type = ContextLogEntity.T_POWER_CONNECTED; break;
            case Intent.ACTION_POWER_DISCONNECTED: type = ContextLogEntity.T_POWER_DISCONNECTED; break;
            default: return;
        }
        new LumoraRepository(context).log(type, null);
    }
}
