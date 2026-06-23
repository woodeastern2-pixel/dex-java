package com.lumora.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.data.HabitEntity;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.notify.ToastBus;

import java.util.List;

public class HabitListActivity extends AppCompatActivity {

    private LumoraRepository repo;
    private HabitAdapter adapter;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habit_list);
        repo = ((LumoraApplication) getApplication()).repository();

        RecyclerView rv = findViewById(R.id.list);
        emptyMessage = findViewById(R.id.emptyMessage);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HabitAdapter(new HabitAdapter.Listener() {
            @Override public void onCheck(HabitEntity h) {
                repo.checkHabitToday(h);
                ToastBus.hint(HabitListActivity.this, h.name + " — " + getString(R.string.habit_checked_today));
                refresh();
            }

            @Override public void onEdit(HabitEntity h) {
                showHabitDialog(h);
            }

            @Override public void onDelete(HabitEntity h) {
                confirmDelete(h);
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnAdd).setOnClickListener(v -> showHabitDialog(null));
        refresh();
    }

    private void showHabitDialog(HabitEntity habit) {
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
                    } else {
                        repo.addHabit(n, t.isEmpty() ? null : t);
                    }
                    refresh();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void refresh() {
        List<HabitEntity> list = repo.habits();
        adapter.submit(list);
        updateEmptyMessage(list);
    }

    private void updateEmptyMessage(List<HabitEntity> list) {
        if (list == null || list.isEmpty()) {
            emptyMessage.setVisibility(View.VISIBLE);
        } else {
            emptyMessage.setVisibility(View.GONE);
        }
    }

    private void confirmDelete(HabitEntity habit) {
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
