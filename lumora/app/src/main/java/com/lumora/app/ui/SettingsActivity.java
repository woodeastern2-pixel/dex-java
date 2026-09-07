package com.lumora.app.ui;

import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.collect.UsageStatsCollector;
import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.HabitEntity;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.data.TaskEntity;
import com.lumora.app.notify.ReminderScheduler;
import com.lumora.app.notify.ToastBus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

public class SettingsActivity extends AppCompatActivity {

    private static final String[] PROVIDERS = new String[]{
            LumoraSettings.PROVIDER_OPENAI,
            LumoraSettings.PROVIDER_ANTHROPIC,
            LumoraSettings.PROVIDER_GEMINI,
            LumoraSettings.PROVIDER_OPENAI_COMPATIBLE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        LumoraSettings s = new LumoraSettings(this);
        LumoraRepository repo = ((LumoraApplication) getApplication()).repository();

        Spinner sp = findViewById(R.id.spProvider);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PROVIDERS));
        for (int i = 0; i < PROVIDERS.length; i++) {
            if (PROVIDERS[i].equals(s.getProvider())) sp.setSelection(i);
        }

        EditText apiKey = findViewById(R.id.edApiKey);
        EditText model = findViewById(R.id.edModel);
        EditText baseUrl = findViewById(R.id.edBaseUrl);
        CheckBox cbRemote = findViewById(R.id.cbUseRemote);
        TextView aiUsage = findViewById(R.id.tvAiUsage);
        EditText userName = findViewById(R.id.edUserName);
        EditText morning = findViewById(R.id.edMorning);
        EditText evening = findViewById(R.id.edEvening);
        CheckBox cbToast = findViewById(R.id.cbToast);
        CheckBox cbScreen = findViewById(R.id.cbCollectScreen);
        CheckBox cbUsage = findViewById(R.id.cbCollectUsage);
        CheckBox cbLoc = findViewById(R.id.cbCollectLocation);

        apiKey.setText(s.getApiKey());
        model.setText(s.getModel());
        baseUrl.setText(s.getBaseUrl());
        cbRemote.setChecked(s.isRemoteEnabled());
        aiUsage.setText(s.isRemoteEnabled()
            ? R.string.settings_ai_usage_enabled
            : R.string.settings_ai_usage_disabled);
        userName.setText(s.getUserName());
        morning.setText(s.getMorningTime());
        evening.setText(s.getEveningTime());
        cbToast.setChecked(s.isToastEnabled());
        cbScreen.setChecked(s.isCollectScreen());
        cbUsage.setChecked(s.isCollectUsage());
        cbLoc.setChecked(s.isCollectLocation());

        findViewById(R.id.btnUsagePerm).setOnClickListener(v ->
                startActivity(UsageStatsCollector.settingsIntent()));

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String provider = PROVIDERS[sp.getSelectedItemPosition()];
            s.saveLlm(provider,
                    apiKey.getText().toString(),
                    model.getText().toString(),
                    baseUrl.getText().toString(),
                    cbRemote.isChecked(),
                    userName.getText().toString());
            s.saveNotify(morning.getText().toString().trim(),
                    evening.getText().toString().trim(),
                    s.getQuietStartHour(), s.getQuietEndHour(),
                    cbToast.isChecked());
            s.saveCollect(cbScreen.isChecked(), cbUsage.isChecked(), cbLoc.isChecked());
            ReminderScheduler.scheduleBriefings(this);
            ToastBus.info(this, getString(R.string.settings_saved_toast));
            finish();
        });

            cbRemote.setOnCheckedChangeListener((buttonView, isChecked) ->
                aiUsage.setText(isChecked
                    ? R.string.settings_ai_usage_enabled
                    : R.string.settings_ai_usage_disabled));

        findViewById(R.id.btnExport).setOnClickListener(v -> exportJson(repo));

        findViewById(R.id.btnResetTasks).setOnClickListener(v ->
                confirm(() -> { repo.clearTasks(); ToastBus.info(this, getString(R.string.settings_reset_done)); }));
        findViewById(R.id.btnResetHabits).setOnClickListener(v ->
                confirm(() -> { repo.clearHabits(); ToastBus.info(this, getString(R.string.settings_reset_done)); }));
        findViewById(R.id.btnResetLogs).setOnClickListener(v ->
                confirm(() -> { repo.clearLogs(); ToastBus.info(this, getString(R.string.settings_reset_done)); }));
        findViewById(R.id.btnResetAll).setOnClickListener(v ->
                confirm(() -> { repo.clearAll(); ToastBus.info(this, getString(R.string.settings_reset_done)); }));
    }

    private void confirm(Runnable r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_confirm_title)
                .setMessage(R.string.settings_reset_confirm_msg)
                .setPositiveButton(R.string.common_delete, (d, w) -> r.run())
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void exportJson(LumoraRepository repo) {
        try {
            JSONObject root = new JSONObject();
            JSONArray ts = new JSONArray();
            for (TaskEntity t : repo.tasksAll()) {
                JSONObject o = new JSONObject();
                o.put("id", t.id); o.put("title", t.title); o.put("dueAt", t.dueAt);
                o.put("status", t.status); o.put("repeat", t.repeatRule);
                o.put("createdAt", t.createdAt); o.put("completedAt", t.completedAt);
                ts.put(o);
            }
            JSONArray hs = new JSONArray();
            for (HabitEntity h : repo.habits()) {
                JSONObject o = new JSONObject();
                o.put("id", h.id); o.put("name", h.name);
                o.put("timeOfDay", h.timeOfDay == null ? JSONObject.NULL : h.timeOfDay);
                o.put("streak", h.streak); o.put("lastCheckDay", h.lastCheckDay);
                o.put("createdAt", h.createdAt);
                hs.put(o);
            }
            JSONArray ls = new JSONArray();
            for (ContextLogEntity e : repo.logsSince(0)) {
                JSONObject o = new JSONObject();
                o.put("type", e.type); o.put("ts", e.ts);
                o.put("value", e.value == null ? JSONObject.NULL : e.value);
                ls.put(o);
            }
            root.put("tasks", ts); root.put("habits", hs); root.put("logs", ls);
            File dir = new File(getFilesDir(), "exports");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "lumora-export-" + System.currentTimeMillis() + ".json");
            try (FileWriter fw = new FileWriter(f)) { fw.write(root.toString(2)); }
            ToastBus.info(this, getString(R.string.settings_export_done, f.getAbsolutePath()));
        } catch (Throwable t) {
            ToastBus.info(this, "내보내기 실패: " + t.getMessage());
        }
    }
}
