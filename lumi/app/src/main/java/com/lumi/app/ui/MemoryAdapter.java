package com.lumi.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.app.R;
import com.lumi.app.model.MemoryEntry;

import java.util.List;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.Holder> {

    private final List<MemoryEntry> items;

    public MemoryAdapter(List<MemoryEntry> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MemoryEntry entry = items.get(position);
        holder.keyword.setText(entry.keyword);
        holder.detail.setText(entry.detail);
        holder.category.setText(entry.category);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView keyword;
        TextView detail;
        TextView category;
        Holder(View itemView) {
            super(itemView);
            keyword = itemView.findViewById(R.id.memoryKeyword);
            detail = itemView.findViewById(R.id.memoryDetail);
            category = itemView.findViewById(R.id.memoryCategory);
        }
    }
}
