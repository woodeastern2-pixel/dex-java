package com.lumora.app.data;

import android.content.Context;

import com.lumora.app.engine.TaskParser;

import java.util.Calendar;
import java.util.List;

/**
 * 단순 facade. UI 코드가 DAO 를 직접 호출하지 않도록 가운데에서 중계.
 */
public class LumoraRepository {

    private final AppDatabase db;
    private final TaskParser parser = new TaskParser();

    public LumoraRepository(Context ctx) {
        this.db = AppDatabase.get(ctx);
    }

    public AppDatabase database() { return db; }

    // --- Task ---
    public TaskEntity quickAdd(String raw) {
        TaskEntity t = parser.parse(raw);
        long id = db.taskDao().insert(t);
        t.id = id;
        return t;
    }

    public List<TaskEntity> tasksToday() {
        long[] r = todayRange();
        return db.taskDao().today(r[0], r[1]);
    }

    public List<TaskEntity> tasksUpcoming() {
        return db.taskDao().upcoming(System.currentTimeMillis());
    }

    public List<TaskEntity> tasksDoneRecent() {
        return db.taskDao().recentDone();
    }

    public List<TaskEntity> tasksAll() { return db.taskDao().all(); }

    public void markDone(TaskEntity t) {
        t.status = TaskEntity.STATUS_DONE;
        t.completedAt = System.currentTimeMillis();
        db.taskDao().update(t);
    }

    public void snooze(TaskEntity t, long deltaMs) {
        if (t.dueAt > 0) t.dueAt += deltaMs;
        else t.dueAt = System.currentTimeMillis() + deltaMs;
        t.status = TaskEntity.STATUS_SNOOZED;
        db.taskDao().update(t);
    }

    public void delete(TaskEntity t) { db.taskDao().delete(t); }

    public void clearTasks() { db.taskDao().clearAll(); }

    // --- Habit ---
    public List<HabitEntity> habits() { return db.habitDao().all(); }

    public HabitEntity addHabit(String name, String time) {
        HabitEntity h = new HabitEntity();
        h.name = name;
        h.timeOfDay = time;
        h.createdAt = System.currentTimeMillis();
        long id = db.habitDao().insert(h);
        h.id = id;
        return h;
    }

    public void checkHabitToday(HabitEntity h) {
        long today = todayKey();
        if (h.lastCheckDay == today) return;
        if (h.lastCheckDay == today - 1L) h.streak = h.streak + 1;
        else h.streak = 1;
        h.lastCheckDay = today;
        db.habitDao().update(h);
    }

    public void updateHabit(HabitEntity h) {
        db.habitDao().update(h);
    }

    public void deleteHabit(HabitEntity h) { db.habitDao().delete(h); }
    public void clearHabits() { db.habitDao().clearAll(); }

    // --- Context log ---
    public void log(String type, String value) {
        ContextLogEntity e = new ContextLogEntity();
        e.type = type;
        e.value = value;
        e.ts = System.currentTimeMillis();
        db.contextLogDao().insert(e);
    }

    public List<ContextLogEntity> logsSince(long since) {
        return db.contextLogDao().since(since);
    }

    public void clearLogs() { db.contextLogDao().clearAll(); }

    public void purgeOldLogs(long retentionMs) {
        db.contextLogDao().purgeOlderThan(System.currentTimeMillis() - retentionMs);
    }

    public void clearAll() {
        clearTasks(); clearHabits(); clearLogs();
    }

    // --- helpers ---
    public static long[] todayRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long s = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{s, c.getTimeInMillis()};
    }

    public static long todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000L
                + (c.get(Calendar.MONTH) + 1) * 100L
                + c.get(Calendar.DAY_OF_MONTH);
    }
}
