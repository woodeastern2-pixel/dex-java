package com.lumora.app.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumora.app.LumoraApplication;
import com.lumora.app.R;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.TaskEntity;
import com.lumora.app.notify.ReminderScheduler;
import com.lumora.app.notify.ToastBus;

import java.util.List;

public class TaskListActivity extends AppCompatActivity {

    private LumoraRepository repo;
    private TaskAdapter adapter;
    private TextView emptyMessage;
    private String filter = "today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);
        repo = ((LumoraApplication) getApplication()).repository();

        RecyclerView rv = findViewById(R.id.list);
        emptyMessage = findViewById(R.id.emptyMessage);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(new TaskAdapter.Listener() {
            @Override public void onDone(TaskEntity t) {
                repo.markDone(t);
                ReminderScheduler.cancelTask(TaskListActivity.this, t.id);
                ToastBus.hint(TaskListActivity.this, "마무리했어요");
                refresh();
            }
            @Override public void onSnooze(TaskEntity t) {
                repo.snooze(t, 10L * 60 * 1000L);
                ReminderScheduler.scheduleTask(TaskListActivity.this, t);
                refresh();
            }
            @Override public void onDelete(TaskEntity t) {
                confirmDelete(t);
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.fToday).setOnClickListener(v -> { filter = "today"; refresh(); });
        findViewById(R.id.fUpcoming).setOnClickListener(v -> { filter = "upcoming"; refresh(); });
        findViewById(R.id.fDone).setOnClickListener(v -> { filter = "done"; refresh(); });
        findViewById(R.id.fAll).setOnClickListener(v -> { filter = "all"; refresh(); });

        refresh();
    }

    private void refresh() {
        List<TaskEntity> list;
        switch (filter) {
            case "upcoming": list = repo.tasksUpcoming(); break;
            case "done": list = repo.tasksDoneRecent(); break;
            case "all": list = repo.tasksAll(); break;
            case "today":
            default: list = repo.tasksToday(); break;
        }
        adapter.submit(list);
        updateEmptyMessage(list);
    }

    private void updateEmptyMessage(List<TaskEntity> list) {
        if (list == null || list.isEmpty()) {
            emptyMessage.setVisibility(android.view.View.VISIBLE);
        } else {
            emptyMessage.setVisibility(android.view.View.GONE);
        }
    }

    private void confirmDelete(TaskEntity task) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.task_delete_confirm_title)
                .setMessage(getString(R.string.task_delete_confirm_message, task.title))
                .setPositiveButton(R.string.common_delete, (d, w) -> {
                    repo.delete(task);
                    ReminderScheduler.cancelTask(this, task.id);
                    ToastBus.info(this, getString(R.string.task_deleted_toast));
                    refresh();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }
}
