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

    public interface OnItemClickListener {
        void onItemClick(RecentFileItem item);
        void onItemRemove(RecentFileItem item, int position);
    }

    /** 최근 파일 항목 */
    public static class RecentFileItem {
        public final String uriString;
        public final String displayName;
        public final String fileType; // "PDF", "이미지"

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

    public RecentFilesAdapter(List<RecentFileItem> items) {
        this.mItems = items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
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
        holder.tvName.setText(item.displayName);
        holder.tvType.setText(item.fileType);

        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onItemClick(item);
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (mListener != null) mListener.onItemRemove(item, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType;
        ImageView btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvType = itemView.findViewById(R.id.tv_file_type);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
