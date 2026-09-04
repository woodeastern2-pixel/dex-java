package com.easternwood.sleeproutine;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
final class LocaleHelper {
    private LocaleHelper() {
    }

    static Context wrap(Context context) {
        Locale locale = new Locale(Prefs.getLanguage(context));
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }
}
