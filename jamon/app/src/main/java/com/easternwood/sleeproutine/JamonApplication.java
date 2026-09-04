package com.easternwood.sleeproutine;

import android.app.Application;
import android.content.Context;

/* JADX INFO: loaded from: classes.dex */
public class JamonApplication extends Application {
    @Override // android.content.ContextWrapper
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LocaleHelper.wrap(context));
    }
}
