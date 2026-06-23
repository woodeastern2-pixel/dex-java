package com.whereisit.app.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.whereisit.app.R;
import com.whereisit.app.data.ItemRepository;
import com.whereisit.app.databinding.ActivityAddEditItemBinding;
import com.whereisit.app.model.ItemEntity;
import com.whereisit.app.ui.adapter.PhotoPreviewAdapter;
import com.whereisit.app.util.TagUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AddEditItemActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    private static final String PREFS_NAME = "whereisit_prefs";
    private static final String KEY_CUSTOM_CATEGORIES = "custom_categories";

    private ActivityAddEditItemBinding binding;
    private ItemRepository repository;
    private final List<String> photoUris = new ArrayList<>();
    private PhotoPreviewAdapter photoAdapter;
    private long editingItemId = -1L;
    private ItemEntity editingItem;
    private Uri pendingCameraUri;

    private ActivityResultLauncher<String[]> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditItemBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new ItemRepository(this);
        setupActivityResultLaunchers();
        setupCategoryDropdown();
        setupPhotoRecycler();

        editingItemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);
        if (editingItemId > 0) {
            binding.tvTitle.setText(R.string.edit_item);
            binding.btnSave.setText(R.string.update);
            loadEditingItem();
        }

        binding.btnGallery.setOnClickListener(v -> openGallery());
        binding.btnCamera.setOnClickListener(v -> openCamera());
        binding.btnSave.setOnClickListener(v -> saveItem());
    }

    private void setupActivityResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    for (Uri uri : uris) {
                        grantPersistableRead(uri);
                        photoUris.add(uri.toString());
                    }
                    photoAdapter.setItems(photoUris);
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                        photoUris.add(pendingCameraUri.toString());
                        photoAdapter.setItems(photoUris);
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchCameraCapture();
                    } else {
                        Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupCategoryDropdown() {
        List<String> categories = getCategorySuggestions();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categories
        );
        binding.etCategory.setAdapter(adapter);
        binding.etCategory.setThreshold(0);
        binding.etCategory.setOnClickListener(v -> binding.etCategory.showDropDown());
        binding.etCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.etCategory.showDropDown();
            }
        });
    }

    private void setupPhotoRecycler() {
        photoAdapter = new PhotoPreviewAdapter(true, position -> {
            if (position >= 0 && position < photoUris.size()) {
                photoUris.remove(position);
                photoAdapter.setItems(photoUris);
            }
        });
        binding.rvPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvPhotos.setAdapter(photoAdapter);
    }

    private void loadEditingItem() {
        repository.getById(editingItemId, item -> {
            if (item == null) {
                finish();
                return;
            }
            editingItem = item;
            binding.etItemName.setText(item.itemName);
            binding.etLocation.setText(item.locationName);
            binding.etCategory.setText(item.category, false);
            binding.etMemo.setText(item.memo);
            binding.etTags.setText(item.tags == null ? "" : TextUtils.join(", ", item.tags));
            photoUris.clear();
            if (item.photoUris != null) {
                photoUris.addAll(item.photoUris);
            }
            photoAdapter.setItems(photoUris);
        });
    }

    private void openGallery() {
        galleryLauncher.launch(new String[]{"image/*"});
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCameraCapture() {
        try {
            File imageDir = new File(getCacheDir(), "images");
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }
            File imageFile = File.createTempFile("whereisit_", ".jpg", imageDir);
            pendingCameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );
            cameraLauncher.launch(pendingCameraUri);
        } catch (IOException e) {
            Toast.makeText(this, "카메라 실행에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveItem() {
        String itemName = safeText(binding.etItemName.getText());
        String location = safeText(binding.etLocation.getText());
        String category = safeText(binding.etCategory.getText());
        String memo = safeText(binding.etMemo.getText());
        String tags = safeText(binding.etTags.getText());

        if (itemName.isEmpty() || location.isEmpty() || category.isEmpty()) {
            Toast.makeText(this, "물건 이름, 보관 위치, 카테고리를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        saveCustomCategory(category);

        long now = System.currentTimeMillis();
        ItemEntity item = editingItem != null ? editingItem : new ItemEntity();
        item.itemName = itemName;
        item.locationName = location;
        item.category = category;
        item.memo = memo;
        item.updatedDate = now;
        if (item.createdDate == 0L) {
            item.createdDate = now;
        }
        item.photoUris = new ArrayList<>(photoUris);
        item.tags = parseTags(tags);

        if (editingItemId > 0) {
            repository.update(item, this::finish);
        } else {
            item.favorite = false;
            repository.insert(item, id -> finish());
        }
    }

    private List<String> parseTags(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        String[] parts = text.split(",");
        for (String part : parts) {
            String tag = part.trim();
            while (tag.startsWith("#")) {
                tag = tag.substring(1).trim();
            }
            if (!tag.isEmpty() && !result.contains(tag)) {
                result.add(tag);
            }
        }
        return TagUtil.normalize(result);
    }

    private String safeText(CharSequence source) {
        if (source == null) {
            return "";
        }
        return source.toString().trim();
    }

    private void grantPersistableRead(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private List<String> getCategorySuggestions() {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.add(getString(R.string.electronics));
        merged.add(getString(R.string.documents));
        merged.add(getString(R.string.camping));
        merged.add(getString(R.string.daily));
        merged.add(getString(R.string.car));
        merged.add(getString(R.string.others));

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> custom = preferences.getStringSet(KEY_CUSTOM_CATEGORIES, new LinkedHashSet<>());
        if (custom != null) {
            merged.addAll(custom);
        }
        return new ArrayList<>(merged);
    }

    private void saveCustomCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> existing = preferences.getStringSet(KEY_CUSTOM_CATEGORIES, new LinkedHashSet<>());
        LinkedHashSet<String> updated = new LinkedHashSet<>();
        if (existing != null) {
            updated.addAll(existing);
        }
        updated.add(category.trim());
        preferences.edit().putStringSet(KEY_CUSTOM_CATEGORIES, updated).apply();
    }
}
