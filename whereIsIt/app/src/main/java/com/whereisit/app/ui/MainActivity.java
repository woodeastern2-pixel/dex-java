package com.whereisit.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import com.whereisit.app.monetization.RewardAccessStore;
import com.whereisit.app.monetization.RewardedAdManager;
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
    private RewardAccessStore rewardAccessStore;
    private RewardedAdManager rewardedAdManager;
    private AdView bannerAd;
    private boolean bannerLoadScheduled;
    private boolean consentResolved;
    private boolean adsAllowed;
    private String selectedCategory = "";
    private String currentQuery = "";
    private final Handler rewardTimer = new Handler(Looper.getMainLooper());
    private final Runnable rewardTicker = new Runnable() {
        @Override
        public void run() {
            renderRewardState();
            if (rewardAccessStore != null && rewardAccessStore.hasAccess()) {
                rewardTimer.postDelayed(this, 30_000L);
            }
        }
    };

    private final ActivityResultLauncher<Intent> detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> refreshContent()
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtil.apply(this, binding.getRoot());

        repository = new ItemRepository(this);
        consentManager = new AdsConsentManager(this);
        rewardAccessStore = new RewardAccessStore(this);
        rewardedAdManager = new RewardedAdManager(
                this,
                rewardAccessStore,
                new RewardedAdManager.Listener() {
                    @Override
                    public void onStateChanged() {
                        renderRewardState();
                    }

                    @Override
                    public void onRewardGranted() {
                        Toast.makeText(
                                MainActivity.this,
                                R.string.reward_access_granted,
                                Toast.LENGTH_LONG
                        ).show();
                        renderRewardState();
                    }
                }
        );
        setupRecyclerViews();
        setupSearch();
        setupCategoryFilter();
        setupAds();

        binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddEditItemActivity.class)));
        binding.btnRewardAccess.setOnClickListener(v -> requestRewardAccess());
        binding.btnAdPrivacy.setOnClickListener(v ->
                consentManager.showPrivacyOptions(this, () -> {
                    updatePrivacyButton();
                    applyConsentResult(consentManager.canRequestAds());
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAd != null) bannerAd.resume();
        rewardTimer.removeCallbacks(rewardTicker);
        rewardTicker.run();
        if (rewardedAdManager != null) rewardedAdManager.load();
        refreshContent();
    }

    @Override
    protected void onPause() {
        rewardTimer.removeCallbacks(rewardTicker);
        if (bannerAd != null) bannerAd.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        rewardTimer.removeCallbacks(rewardTicker);
        if (rewardedAdManager != null) rewardedAdManager.destroy();
        destroyBanner();
        super.onDestroy();
    }

    private void setupAds() {
        binding.tvAdLabel.setText(BuildConfig.ADS_USING_TEST_IDS
                ? R.string.ad_label_test
                : R.string.ad_label);
        consentManager.gatherConsent(this, allowed -> {
            updatePrivacyButton();
            applyConsentResult(allowed);
        });
    }

    private void applyConsentResult(boolean allowed) {
        consentResolved = true;
        adsAllowed = allowed;
        renderRewardState();
    }

    private void requestRewardAccess() {
        if (rewardAccessStore.hasAccess()) {
            Toast.makeText(this, R.string.reward_access_already_active, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!consentResolved) {
            Toast.makeText(this, R.string.reward_consent_waiting, Toast.LENGTH_LONG).show();
            return;
        }
        if (!adsAllowed) {
            Toast.makeText(this, R.string.reward_ad_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.reward_ad_intro_title)
                .setMessage(R.string.reward_ad_intro_message)
                .setPositiveButton(R.string.reward_ad_watch, (dialog, which) -> {
                    if (!rewardedAdManager.show()) {
                        Toast.makeText(
                                this,
                                R.string.reward_ad_not_ready,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderRewardState() {
        if (binding == null || rewardAccessStore == null || rewardedAdManager == null) return;
        boolean active = rewardAccessStore.hasAccess();
        if (active) {
            binding.btnRewardAccess.setText(getString(
                    R.string.reward_access_active,
                    rewardAccessStore.remainingMinutes()));
            binding.btnRewardAccess.setEnabled(false);
            rewardedAdManager.setEnabled(false);
            destroyBanner();
            return;
        }

        boolean canUseReward = consentResolved && adsAllowed;
        if (!consentResolved || rewardedAdManager.isLoading()) {
            binding.btnRewardAccess.setText(R.string.reward_ad_loading);
        } else if (!adsAllowed) {
            binding.btnRewardAccess.setText(R.string.reward_ad_unavailable_button);
        } else {
            binding.btnRewardAccess.setText(R.string.reward_access_button);
        }
        binding.btnRewardAccess.setEnabled(canUseReward);
        rewardedAdManager.setEnabled(canUseReward);
        if (canUseReward) {
            loadBanner();
        } else {
            destroyBanner();
        }
    }

    private void loadBanner() {
        if (!adsAllowed || rewardAccessStore.hasAccess() || bannerAd != null || bannerLoadScheduled) return;
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
                    if (rewardAccessStore.hasAccess()) {
                        destroyBanner();
                        return;
                    }
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
