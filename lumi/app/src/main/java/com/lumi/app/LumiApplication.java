package com.lumi.app;

import android.app.Application;

import com.lumi.app.data.CharacterRepository;
import com.lumi.app.data.LumiDatabase;
import com.lumi.app.data.LumiSettings;
import com.lumi.app.notify.LumiNotifier;
import com.lumi.app.notify.ProactiveScheduler;

public class LumiApplication extends Application {
    private LumiDatabase database;
    private CharacterRepository repository;
    private LumiSettings settings;

    @Override
    public void onCreate() {
        super.onCreate();
        database = LumiDatabase.getInstance(this);
        settings = new LumiSettings(this);
        repository = new CharacterRepository(database.characterDao());
        repository.setSettings(settings);
        LumiNotifier.ensureChannel(this);
        ProactiveScheduler.scheduleNext(this, settings);
    }

    public CharacterRepository getRepository() {
        return repository;
    }

    public LumiSettings getSettings() {
        return settings;
    }
}