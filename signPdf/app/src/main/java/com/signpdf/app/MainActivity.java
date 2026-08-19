package com.signpdf.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.signpdf.app.converter.DocumentToPdfConverter;
import com.signpdf.app.converter.ImageToPdfConverter;
import com.signpdf.app.databinding.ActivityMainBinding;
import com.signpdf.app.monetization.AdsConsentManager;
import com.signpdf.app.monetization.ProBillingManager;
import com.signpdf.app.util.PdfSecurityManager;
import com.signpdf.app.viewer.PdfViewerActivity;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "extra_section";
    public static final String SECTION_RECENT = "recent";
    public static final String SECTION_TOOLS = "tools";

    private static final String PREF_NAME = "signpdf_prefs";
    private static final String PREF_RECENT = "recent_files";
    private static final int MAX_RECENT = 10;

    private ActivityMainBinding mBinding;
    private FilePickerHelper mFilePicker;
    private List<RecentFilesAdapter.RecentFileItem> mRecentItems;
    private RecentFilesAdapter mAdapter;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private AdsConsentManager mAdsConsentManager;
    private ProBillingManager mBillingManager;
    private ProBillingManager.State mBillingState;
    private AdView mBannerAd;
    private boolean mConsentResolved = false;
    private boolean mAdsAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        PdfSecurityManager.initialize(this);
        mFilePicker = new FilePickerHelper(this);
        setupRecentFiles();
        setupClickListeners();
        setupMonetization();
        handleIncomingIntent(getIntent());
        handleNavigationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        handleNavigationIntent(intent);
    }

    private void setupRecentFiles() {
        mRecentItems = loadRecentFiles();
        mAdapter = new RecentFilesAdapter(mRecentItems);
        mBinding.rvRecentFiles.setLayoutManager(new LinearLayoutManager(this));
        mBinding.rvRecentFiles.setAdapter(mAdapter);
        updateRecentFilesVisibility();

        mAdapter.setOnItemClickListener(new RecentFilesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(RecentFilesAdapter.RecentFileItem item) {
                boolean isPdf = "PDF".equals(item.fileType);
                openFile(item.getUri(), isPdf ? "application/pdf" : "image/jpeg");
            }

            @Override
            public void onItemRemove(RecentFilesAdapter.RecentFileItem item, int position) {
                if (position < 0 || position >= mRecentItems.size()) return;
                mRecentItems.remove(position);
                mAdapter.notifyDataSetChanged();
                saveRecentFiles();
                updateRecentFilesVisibility();
            }
        });
    }

    private void setupClickListeners() {
        mBinding.btnOpenDocument.setOnClickListener(v -> openPdfPicker());
        mBinding.btnImportImage.setOnClickListener(v -> openImagePicker());
        mBinding.quickSign.setOnClickListener(v -> openPdfPicker());
        mBinding.quickImagePdf.setOnClickListener(v -> openImagePicker());
        mBinding.quickPasswordPdf.setOnClickListener(v -> openPdfPicker());

        mBinding.btnSettingsHeader.setOnClickListener(v -> openSettings());
        mBinding.navSettings.setOnClickListener(v -> openSettings());
        mBinding.navHome.setOnClickListener(v -> mBinding.homeScroll.smoothScrollTo(0, 0));
        mBinding.navRecent.setOnClickListener(v -> scrollToSection(mBinding.sectionRecent));
        mBinding.navTools.setOnClickListener(v -> scrollToSection(mBinding.sectionTools));

        mBinding.btnViewAllRecent.setOnClickListener(v -> {
            mAdapter.setExpanded(true);
            mBinding.btnViewAllRecent.setVisibility(View.GONE);
            scrollToSection(mBinding.sectionRecent);
        });
    }

    private void openPdfPicker() {
        mFilePicker.openPdfPicker(new FilePickerHelper.OnFilePickedListener() {
            @Override
            public void onFilePicked(Uri uri, String mimeType) {
                openFile(uri, "application/pdf");
            }

            @Override
            public void onCancelled() { }
        });
    }

    private void openImagePicker() {
        mFilePicker.openImagePicker(new FilePickerHelper.OnFilePickedListener() {
            @Override
            public void onFilePicked(Uri uri, String mimeType) {
                openFile(uri, mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType);
            }

            @Override
            public void onCancelled() { }
        });
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void scrollToSection(View section) {
        mBinding.homeScroll.post(() ->
            mBinding.homeScroll.smoothScrollTo(0, Math.max(0, section.getTop() - dpToPx(12))));
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent == null) return;
        String section = intent.getStringExtra(EXTRA_SECTION);
        if (SECTION_RECENT.equals(section)) {
            scrollToSection(mBinding.sectionRecent);
        } else if (SECTION_TOOLS.equals(section)) {
            scrollToSection(mBinding.sectionTools);
        }
        intent.removeExtra(EXTRA_SECTION);
    }

    private void setupMonetization() {
        mAdsConsentManager = new AdsConsentManager(this);
        mBillingManager = new ProBillingManager(this, this::renderBillingState);

        mBinding.btnPro.setOnClickListener(v -> {
            if (mBillingState != null && mBillingState.pro) {
                Toast.makeText(this, R.string.pro_active, Toast.LENGTH_SHORT).show();
                return;
            }
            mBillingManager.launchPurchase();
        });

        mBinding.btnRestorePurchase.setOnClickListener(v -> mBillingManager.refreshPurchases());
        mBinding.btnAdPrivacy.setOnClickListener(v ->
            mAdsConsentManager.showPrivacyOptions(this, () ->
                mAdsConsentManager.gatherConsent(this, this::onConsentResolved)));

        mBillingManager.start();
        mAdsConsentManager.gatherConsent(this, this::onConsentResolved);
    }

    private void onConsentResolved(boolean allowed) {
        mConsentResolved = true;
        mAdsAllowed = allowed;
        updatePrivacyOptionsVisibility();
        updateBannerVisibility();
    }

    private void renderBillingState(ProBillingManager.State state) {
        mBillingState = state;
        mBinding.tvProStatus.setText(
            state.priceText == null || state.priceText.isEmpty()
                ? state.message
                : state.message + " · " + state.priceText);

        if (state.pro) {
            mBinding.btnPro.setText(R.string.pro_active);
            mBinding.btnPro.setEnabled(false);
        } else {
            String buttonText = getString(R.string.pro_buy);
            if (state.priceText != null && !state.priceText.isEmpty()) {
                buttonText += " · " + state.priceText;
            }
            mBinding.btnPro.setText(buttonText);
            mBinding.btnPro.setEnabled(state.ready);
        }

        mBinding.btnRestorePurchase.setEnabled(!state.pro);
        updateBannerVisibility();
    }

    private void updatePrivacyOptionsVisibility() {
        if (mAdsConsentManager == null) return;
        mBinding.btnAdPrivacy.setVisibility(
            mAdsConsentManager.isPrivacyOptionsRequired() ? View.VISIBLE : View.GONE);
    }

    private void updateBannerVisibility() {
        boolean billingReady = mBillingState != null && mBillingState.ready;
        boolean isPro = mBillingState != null && mBillingState.pro;
        boolean shouldShow = mConsentResolved && mAdsAllowed && billingReady && !isPro;

        if (!shouldShow) {
            mBinding.adContainer.setVisibility(View.GONE);
            if (isPro) destroyBanner();
            return;
        }

        if (mBannerAd == null) {
            mBannerAd = new AdView(this);
            mBannerAd.setAdSize(AdSize.BANNER);
            mBannerAd.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            mBinding.adContainer.removeAllViews();
            mBinding.adContainer.addView(mBannerAd);
            mBannerAd.loadAd(new AdRequest.Builder().build());
        }
        mBinding.adContainer.setVisibility(View.VISIBLE);
    }

    private void destroyBanner() {
        if (mBannerAd != null) {
            mBannerAd.destroy();
            mBannerAd = null;
        }
        if (mBinding != null) {
            mBinding.adContainer.removeAllViews();
            mBinding.adContainer.setVisibility(View.GONE);
        }
    }

    private void openFile(Uri uri, String mimeType) {
        if (FilePickerHelper.isPdf(mimeType)) {
            openPdfFile(uri);
        } else if (FilePickerHelper.isImage(mimeType)) {
            showLoading(true);
            mExecutor.execute(() -> {
                File imageFile = null;
                try {
                    String fileName = getFileName(uri);
                    imageFile = copyToCacheDir(uri, fileName);
                    File pdfFile = new File(getCacheDir(),
                        "converted_" + System.currentTimeMillis() + ".pdf");

                    new ImageToPdfConverter().convert(imageFile, pdfFile);
                    addToRecent(uri, fileName, "IMAGE");

                    runOnUiThread(() -> {
                        showLoading(false);
                        launchPdfViewer(pdfFile.getAbsolutePath(), fileName);
                    });
                } catch (IOException | DocumentToPdfConverter.ConversionException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this,
                            getString(R.string.image_convert_failed, safeErrorMessage(e)),
                            Toast.LENGTH_LONG).show();
                    });
                } finally {
                    if (imageFile != null) {
                        //noinspection ResultOfMethodCallIgnored
                        imageFile.delete();
                    }
                }
            });
        } else {
            Toast.makeText(this, R.string.unsupported_format, Toast.LENGTH_SHORT).show();
        }
    }

    private void openPdfFile(Uri uri) {
        showLoading(true);
        mExecutor.execute(() -> {
            try {
                String displayName = getFileName(uri);
                File cachedFile = copyToCacheDir(uri, displayName);
                boolean passwordRequired = PdfSecurityManager.requiresPassword(cachedFile);

                runOnUiThread(() -> {
                    showLoading(false);
                    if (passwordRequired) {
                        showPdfPasswordDialog(uri, cachedFile, displayName);
                    } else {
                        addToRecent(uri, displayName, "PDF");
                        launchPdfViewer(cachedFile.getAbsolutePath(), displayName);
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this,
                        getString(R.string.pdf_open_failed, safeErrorMessage(e)),
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showPdfPasswordDialog(Uri originalUri, File cachedFile, String displayName) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(R.string.pdf_password_hint);
        inputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText passwordInput = new TextInputEditText(inputLayout.getContext());
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputLayout.addView(passwordInput, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout container = new FrameLayout(this);
        int horizontalMargin = dpToPx(24);
        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.leftMargin = horizontalMargin;
        inputParams.rightMargin = horizontalMargin;
        container.addView(inputLayout, inputParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.pdf_password_title)
            .setMessage(getString(R.string.pdf_password_message, displayName))
            .setView(container)
            .setNegativeButton(R.string.cancel, (d, which) -> cachedFile.delete())
            .setPositiveButton(R.string.open_file, null)
            .create();

        dialog.setOnCancelListener(d -> cachedFile.delete());
        dialog.setOnShowListener(ignored -> {
            dialog.setCanceledOnTouchOutside(false);
            passwordInput.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = passwordInput.getText() == null
                    ? "" : passwordInput.getText().toString();

                inputLayout.setError(null);
                setPasswordDialogBusy(dialog, passwordInput, true);

                mExecutor.execute(() -> {
                    try {
                        PdfSecurityManager.unlockCachedCopy(cachedFile, password);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            addToRecent(originalUri, displayName, "PDF");
                            dialog.dismiss();
                            launchPdfViewer(cachedFile.getAbsolutePath(), displayName);
                        });
                    } catch (InvalidPasswordException e) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            inputLayout.setError(getString(R.string.pdf_password_incorrect));
                            setPasswordDialogBusy(dialog, passwordInput, false);
                            passwordInput.selectAll();
                        });
                    } catch (IOException e) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            inputLayout.setError(getString(
                                R.string.pdf_unlock_failed, safeErrorMessage(e)));
                            setPasswordDialogBusy(dialog, passwordInput, false);
                        });
                    }
                });
            });
        });
        dialog.show();
    }

    private void setPasswordDialogBusy(
        AlertDialog dialog,
        TextInputEditText passwordInput,
        boolean busy
    ) {
        passwordInput.setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
            busy ? R.string.pdf_password_checking : R.string.open_file);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String safeErrorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? getString(R.string.unknown_error) : message;
    }

    private void launchPdfViewer(String pdfPath, String displayName) {
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra(PdfViewerActivity.EXTRA_PDF_PATH, pdfPath);
        intent.putExtra(PdfViewerActivity.EXTRA_DISPLAY_NAME, displayName);
        startActivity(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String mimeType = getContentResolver().getType(data);
            if (mimeType == null) {
                String path = data.getPath();
                if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else {
                    mimeType = "image/jpeg";
                }
            }
            openFile(data, mimeType);
        }
    }

    private File copyToCacheDir(Uri uri, String fileName) throws IOException {
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
        File tempFile = new File(getCacheDir(),
            "open_" + System.currentTimeMillis() + "_" + safeName);
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            if (is == null) throw new IOException(getString(R.string.file_not_found));
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
        }
        return tempFile;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result != null ? result : "document.pdf";
    }

    private void addToRecent(Uri uri, String name, String type) {
        String uriStr = uri.toString();
        mRecentItems.removeIf(item -> item.uriString.equals(uriStr));
        mRecentItems.add(0, new RecentFilesAdapter.RecentFileItem(uriStr, name, type));
        while (mRecentItems.size() > MAX_RECENT) {
            mRecentItems.remove(mRecentItems.size() - 1);
        }
        saveRecentFiles();
        runOnUiThread(() -> {
            mAdapter.setExpanded(false);
            mAdapter.notifyDataSetChanged();
            updateRecentFilesVisibility();
        });
    }

    private void saveRecentFiles() {
        JSONArray arr = new JSONArray();
        for (RecentFilesAdapter.RecentFileItem item : mRecentItems) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("uri", item.uriString);
                obj.put("name", item.displayName);
                obj.put("type", item.fileType);
                arr.put(obj);
            } catch (JSONException ignored) { }
        }
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putString(PREF_RECENT, arr.toString()).apply();
    }

    private List<RecentFilesAdapter.RecentFileItem> loadRecentFiles() {
        List<RecentFilesAdapter.RecentFileItem> list = new ArrayList<>();
        String json = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(PREF_RECENT, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String type = obj.getString("type");
                if ("이미지".equals(type)) type = "IMAGE";
                list.add(new RecentFilesAdapter.RecentFileItem(
                    obj.getString("uri"), obj.getString("name"), type));
            }
        } catch (JSONException ignored) { }
        return list;
    }

    private void updateRecentFilesVisibility() {
        boolean hasItems = !mRecentItems.isEmpty();
        mBinding.tvRecentTitle.setVisibility(View.VISIBLE);
        mBinding.rvRecentFiles.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        mBinding.emptyRecentContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        mBinding.tvNoRecentFiles.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        mBinding.btnViewAllRecent.setVisibility(
            hasItems && mRecentItems.size() > 3 && !mAdapter.isExpanded()
                ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        mBinding.progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        mBinding.btnOpenDocument.setEnabled(!show);
        mBinding.btnImportImage.setEnabled(!show);
        mBinding.quickSign.setEnabled(!show);
        mBinding.quickImagePdf.setEnabled(!show);
        mBinding.quickPasswordPdf.setEnabled(!show);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBillingManager != null) mBillingManager.refreshPurchases();
    }

    @Override
    protected void onDestroy() {
        destroyBanner();
        if (mBillingManager != null) mBillingManager.stop();
        mExecutor.shutdown();
        super.onDestroy();
    }
}
