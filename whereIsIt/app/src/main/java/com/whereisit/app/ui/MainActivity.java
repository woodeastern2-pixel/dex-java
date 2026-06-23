package com.whereisit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.Chip;
import com.whereisit.app.R;
import com.whereisit.app.data.ItemRepository;
import com.whereisit.app.databinding.ActivityMainBinding;
import com.whereisit.app.model.ItemEntity;
import com.whereisit.app.ui.adapter.ItemAdapter;
import com.whereisit.app.util.TagUtil;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ItemRepository repository;
    private ItemAdapter recentAdapter;
    private ItemAdapter favoriteAdapter;
    private String selectedCategory = "";
    private String currentQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new ItemRepository(this);
        setupRecyclerViews();
        setupSearch();
        setupCategoryFilter();

        binding.fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditItemActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContent();
    }

    private void setupRecyclerViews() {
        recentAdapter = new ItemAdapter(
                ItemAdapter.typeNormal(),
                this::openDetail,
                this::toggleFavorite
        );
        favoriteAdapter = new ItemAdapter(
                ItemAdapter.typeFavorite(),
                this::openDetail,
                null
        );

        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(recentAdapter);

        binding.rvFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvFavorites.setAdapter(favoriteAdapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim();
                recentAdapter.setHighlightQuery(currentQuery);
                refreshContent();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupCategoryFilter() {
        binding.chipAll.setChecked(true);
        for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
            View view = binding.chipGroupCategories.getChildAt(i);
            if (view instanceof Chip) {
                Chip chip = (Chip) view;
                chip.setOnClickListener(v -> {
                    String text = chip.getText() == null ? "" : chip.getText().toString();
                    selectedCategory = getString(R.string.all_category).equals(text) ? "" : text;
                    refreshContent();
                });
            }
        }
    }

    private void refreshContent() {
        loadFavorites();

        boolean isSearching = !currentQuery.isEmpty() || !selectedCategory.isEmpty();
        if (isSearching) {
            binding.tvSectionTitle.setText(R.string.search_result);
            repository.searchItems(currentQuery, selectedCategory, list -> {
                recentAdapter.setItems(list);
                updateEmptyState(list, true);
            });
        } else {
            binding.tvSectionTitle.setText(R.string.recent_items);
            repository.getRecentItems(20, list -> {
                recentAdapter.setItems(list);
                updateEmptyState(list, false);
            });
        }
    }

    private void loadFavorites() {
        repository.getFavoriteItems(items -> {
            List<ItemEntity> favorites = items == null ? new ArrayList<>() : items;
            favoriteAdapter.setItems(favorites);
            binding.tvFavoriteTitle.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
            binding.rvFavorites.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    private void updateEmptyState(List<ItemEntity> list, boolean isSearching) {
        boolean isEmpty = list == null || list.isEmpty();
        binding.layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.tvEmptyTitle.setText(isSearching ? R.string.empty_search : R.string.no_items);
        binding.tvEmptyDesc.setText(isSearching ? R.string.search_hint : R.string.no_items_desc);
    }

    private void openDetail(ItemEntity item) {
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, item.id);
        startActivity(intent);
    }

    private void toggleFavorite(ItemEntity item) {
        item.favorite = !item.favorite;
        item.updatedDate = System.currentTimeMillis();
        item.tags = TagUtil.normalize(item.tags);
        repository.update(item, this::refreshContent);
    }
}
