package com.whereisit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.material.chip.Chip;
import com.whereisit.app.BuildConfig;
import com.whereisit.app.R;
import com.whereisit.app.data.ItemRepository;
import com.whereisit.app.databinding.ActivityMainBinding;
import com.whereisit.app.model.ItemEntity;
import com.whereisit.app.monetization.AdsConsentManager;
import com.whereisit.app.monetization.GentleAdManager;
import com.whereisit.app.ui.adapter.ItemAdapter;
import com.whereisit.app.util.EdgeToEdgeUtil;
import com.whereisit.app.util.TagUtil;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ItemRepository repository;
    private ItemAdapter recentAdapter;
    private ItemAdapter favoriteAdapter;
    private AdsConsentManager consentManager;
    private GentleAdManager gentleAdManager;
    private AdView bannerAd;
    private boolean bannerLoadScheduled;
    private String selectedCategory = "";
    private String currentQuery = "";

    private final ActivityResultLauncher<Intent> detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> gentleAdManager.showAfterNaturalBreak(this)
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtil.apply(this, binding.getRoot());

        repository = new ItemRepository(this);
        consentManager = new AdsConsentManager(this);
        gentleAdManager = new GentleAdManager(this);
        setupRecyclerViews();
        setupSearch();
        setupCategoryFilter();
        setupAds();

        binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddEditItemActivity.class)));
        binding.btnAdPrivacy.setOnClickListener(v ->
                consentManager.showPrivacyOptions(this, () -> {
                    updatePrivacyButton();
                    if (consentManager.canRequestAds()) {
                        gentleAdManager.start();
                        loadBanner();
                    } else {
                        gentleAdManager.stop();
                        destroyBanner();
                    }
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) bannerAd.resume();
        refreshContent();
    }

    @Override
    protected void onPause() {
        if (bannerAd != null) bannerAd.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gentleAdManager != null) gentleAdManager.stop();
        destroyBanner();
        super.onDestroy();
    }

    private void setupAds() {
        binding.tvAdLabel.setText(BuildConfig.ADS_USING_TEST_IDS
                ? R.string.ad_label_test
                : R.string.ad_label);
        consentManager.gatherConsent(this, allowed -> {
            updatePrivacyButton();
            if (!allowed) return;
            gentleAdManager.start();
            loadBanner();
        });
    }

    private void loadBanner() {
        if (bannerAd != null || bannerLoadScheduled) return;
        bannerLoadScheduled = true;
        binding.adContainer.post(() -> {
            bannerLoadScheduled = false;
            if (bannerAd != null || isFinishing() || isDestroyed()) return;
            bannerAd = new AdView(this);
            bannerAd.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            bannerAd.setAdSize(getAnchoredAdaptiveBannerSize());
            bannerAd.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    updateAdLayout(true);
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    updateAdLayout(false);
                }
            });
            binding.adContainer.removeAllViews();
            binding.adContainer.addView(bannerAd);
            bannerAd.loadAd(new AdRequest.Builder().build());
        });
    }

    private void destroyBanner() {
        bannerLoadScheduled = false;
        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }
        if (binding != null) {
            binding.adContainer.removeAllViews();
            updateAdLayout(false);
        }
    }

    private AdSize getAnchoredAdaptiveBannerSize() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int widthPixels = binding.adContainer.getWidth();
        if (widthPixels <= 0) widthPixels = metrics.widthPixels;
        int widthDp = Math.max(1, Math.round(widthPixels / metrics.density));
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp);
    }

    private void updateAdLayout(boolean visible) {
        if (binding == null) return;
        binding.adShell.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.adShell.post(() -> {
            if (binding == null) return;
            int adHeight = visible ? binding.adShell.getHeight() : 0;
            int spacing = dpToPx(16);
            CoordinatorLayout.LayoutParams fabParams =
                    (CoordinatorLayout.LayoutParams) binding.fabAdd.getLayoutParams();
            fabParams.bottomMargin = spacing + adHeight;
            binding.fabAdd.setLayoutParams(fabParams);
            binding.contentScroll.setPadding(
                    binding.contentScroll.getPaddingLeft(),
                    binding.contentScroll.getPaddingTop(),
                    binding.contentScroll.getPaddingRight(),
                    dpToPx(104) + adHeight
            );
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updatePrivacyButton() {
        binding.btnAdPrivacy.setVisibility(
                consentManager.isPrivacyOptionsRequired() ? View.VISIBLE : View.GONE);
    }

    private void setupRecyclerViews() {
        recentAdapter = new ItemAdapter(ItemAdapter.typeNormal(), this::openDetail, this::toggleFavorite);
        favoriteAdapter = new ItemAdapter(ItemAdapter.typeFavorite(), this::openDetail, null);
        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(recentAdapter);
        binding.rvFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvFavorites.setAdapter(favoriteAdapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim();
                recentAdapter.setHighlightQuery(currentQuery);
                refreshContent();
            }
        });
    }

    private void setupCategoryFilter() {
        binding.chipAll.setChecked(true);
        for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
            View view = binding.chipGroupCategories.getChildAt(i);
            if (!(view instanceof Chip)) continue;
            Chip chip = (Chip) view;
            chip.setOnClickListener(v -> {
                String text = chip.getText() == null ? "" : chip.getText().toString();
                selectedCategory = getString(R.string.all_category).equals(text) ? "" : text;
                refreshContent();
            });
        }
    }

    private void refreshContent() {
        repository.getAllItems(items -> binding.tvInventoryCount.setText(
                getString(R.string.inventory_count, items == null ? 0 : items.size())));
        loadFavorites();

        boolean searching = !currentQuery.isEmpty() || !selectedCategory.isEmpty();
        if (searching) {
            binding.tvSectionTitle.setText(R.string.search_result);
            binding.tvSectionSubtitle.setText(currentQuery.isEmpty() ? selectedCategory : currentQuery);
            repository.searchItems(currentQuery, selectedCategory, list -> {
                recentAdapter.setItems(list);
                updateEmptyState(list, true);
            });
        } else {
            binding.tvSectionTitle.setText(R.string.recent_items);
            binding.tvSectionSubtitle.setText(R.string.recent_items_desc);
            repository.getAllItems(list -> {
                recentAdapter.setItems(list);
                updateEmptyState(list, false);
            });
        }
    }

    private void loadFavorites() {
        repository.getFavoriteItems(items -> {
            List<ItemEntity> favorites = items == null ? new ArrayList<>() : items;
            favoriteAdapter.setItems(favorites);
            int visibility = favorites.isEmpty() ? View.GONE : View.VISIBLE;
            binding.tvFavoriteTitle.setVisibility(visibility);
            binding.rvFavorites.setVisibility(visibility);
        });
    }

    private void updateEmptyState(List<ItemEntity> list, boolean searching) {
        boolean empty = list == null || list.isEmpty();
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvItems.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvEmptyTitle.setText(searching ? R.string.empty_search : R.string.no_items);
        binding.tvEmptyDesc.setText(searching ? R.string.search_hint : R.string.no_items_desc);
    }

    private void openDetail(ItemEntity item) {
        gentleAdManager.recordMeaningfulAction();
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, item.id);
        detailLauncher.launch(intent);
    }

    private void toggleFavorite(ItemEntity item) {
        item.favorite = !item.favorite;
        item.updatedDate = System.currentTimeMillis();
        item.tags = TagUtil.normalize(item.tags);
        repository.update(item, this::refreshContent);
    }
}
