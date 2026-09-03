package com.easternwood.ireumgil;

import android.app.Application;

import com.easternwood.ireumgil.data.HanjaAssetImporter;
import com.easternwood.ireumgil.data.HanjaDatabase;
import com.easternwood.ireumgil.monetization.RewardAccessStore;

public class IreumGilApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RewardAccessStore.initialize(this);
        HanjaDatabase db = HanjaDatabase.getInstance(this);
        new HanjaAssetImporter().importIfNeeded(this, db);
    }
}
