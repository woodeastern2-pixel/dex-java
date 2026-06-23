package com.lumora.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumora.app.R;
import com.lumora.app.data.TaskEntity;
import com.lumora.app.engine.TaskParser;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface Listener {
        void onDone(TaskEntity t);
        void onSnooze(TaskEntity t);
        void onDelete(TaskEntity t);
    }

    private final List<TaskEntity> items = new ArrayList<>();
    private final Listener listener;

    public TaskAdapter(Listener l) { this.listener = l; }

    public void submit(List<TaskEntity> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TaskEntity t = items.get(position);
        h.title.setText(t.title);
        if (t.dueAt > 0) h.sub.setText(TaskParser.formatDue(t.dueAt));
        else h.sub.setText(R.string.task_no_due);
        h.done.setContentDescription(
                h.itemView.getContext().getString(R.string.cd_task_done, t.title));
        h.snooze.setContentDescription(
                h.itemView.getContext().getString(R.string.cd_task_snooze, t.title));
        h.delete.setContentDescription(
            h.itemView.getContext().getString(R.string.cd_task_delete, t.title));
        h.done.setOnClickListener(v -> listener.onDone(t));
        h.snooze.setOnClickListener(v -> listener.onSnooze(t));
        h.delete.setOnClickListener(v -> listener.onDelete(t));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, sub;
        final Button done, snooze, delete;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.itemTitle);
            sub = v.findViewById(R.id.itemSub);
            done = v.findViewById(R.id.btnDone);
            snooze = v.findViewById(R.id.btnSnooze);
            delete = v.findViewById(R.id.btnDelete);
        }
    }
}
