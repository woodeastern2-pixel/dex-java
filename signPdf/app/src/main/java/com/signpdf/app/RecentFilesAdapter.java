package com.signpdf.app;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecentFilesAdapter extends RecyclerView.Adapter<RecentFilesAdapter.ViewHolder> {

    private static final int COLLAPSED_LIMIT = 3;

    public interface OnItemClickListener {
        void onItemClick(RecentFileItem item);
        void onItemRemove(RecentFileItem item, int position);
    }

    public static class RecentFileItem {
        public final String uriString;
        public final String displayName;
        public final String fileType; // PDF or IMAGE

        public RecentFileItem(String uriString, String displayName, String fileType) {
            this.uriString = uriString;
            this.displayName = displayName;
            this.fileType = fileType;
        }

        public Uri getUri() {
            return Uri.parse(uriString);
        }
    }

    private final List<RecentFileItem> mItems;
    private OnItemClickListener mListener;
    private boolean expanded = false;

    public RecentFilesAdapter(List<RecentFileItem> items) {
        mItems = items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        notifyDataSetChanged();
    }

    public boolean isExpanded() {
        return expanded;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_recent_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentFileItem item = mItems.get(position);
        boolean isPdf = "PDF".equals(item.fileType);

        holder.tvName.setText(item.displayName);
        holder.tvType.setText(isPdf ? R.string.file_type_pdf : R.string.file_type_image);
        holder.tvBadge.setText(isPdf ? R.string.file_type_pdf : R.string.file_type_image);
        holder.tvBadge.setTextSize(isPdf ? 12f : 9f);

        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onItemClick(item);
        });

        holder.btnRemove.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (mListener != null && adapterPosition != RecyclerView.NO_POSITION) {
                mListener.onItemRemove(item, adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return expanded ? mItems.size() : Math.min(COLLAPSED_LIMIT, mItems.size());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvType;
        final TextView tvBadge;
        final ImageView btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvType = itemView.findViewById(R.id.tv_file_type);
            tvBadge = itemView.findViewById(R.id.tv_file_badge);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
