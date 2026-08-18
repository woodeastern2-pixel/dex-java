package com.lumora.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumora.app.R;
import com.lumora.app.data.HabitEntity;

import java.util.ArrayList;
import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.VH> {

    public interface Listener {
        void onCheck(HabitEntity h);
        void onEdit(HabitEntity h);
        void onDelete(HabitEntity h);
    }

    private final List<HabitEntity> items = new ArrayList<>();
    private final Listener listener;

    public HabitAdapter(Listener l) { this.listener = l; }

    public void submit(List<HabitEntity> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_habit, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HabitEntity item = items.get(position);
        h.name.setText(item.name);
        StringBuilder meta = new StringBuilder();
        if (item.timeOfDay != null && !item.timeOfDay.isEmpty()) {
            meta.append(item.timeOfDay);
        }
        if (item.streak > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(item.streak).append("일 연속");
        }
        h.meta.setText(meta.toString());
        h.btn.setContentDescription(
                h.itemView.getContext().getString(R.string.cd_habit_check, item.name));
        h.btn.setOnClickListener(v -> listener.onCheck(item));
        h.edit.setContentDescription(
            h.itemView.getContext().getString(R.string.cd_habit_edit, item.name));
        h.delete.setContentDescription(
            h.itemView.getContext().getString(R.string.cd_habit_delete, item.name));
        h.edit.setOnClickListener(v -> listener.onEdit(item));
        h.delete.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, meta;
        final Button btn, edit, delete;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.habitName);
            meta = v.findViewById(R.id.habitMeta);
            btn = v.findViewById(R.id.btnCheck);
            edit = v.findViewById(R.id.btnEdit);
            delete = v.findViewById(R.id.btnDelete);
        }
    }
}
