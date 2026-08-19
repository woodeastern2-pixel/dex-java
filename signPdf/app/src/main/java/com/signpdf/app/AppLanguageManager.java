package com.signpdf.app;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * SignPDF app-language controller.
 *
 * Korean is intentionally the first-run default regardless of the device locale.
 * Users can switch to English from Settings; the choice is persisted locally.
 */
public final class AppLanguageManager {

    private static final String PREFS = "signpdf_locale";
    private static final String KEY_LANGUAGE = "language";
    public static final String KOREAN = "ko";
    public static final String ENGLISH = "en";

    private AppLanguageManager() { }

    public static void applySavedLanguage(Context context) {
        String language = getLanguage(context);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
    }

    public static String getLanguage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, KOREAN);
    }

    public static void setLanguage(Context context, String language) {
        String safeLanguage = ENGLISH.equals(language) ? ENGLISH : KOREAN;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, safeLanguage)
            .apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(safeLanguage));
    }
}
