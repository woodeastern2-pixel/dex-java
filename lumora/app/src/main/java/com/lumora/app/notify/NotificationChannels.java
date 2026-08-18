package com.lumora.app.notify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.lumora.app.R;

public final class NotificationChannels {

    public static final String CH_REMINDER = "lumora_reminder";
    public static final String CH_BRIEFING = "lumora_briefing";
    public static final String CH_NUDGE = "lumora_nudge";

    private NotificationChannels() {}

    public static void ensure(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                CH_REMINDER, ctx.getString(R.string.notif_channel_reminder),
                NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CH_BRIEFING, ctx.getString(R.string.notif_channel_briefing),
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CH_NUDGE, ctx.getString(R.string.notif_channel_nudge),
                NotificationManager.IMPORTANCE_LOW));
    }
}
