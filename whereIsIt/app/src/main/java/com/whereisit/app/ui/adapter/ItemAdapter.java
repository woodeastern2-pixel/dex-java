package com.whereisit.app.ui.adapter;

import android.net.Uri;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.whereisit.app.R;
import com.whereisit.app.databinding.ItemCardBinding;
import com.whereisit.app.databinding.ItemFavoriteCardBinding;
import com.whereisit.app.model.ItemEntity;
import com.whereisit.app.util.TagUtil;
import java.util.ArrayList;
import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ItemEntity item);
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(ItemEntity item);
    }

    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_FAVORITE = 1;

    private final List<ItemEntity> items = new ArrayList<>();
    private final int viewType;
    private final OnItemClickListener itemClickListener;
    private final OnFavoriteClickListener favoriteClickListener;
    private String highlightQuery = "";

    public ItemAdapter(
            int viewType,
            OnItemClickListener itemClickListener,
            OnFavoriteClickListener favoriteClickListener
    ) {
        this.viewType = viewType;
        this.itemClickListener = itemClickListener;
        this.favoriteClickListener = favoriteClickListener;
    }

    public void setItems(List<ItemEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setHighlightQuery(String highlightQuery) {
        this.highlightQuery = highlightQuery == null ? "" : highlightQuery.trim();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == TYPE_FAVORITE) {
            ItemFavoriteCardBinding binding = ItemFavoriteCardBinding.inflate(inflater, parent, false);
            return new FavoriteViewHolder(binding);
        }
        ItemCardBinding binding = ItemCardBinding.inflate(inflater, parent, false);
        return new NormalViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ItemEntity item = items.get(position);
        if (holder instanceof FavoriteViewHolder) {
            ((FavoriteViewHolder) holder).bind(item);
        } else {
            ((NormalViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private CharSequence highlightedText(String source, @ColorInt int color) {
        if (TextUtils.isEmpty(source) || TextUtils.isEmpty(highlightQuery)) {
            return source;
        }
        String lowerSource = source.toLowerCase();
        String lowerQuery = highlightQuery.toLowerCase();
        int start = lowerSource.indexOf(lowerQuery);
        if (start < 0) {
            return source;
        }
        int end = start + lowerQuery.length();
        SpannableString spannable = new SpannableString(source);
        spannable.setSpan(new ForegroundColorSpan(color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    class NormalViewHolder extends RecyclerView.ViewHolder {
        private final ItemCardBinding binding;

        NormalViewHolder(ItemCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ItemEntity item) {
            int accent = ContextCompat.getColor(binding.getRoot().getContext(), R.color.orange_accent);
            binding.tvName.setText(highlightedText(item.itemName, accent));
            binding.tvLocation.setText(highlightedText(item.locationName, accent));
            List<String> normalizedTags = TagUtil.normalize(item.tags);
            String tagText = normalizedTags.isEmpty()
                    ? item.category
                    : item.category + " · #" + TextUtils.join(" #", normalizedTags);
            binding.tvMeta.setText(highlightedText(tagText, accent));
            int star = item.favorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off;
            binding.btnFavorite.setImageResource(star);
            binding.btnFavorite.setOnClickListener(v -> {
                if (favoriteClickListener != null) {
                    favoriteClickListener.onFavoriteClick(item);
                }
            });
            binding.getRoot().setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(item);
                }
            });

            if (item.photoUris != null && !item.photoUris.isEmpty()) {
                Glide.with(binding.ivThumb.getContext())
                        .load(Uri.parse(item.photoUris.get(0)))
                        .placeholder(R.drawable.ic_box_search)
                        .centerCrop()
                        .into(binding.ivThumb);
            } else {
                binding.ivThumb.setImageResource(R.drawable.ic_box_search);
            }
        }
    }

    class FavoriteViewHolder extends RecyclerView.ViewHolder {
        private final ItemFavoriteCardBinding binding;

        FavoriteViewHolder(ItemFavoriteCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ItemEntity item) {
            binding.tvName.setText(item.itemName);
            binding.tvLocation.setText(item.locationName);
            if (item.photoUris != null && !item.photoUris.isEmpty()) {
                Glide.with(binding.ivThumb.getContext())
                        .load(Uri.parse(item.photoUris.get(0)))
                        .placeholder(R.drawable.ic_box_search)
                        .centerCrop()
                        .into(binding.ivThumb);
            } else {
                binding.ivThumb.setImageResource(R.drawable.ic_box_search);
            }
            binding.getRoot().setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(item);
                }
            });
        }
    }

    public static int typeNormal() {
        return TYPE_NORMAL;
    }

    public static int typeFavorite() {
        return TYPE_FAVORITE;
    }
}
