package com.signpdf.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.signpdf.app.converter.DocumentToPdfConverter;
import com.signpdf.app.converter.ImageToPdfConverter;
import com.signpdf.app.converter.WordToPdfConverter;
import com.signpdf.app.databinding.ActivityMainBinding;
import com.signpdf.app.viewer.PdfViewerActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "signpdf_prefs";
    private static final String PREF_RECENT = "recent_files";
    private static final int MAX_RECENT = 10;

    private ActivityMainBinding mBinding;
    private FilePickerHelper mFilePicker;
    private List<RecentFilesAdapter.RecentFileItem> mRecentItems;
    private RecentFilesAdapter mAdapter;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mFilePicker = new FilePickerHelper(this);
        setupRecentFiles();
        setupClickListeners();

        // 외부에서 파일이 열린 경우 처리
        handleIncomingIntent(getIntent());
    }

    private void setupRecentFiles() {
        mRecentItems = loadRecentFiles();
        mAdapter = new RecentFilesAdapter(mRecentItems);
        mBinding.rvRecentFiles.setLayoutManager(
            new LinearLayoutManager(this));
        mBinding.rvRecentFiles.setAdapter(mAdapter);

        updateRecentFilesVisibility();

        mAdapter.setOnItemClickListener(new RecentFilesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(RecentFilesAdapter.RecentFileItem item) {
                openFile(item.getUri(),
                    item.fileType.equals("PDF") ? "application/pdf" : "image/jpeg");
            }

            @Override
            public void onItemRemove(RecentFilesAdapter.RecentFileItem item, int position) {
                mRecentItems.remove(position);
                mAdapter.notifyItemRemoved(position);
                saveRecentFiles();
                updateRecentFilesVisibility();
            }
        });
    }

    private void setupClickListeners() {
        mBinding.btnOpenDocument.setOnClickListener(v -> {
            mFilePicker.openFilePicker(new FilePickerHelper.OnFilePickedListener() {
                @Override
                public void onFilePicked(android.net.Uri uri, String mimeType) {
                    openFile(uri, mimeType);
                }
                @Override
                public void onCancelled() {}
            });
        });
    }

    private void openFile(Uri uri, String mimeType) {
        if (FilePickerHelper.isPdf(mimeType)) {
            // PDF: 캐시에 복사 후 바로 편집
            showLoading(true);
            mExecutor.execute(() -> {
                try {
                    File cachedFile = copyToCacheDir(uri, getFileName(uri));
                    String displayName = getFileName(uri);
                    addToRecent(uri, displayName, "PDF");
                    runOnUiThread(() -> {
                        showLoading(false);
                        launchPdfViewer(cachedFile.getAbsolutePath(), displayName);
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, getString(R.string.file_not_found),
                            Toast.LENGTH_SHORT).show();
                    });
                }
            });

        } else if (FilePickerHelper.isImage(mimeType)) {
            // 이미지: PDF로 변환 후 편집
            showLoading(true);
            mExecutor.execute(() -> {
                try {
                    String fileName = getFileName(uri);
                    File imageFile = copyToCacheDir(uri, fileName);
                    File pdfFile = new File(getCacheDir(),
                        "converted_" + System.currentTimeMillis() + ".pdf");

                    new ImageToPdfConverter().convert(imageFile, pdfFile);
                    addToRecent(uri, fileName, "이미지");

                    runOnUiThread(() -> {
                        showLoading(false);
                        launchPdfViewer(pdfFile.getAbsolutePath(), fileName);
                    });
                } catch (IOException | DocumentToPdfConverter.ConversionException e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "이미지 변환 실패: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    });
                }
            });

        } else if (FilePickerHelper.isWord(mimeType)) {
            // Word: 안내 메시지 표시
            new AlertDialog.Builder(this)
                .setTitle("Word 문서 변환")
                .setMessage(WordToPdfConverter.LIMITATION_MESSAGE)
                .setPositiveButton("확인", null)
                .show();
        } else {
            Toast.makeText(this, getString(R.string.unsupported_format),
                Toast.LENGTH_SHORT).show();
        }
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
                if (path != null && path.toLowerCase().endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else {
                    mimeType = "image/jpeg";
                }
            }
            openFile(data, mimeType);
        }
    }

    // ==================== 파일 유틸리티 ====================

    private File copyToCacheDir(Uri uri, String fileName) throws IOException {
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
        File tempFile = new File(getCacheDir(), "open_" + System.currentTimeMillis()
            + "_" + safeName);
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            if (is == null) throw new IOException("파일을 열 수 없습니다");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
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
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "document.pdf";
    }

    // ==================== 최근 파일 ====================

    private void addToRecent(Uri uri, String name, String type) {
        String uriStr = uri.toString();
        // 중복 제거
        mRecentItems.removeIf(item -> item.uriString.equals(uriStr));
        mRecentItems.add(0, new RecentFilesAdapter.RecentFileItem(uriStr, name, type));
        // 최대 개수 유지
        while (mRecentItems.size() > MAX_RECENT) {
            mRecentItems.remove(mRecentItems.size() - 1);
        }
        saveRecentFiles();
        runOnUiThread(() -> {
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
            } catch (JSONException ignored) {}
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
                list.add(new RecentFilesAdapter.RecentFileItem(
                    obj.getString("uri"),
                    obj.getString("name"),
                    obj.getString("type")
                ));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    private void updateRecentFilesVisibility() {
        boolean hasItems = !mRecentItems.isEmpty();
        mBinding.tvRecentTitle.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        mBinding.rvRecentFiles.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        mBinding.tvNoRecentFiles.setVisibility(hasItems ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        mBinding.progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        mBinding.btnOpenDocument.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }
}
