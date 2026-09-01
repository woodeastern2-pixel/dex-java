package com.signpdf.app.util;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Small on-device crash breadcrumb recorder for the current SignPDF debug cycle.
 * It intentionally stores no document contents or passwords.
 */
public final class CrashDiagnostics {

    private static final String PREFS = "signpdf_crash_diagnostics";
    private static final String KEY_STAGE = "stage";
    private static final String KEY_CRASH = "crash";
    private static final String KEY_TIME = "time";

    private static Thread.UncaughtExceptionHandler sPreviousHandler;

    private CrashDiagnostics() {
    }

    public static void install(Application application) {
        if (sPreviousHandler != null) return;

        sPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                recordCrash(application, thread, throwable);
            } catch (Throwable ignored) {
            }

            if (sPreviousHandler != null) {
                sPreviousHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public static void mark(Context context, String stage) {
        if (context == null) return;
        // commit() is intentional here: these breadcrumbs must survive an immediate
        // process crash before an asynchronous SharedPreferences write could complete.
        prefs(context).edit()
            .putString(KEY_STAGE, stage == null ? "" : stage)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .commit();
    }

    public static void completed(Context context) {
        if (context == null) return;
        prefs(context).edit()
            .putString(KEY_STAGE, "")
            .remove(KEY_CRASH)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .commit();
    }

    public static boolean hasPendingPdfDiagnostic(Context context) {
        SharedPreferences prefs = prefs(context);
        String stage = prefs.getString(KEY_STAGE, "");
        if (stage == null || !stage.startsWith("pdf:")) return false;

        String javaCrash = prefs.getString(KEY_CRASH, "");
        if (javaCrash != null && !javaCrash.trim().isEmpty()) return true;

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && wasLastExitAbnormalApi30(context);
    }

    public static String buildSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        String stage = prefs.getString(KEY_STAGE, "unknown");
        String crash = prefs.getString(KEY_CRASH, "");

        StringBuilder out = new StringBuilder();
        out.append("마지막 PDF 처리 단계: ").append(stage == null ? "unknown" : stage);

        if (crash != null && !crash.trim().isEmpty()) {
            out.append("\n\nJava 오류:\n").append(crash);
        }

        String exit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? getLastExitReasonApi30(context)
            : "";
        if (!exit.isEmpty()) {
            out.append("\n\n이전 프로세스 종료 정보:\n").append(exit);
        }
        return out.toString();
    }

    public static void dismiss(Context context) {
        prefs(context).edit().remove(KEY_CRASH).putString(KEY_STAGE, "").apply();
    }

    private static void recordCrash(Context context, Thread thread, Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String stack = writer.toString();
        if (stack.length() > 5000) stack = stack.substring(0, 5000);

        String value = "thread=" + thread.getName() + "\n" + stack;
        prefs(context).edit()
            .putString(KEY_CRASH, value)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .commit();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static boolean wasLastExitAbnormalApi30(Context context) {
        ApplicationExitInfo info = getLastExitInfoApi30(context);
        if (info == null) return false;
        int reason = info.getReason();
        return reason == ApplicationExitInfo.REASON_CRASH
            || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
            || reason == ApplicationExitInfo.REASON_ANR
            || reason == ApplicationExitInfo.REASON_LOW_MEMORY;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String getLastExitReasonApi30(Context context) {
        ApplicationExitInfo info = getLastExitInfoApi30(context);
        if (info == null) return "";
        String description = info.getDescription();
        return "reason=" + info.getReason()
            + ", status=" + info.getStatus()
            + (description == null || description.isEmpty()
                ? "" : ", description=" + description);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static ApplicationExitInfo getLastExitInfoApi30(Context context) {
        try {
            ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return null;

            List<ApplicationExitInfo> infos = manager.getHistoricalProcessExitReasons(
                context.getPackageName(), 0, 3);
            if (infos == null || infos.isEmpty()) return null;
            return infos.get(0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
