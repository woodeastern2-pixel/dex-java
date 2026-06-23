package com.lumora.app.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;
import com.lumora.app.data.TaskEntity;
import com.lumora.app.engine.BriefingComposer;
import com.lumora.app.engine.TaskParser;
import com.lumora.app.llm.ChatTurn;
import com.lumora.app.llm.LlmClient;
import com.lumora.app.notify.ReminderScheduler;
import com.lumora.app.notify.ToastBus;
import com.lumora.app.widget.TodayWidgetProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_QUICK_ADD = "com.lumora.app.action.QUICK_ADD";

    private static final int REQ_NOTIF = 9120;
    private static final int REQ_MIC = 9121;

    private LumoraRepository repo;
    private TaskAdapter taskAdapter;
    private HabitAdapter habitAdapter;
    private TextView briefingText;
    private TextView todayEmpty;
    private TextView habitEmpty;
    private EditText quickInput;
    private SpeechRecognizer speech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LumoraSettings s = new LumoraSettings(this);
        if (!s.isLegalAccepted()) {
            startActivity(new Intent(this, ConsentActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        repo = ((LumoraApplication) getApplication()).repository();

        briefingText = findViewById(R.id.briefingText);
        todayEmpty = findViewById(R.id.todayEmpty);
        habitEmpty = findViewById(R.id.habitEmpty);

        RecyclerView todayRv = findViewById(R.id.todayList);
        RecyclerView habitRv = findViewById(R.id.habitList);
        todayRv.setLayoutManager(new LinearLayoutManager(this));
        habitRv.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(new TaskAdapter.Listener() {
            @Override public void onDone(TaskEntity t) {
                repo.markDone(t);
                ReminderScheduler.cancelTask(MainActivity.this, t.id);
                ToastBus.hint(MainActivity.this, "마무리했어요 — " + t.title);
                announce(getString(R.string.ax_announce_task_done, t.title));
                refresh();
                TodayWidgetProvider.requestRefresh(MainActivity.this);
            }
            @Override public void onSnooze(TaskEntity t) {
                repo.snooze(t, 10L * 60 * 1000L);
                ReminderScheduler.scheduleTask(MainActivity.this, t);
                ToastBus.hint(MainActivity.this, "10분 뒤에 다시 알려드릴게요");
                announce(getString(R.string.ax_announce_task_snoozed));
                refresh();
            }
            @Override public void onDelete(TaskEntity t) {
                confirmDeleteTask(t);
            }
        });
        habitAdapter = new HabitAdapter(new HabitAdapter.Listener() {
            @Override public void onCheck(com.lumora.app.data.HabitEntity h) {
                repo.checkHabitToday(h);
                ToastBus.hint(MainActivity.this, h.name + " — " + getString(R.string.habit_checked_today));
                announce(getString(R.string.ax_announce_habit_checked, h.name));
                refresh();
            }

            @Override public void onEdit(com.lumora.app.data.HabitEntity h) {
                showHabitDialog(h);
            }

            @Override public void onDelete(com.lumora.app.data.HabitEntity h) {
                confirmDeleteHabit(h);
            }
        });
        todayRv.setAdapter(taskAdapter);
        habitRv.setAdapter(habitAdapter);

        quickInput = findViewById(R.id.quickInput);
        Button quickAdd = findViewById(R.id.quickAdd);
        ImageButton quickMic = findViewById(R.id.quickMic);
        DrawerLayout drawerLayout = findViewById(R.id.mainDrawer);
        NavigationView navView = findViewById(R.id.mainNavView);
        ImageButton openDrawerButton = findViewById(R.id.openDrawerButton);
        TextView quickHintTicker = findViewById(R.id.quickHintTicker);

        briefingText.setSelected(true);
        quickHintTicker.setSelected(true);

        openDrawerButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_tasks) {
                startActivity(new Intent(this, TaskListActivity.class));
            } else if (id == R.id.nav_habits) {
                startActivity(new Intent(this, HabitListActivity.class));
            } else if (id == R.id.nav_insight) {
                startActivity(new Intent(this, InsightActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                return false;
            }
            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });

        Runnable add = () -> {
            String text = quickInput.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            TaskEntity t = repo.quickAdd(text);
            quickInput.setText("");
            if (t.dueAt > 0) {
                ReminderScheduler.scheduleTask(this, t);
                ToastBus.info(this, getString(R.string.task_added_with_time_toast, TaskParser.formatDue(t.dueAt)));
            } else {
                ToastBus.info(this, getString(R.string.task_added_toast));
            }
            announce(getString(R.string.ax_announce_added));
            refresh();
            refineTitleWithAiAsync(t, text);
            TodayWidgetProvider.requestRefresh(this);
        };
        quickAdd.setOnClickListener(v -> add.run());
        quickInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { add.run(); return true; }
            return false;
        });
        quickMic.setOnClickListener(v -> startVoice());
        findViewById(R.id.openLegal).setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)));

        TextView versionLabel = findViewById(R.id.versionLabel);
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionLabel.setText(getString(R.string.main_version_label, v == null ? "1.0" : v));
        } catch (PackageManager.NameNotFoundException ignored) {
            versionLabel.setText(getString(R.string.main_version_label, "1.0"));
        }

        ensureNotificationPermission();
        ReminderScheduler.scheduleBriefings(this);
        TodayWidgetProvider.requestRefresh(this);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_QUICK_ADD.equals(intent.getAction()) && quickInput != null) {
            quickInput.requestFocus();
            quickInput.post(this::startVoice);
        }
    }

    private void announce(CharSequence msg) {
        View root = findViewById(android.R.id.content);
        if (root != null) root.announceForAccessibility(msg);
    }

    private void startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO")
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{"android.permission.RECORD_AUDIO"}, REQ_MIC);
            return;
        }
        if (speech != null) { speech.destroy(); speech = null; }
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.KOREA.toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speech.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) {
                Toast.makeText(MainActivity.this, R.string.voice_listening, Toast.LENGTH_SHORT).show();
                announce(getString(R.string.voice_listening));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] bytes) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { release(); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> list = results == null ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String text = list.get(0);
                    if (quickInput != null) {
                        quickInput.setText(text);
                        quickInput.setSelection(text.length());
                    }
                }
                release();
            }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}
            private void release() {
                if (speech != null) { speech.destroy(); speech = null; }
            }
        });
        try { speech.startListening(i); }
        catch (SecurityException se) {
            Toast.makeText(this, R.string.voice_perm_needed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else if (requestCode == REQ_MIC) {
            Toast.makeText(this, R.string.voice_perm_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            String perm = "android.permission.POST_NOTIFICATIONS";
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_NOTIF);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo == null) return;
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (speech != null) { speech.destroy(); speech = null; }
        super.onDestroy();
    }

    private void refresh() {
        var today = repo.tasksToday();
        taskAdapter.submit(today);
        todayEmpty.setVisibility(today.isEmpty() ? View.VISIBLE : View.GONE);

        var habits = repo.habits();
        habitAdapter.submit(habits);
        habitEmpty.setVisibility(habits.isEmpty() ? View.VISIBLE : View.GONE);

        BriefingComposer composer = new BriefingComposer(repo.database(),
                new LumoraSettings(this));
        briefingText.setText(composer.compose(BriefingComposer.Kind.MORNING));
        briefingText.setSelected(true);
    }

    private void refineTitleWithAiAsync(TaskEntity task, String rawInput) {
        LumoraSettings settings = new LumoraSettings(this);
        if (!settings.isRemoteEnabled()) return;
        String key = settings.getApiKey();
        if (key == null || key.trim().isEmpty()) return;

        new Thread(() -> {
            try {
                String due = task.dueAt > 0 ? TaskParser.formatDue(task.dueAt) : "시간 없음";
                String prompt = "입력 문장을 일정 제목 한 줄로 다듬어 주세요. "
                        + "명령형 표현(예: 넣어줘, 등록해줘, 추가해줘)은 제거하고, "
                        + "실행 행동 중심으로 자연스럽게 정리하세요. "
                        + "응답은 제목 한 줄만 반환하세요.";
                String user = "원문: " + rawInput + "\n"
                        + "현재 제목: " + task.title + "\n"
                        + "시간: " + due;

                String out = new LlmClient().complete(
                        settings,
                        prompt,
                        Collections.singletonList(ChatTurn.user(user))
                );
                String refined = normalizeLlmTitle(out);
                if (TextUtils.isEmpty(refined) || refined.equals(task.title)) return;

                task.title = refined;
                repo.database().taskDao().update(task);
                runOnUiThread(() -> {
                    refresh();
                    TodayWidgetProvider.requestRefresh(MainActivity.this);
                });
            } catch (Throwable ignore) {
                // LLM 실패 시 기존 파싱 결과를 유지한다.
            }
        }).start();
    }

    private String normalizeLlmTitle(String out) {
        if (out == null) return "";
        String line = out.split("\\r?\\n")[0].trim();
        line = line.replaceAll("^[\"'`]+|[\"'`]+$", "");
        line = line.replaceAll("^(제목\s*[:：]|일정\s*[:：])", "").trim();
        if (line.length() > 40) line = line.substring(0, 40).trim();
        return line;
    }

    private void confirmDeleteTask(TaskEntity task) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.task_delete_confirm_title)
                .setMessage(getString(R.string.task_delete_confirm_message, task.title))
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    repo.delete(task);
                    ReminderScheduler.cancelTask(this, task.id);
                    ToastBus.info(this, getString(R.string.task_deleted_toast));
                    refresh();
                    TodayWidgetProvider.requestRefresh(this);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showHabitDialog(com.lumora.app.data.HabitEntity habit) {
        View view = getLayoutInflater().inflate(R.layout.dialog_habit, null);
        EditText name = view.findViewById(R.id.inputName);
        EditText time = view.findViewById(R.id.inputTime);
        if (habit != null) {
            name.setText(habit.name);
            if (habit.timeOfDay != null) time.setText(habit.timeOfDay);
        }
        boolean isEdit = habit != null;
        new AlertDialog.Builder(this)
                .setTitle(isEdit ? R.string.habit_edit : R.string.habit_add)
                .setView(view)
                .setPositiveButton(R.string.habit_dialog_save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String t = time.getText().toString().trim();
                    if (n.isEmpty()) return;
                    if (isEdit) {
                        habit.name = n;
                        habit.timeOfDay = t.isEmpty() ? null : t;
                        repo.updateHabit(habit);
                        ToastBus.info(this, getString(R.string.habit_updated_toast));
                    } else {
                        repo.addHabit(n, t.isEmpty() ? null : t);
                        ToastBus.info(this, getString(R.string.habit_added_toast));
                    }
                    refresh();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void confirmDeleteHabit(com.lumora.app.data.HabitEntity habit) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.habit_delete_confirm_title)
                .setMessage(getString(R.string.habit_delete_confirm_message, habit.name))
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    repo.deleteHabit(habit);
                    ToastBus.info(this, getString(R.string.habit_deleted_toast));
                    refresh();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }
}
