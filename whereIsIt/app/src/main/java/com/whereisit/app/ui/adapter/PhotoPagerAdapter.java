package com.whereisit.app.ui.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.whereisit.app.R;
import com.whereisit.app.databinding.ItemPhotoPagerBinding;
import java.util.ArrayList;
import java.util.List;

public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoPagerViewHolder> {

    private final List<String> photoUris = new ArrayList<>();

    public void setItems(List<String> items) {
        photoUris.clear();
        if (items != null) {
            photoUris.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoPagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPhotoPagerBinding binding = ItemPhotoPagerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PhotoPagerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoPagerViewHolder holder, int position) {
        if (photoUris.isEmpty()) {
            holder.binding.ivPhoto.setImageResource(R.drawable.ic_box_search);
            return;
        }
        Glide.with(holder.binding.ivPhoto.getContext())
                .load(Uri.parse(photoUris.get(position)))
                .placeholder(R.drawable.ic_box_search)
                .centerCrop()
                .into(holder.binding.ivPhoto);
    }

    @Override
    public int getItemCount() {
        return photoUris.isEmpty() ? 1 : photoUris.size();
    }

    class PhotoPagerViewHolder extends RecyclerView.ViewHolder {
        final ItemPhotoPagerBinding binding;

        PhotoPagerViewHolder(ItemPhotoPagerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
