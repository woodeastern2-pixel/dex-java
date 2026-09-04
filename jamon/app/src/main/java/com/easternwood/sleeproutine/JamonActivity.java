package com.easternwood.sleeproutine;

import android.app.Activity;
import android.content.Context;

/* JADX INFO: loaded from: classes.dex */
abstract class JamonActivity extends Activity {
    JamonActivity() {
    }

    @Override // android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LocaleHelper.wrap(context));
    }
}
