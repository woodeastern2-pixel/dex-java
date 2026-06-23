package com.whereisit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.whereisit.app.R;
import com.whereisit.app.data.ItemRepository;
import com.whereisit.app.databinding.ActivityItemDetailBinding;
import com.whereisit.app.model.ItemEntity;
import com.whereisit.app.ui.adapter.PhotoPagerAdapter;
import com.whereisit.app.util.DateTimeUtil;
import com.whereisit.app.util.TagUtil;
import java.util.ArrayList;
import java.util.List;

public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private ActivityItemDetailBinding binding;
    private ItemRepository repository;
    private PhotoPagerAdapter photoPagerAdapter;
    private long itemId = -1L;
    private ItemEntity currentItem;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityItemDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new ItemRepository(this);
        photoPagerAdapter = new PhotoPagerAdapter();
        binding.viewPagerPhotos.setAdapter(photoPagerAdapter);

        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);
        if (itemId <= 0) {
            finish();
            return;
        }

        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditItemActivity.class);
            intent.putExtra(AddEditItemActivity.EXTRA_ITEM_ID, itemId);
            startActivity(intent);
        });

        binding.btnDelete.setOnClickListener(v -> showDeleteDialog());
        binding.btnFavorite.setOnClickListener(v -> toggleFavorite());
        binding.btnShare.setOnClickListener(v -> shareItem());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadItem();
    }

    private void loadItem() {
        repository.getById(itemId, item -> {
            if (item == null) {
                finish();
                return;
            }
            currentItem = item;
            render(item);
        });
    }

    private void render(ItemEntity item) {
        photoPagerAdapter.setItems(item.photoUris == null ? new ArrayList<>() : item.photoUris);
        binding.tvName.setText(item.itemName);
        binding.tvLocation.setText(item.locationName);
        binding.tvCategory.setText(item.category);
        binding.tvMemo.setText(item.memo == null || item.memo.isEmpty() ? "메모 없음" : item.memo);
        List<String> normalizedTags = TagUtil.normalize(item.tags);
        binding.tvTags.setText(
            normalizedTags.isEmpty()
                        ? ""
                : "#" + String.join(" #", normalizedTags)
        );
        String dateText = getString(R.string.created_at) + " " + DateTimeUtil.format(item.createdDate)
                + "\n" + getString(R.string.updated_at) + " " + DateTimeUtil.format(item.updatedDate);
        binding.tvDates.setText(dateText);
        binding.btnFavorite.setText(item.favorite ? "즐겨찾기 해제" : getString(R.string.favorite));
    }

    private void toggleFavorite() {
        if (currentItem == null) {
            return;
        }
        currentItem.favorite = !currentItem.favorite;
        currentItem.updatedDate = System.currentTimeMillis();
        currentItem.tags = TagUtil.normalize(currentItem.tags);
        repository.update(currentItem, this::loadItem);
    }

    private void showDeleteDialog() {
        if (currentItem == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.yes_delete, (dialog, which) ->
                        repository.delete(currentItem, this::finish))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareItem() {
        if (currentItem == null) {
            return;
        }
        String text = "[내 물건 어디있지?]\n"
                + "물건: " + currentItem.itemName + "\n"
                + "위치: " + currentItem.locationName + "\n"
                + "카테고리: " + currentItem.category + "\n"
                + "메모: " + (currentItem.memo == null ? "" : currentItem.memo);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }
}
