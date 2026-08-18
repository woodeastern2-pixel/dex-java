package com.lumora.app.notify;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.lumora.app.data.LumoraSettings;

/**
 * 포그라운드에서 짧게 노출하는 비서 토스트.
 * 사용자가 토스트 알림을 끈 경우 무시한다.
 */
public final class ToastBus {
    private ToastBus() {}

    public static void hint(Context ctx, String text) {
        if (text == null || text.isEmpty()) return;
        if (!new LumoraSettings(ctx).isToastEnabled()) return;
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx.getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    public static void info(Context ctx, String text) {
        if (text == null || text.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx.getApplicationContext(), text, Toast.LENGTH_LONG).show());
    }
}
