package com.signpdf.app;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Applies safe system-bar insets to all SignPDF activities on Android 15+.
 *
 * targetSdk 35+ is edge-to-edge by default. SignPDF intentionally keeps its
 * document controls inside the safe area so toolbar and bottom editing controls
 * never sit underneath the status/navigation bars or display cutouts.
 */
public class SignPdfApplication extends Application implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();
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
            // The area exposed by padding uses SignPDF's light window backgrounds.
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

    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
