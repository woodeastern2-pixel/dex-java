package com.lumora.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.ui.MainActivity;

public class TodayWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.lumora.app.action.WIDGET_REFRESH";

    public static void requestRefresh(Context ctx) {
        Intent i = new Intent(ctx, TodayWidgetProvider.class);
        i.setAction(ACTION_REFRESH);
        ctx.sendBroadcast(i);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                onUpdate(context, mgr, ids);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        int count = 0;
        try {
            LumoraApplication app = (LumoraApplication) context.getApplicationContext();
            LumoraRepository repo = app.repository();
            count = repo.tasksToday() == null ? 0 : repo.tasksToday().size();
        } catch (Throwable ignored) { }

        for (int id : appWidgetIds) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_today);
            String label = count <= 0
                    ? context.getString(R.string.widget_today_empty)
                    : context.getString(R.string.widget_today_count, count);
            rv.setTextViewText(R.id.widgetCount, label);

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(context, id, open, flags);
            rv.setOnClickPendingIntent(R.id.widgetRoot, pi);

            manager.updateAppWidget(id, rv);
        }
    }
}
