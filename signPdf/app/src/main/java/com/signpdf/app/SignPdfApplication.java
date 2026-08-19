package com.signpdf.app;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.signpdf.app.util.CrashDiagnostics;

/**
 * Global SignPDF application setup: Korean-first app locale, crash diagnostics and
 * safe system-bar insets.
 */
public class SignPdfApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private boolean mDiagnosticShownThisProcess = false;

    @Override
    public void onCreate() {
        super.onCreate();
        CrashDiagnostics.install(this);
        AppLanguageManager.applySavedLanguage(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
        }

        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        final int initialLeft = content.getPaddingLeft();
        final int initialTop = content.getPaddingTop();
        final int initialRight = content.getPaddingRight();
        final int initialBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (!BuildConfig.DEBUG
            || mDiagnosticShownThisProcess
            || !(activity instanceof MainActivity)
            || !CrashDiagnostics.hasPendingPdfDiagnostic(activity)) {
            return;
        }

        mDiagnosticShownThisProcess = true;
        String summary = CrashDiagnostics.buildSummary(activity);

        new AlertDialog.Builder(activity)
            .setTitle("SignPDF PDF 오류 진단")
            .setMessage(summary)
            .setPositiveButton("확인", (dialog, which) ->
                CrashDiagnostics.dismiss(activity))
            .setNeutralButton("복사", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("SignPDF diagnostic", summary));
                    Toast.makeText(activity, "진단 정보가 복사되었습니다.", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
