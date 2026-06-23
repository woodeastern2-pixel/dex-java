package com.whereisit.app.ui.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.whereisit.app.R;
import com.whereisit.app.databinding.ItemPhotoPreviewBinding;
import java.util.ArrayList;
import java.util.List;

public class PhotoPreviewAdapter extends RecyclerView.Adapter<PhotoPreviewAdapter.PhotoViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(int position);
    }

    private final List<String> photoUris = new ArrayList<>();
    private final boolean removable;
    private final OnRemoveClickListener removeClickListener;

    public PhotoPreviewAdapter(boolean removable, OnRemoveClickListener removeClickListener) {
        this.removable = removable;
        this.removeClickListener = removeClickListener;
    }

    public void setItems(List<String> items) {
        photoUris.clear();
        if (items != null) {
            photoUris.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPhotoPreviewBinding binding = ItemPhotoPreviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PhotoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        String uri = photoUris.get(position);
        Glide.with(holder.binding.ivPhoto.getContext())
                .load(Uri.parse(uri))
                .placeholder(R.drawable.ic_box_search)
                .centerCrop()
                .into(holder.binding.ivPhoto);

        holder.binding.btnRemove.setVisibility(removable ? View.VISIBLE : View.GONE);
        holder.binding.btnRemove.setOnClickListener(v -> {
            if (removeClickListener != null) {
                removeClickListener.onRemove(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return photoUris.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ItemPhotoPreviewBinding binding;

        PhotoViewHolder(ItemPhotoPreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
