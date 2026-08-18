package com.ireumgil;

import android.app.Application;

import com.ireumgil.data.HanjaAssetImporter;
import com.ireumgil.data.HanjaDatabase;

public class IreumGilApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HanjaDatabase db = HanjaDatabase.getInstance(this);
        new HanjaAssetImporter().importIfNeeded(this, db);
    }
}
