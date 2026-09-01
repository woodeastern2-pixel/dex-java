package com.signpdf.app.util;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.signpdf.app.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves generated PDFs as new files without overwriting the original document. */
public class SaveManager {

    private static final Pattern SPLIT_NAME = Pattern.compile("^split_p(\\d+)_(.+)$");
    private static final ThreadLocal<String> SPLIT_BASE_NAME = new ThreadLocal<>();

    private final Context context;
    private final Activity activity;

    public SaveManager(Context context) {
        this.context = context.getApplicationContext();
        this.activity = context instanceof Activity ? (Activity) context : null;
    }

    /** Saves an edited PDF using the regular signed_ filename convention. */
    public Uri save(File tempFile, String originalName) throws IOException {
        String baseName = removeExtension(originalName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        return saveGenerated(tempFile, "signed_" + baseName + "_" + timeStamp + ".pdf");
    }

    /**
     * Saves any generated PDF. Before writing, the user can edit the proposed
     * filename. Free-tier successful output operations consume one monthly action;
     * Pro bypasses the quota.
     */
    public Uri saveGenerated(File tempFile, String fileName) throws IOException {
        if (!UsageQuotaManager.canUseAction()) {
            throw new IOException(UsageQuotaManager.getLimitReachedMessage());
        }
        if (tempFile == null || !tempFile.exists() || tempFile.length() <= 0L) {
            throw new IOException("Generated PDF is empty.");
        }

        String chosenName = resolveOutputName(fileName);
        String safeName = sanitizePdfFileName(chosenName);
        Uri savedUri;

        Uri configuredTree = SaveLocationPreferences.getTreeUri(context);
        if (configuredTree != null) {
            savedUri = saveToDocumentTree(tempFile, safeName, configuredTree);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savedUri = saveViaMediaStore(tempFile, safeName);
        } else {
            savedUri = saveToExternalFiles(tempFile, safeName);
        }

        UsageQuotaManager.recordSuccessfulAction();
        return savedUri;
    }

    /**
     * Split operations create many files. The first split page asks for a base
     * name, then subsequent pages use that base with _2, _3... automatically.
     */
    private String resolveOutputName(String proposedName) {
        String defaultName = sanitizePdfFileName(proposedName);
        Matcher matcher = SPLIT_NAME.matcher(defaultName);
        if (matcher.matches()) {
            int pageNumber;
            try {
                pageNumber = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                pageNumber = 1;
            }

            if (pageNumber == 1) {
                String cleanerDefault = defaultName.replaceFirst("^split_p1_", "split_");
                String chosen = requestFileName(cleanerDefault);
                String base = removeExtension(chosen);
                SPLIT_BASE_NAME.set(base);
                return base + "_1.pdf";
            }

            String base = SPLIT_BASE_NAME.get();
            if (base != null && !base.trim().isEmpty()) {
                return base + "_" + pageNumber + ".pdf";
            }
        }
        return requestFileName(defaultName);
    }

    /**
     * Save calls currently run on worker executors. We marshal the filename dialog
     * to the Activity and wait for the choice so every save path shares one UX.
     * The preceding action-confirmation dialog already provides cancellation;
     * dismissing this dialog simply keeps the proposed default filename.
     */
    private String requestFileName(String defaultName) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return defaultName;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return defaultName;
        }

        AtomicReference<String> result = new AtomicReference<>(defaultName);
        AtomicBoolean resolved = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                latch.countDown();
                return;
            }
            try {
                EditText input = new EditText(activity);
                input.setSingleLine(true);
                input.setText(defaultName);
                input.setSelectAllOnFocus(true);
                input.selectAll();

                LinearLayout container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);
                int horizontal = dp(activity, 22);
                container.setPadding(horizontal, dp(activity, 6), horizontal, 0);
                container.addView(input, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

                AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.save_file_name_title)
                    .setMessage(activity.getString(
                        R.string.save_file_name_message,
                        getConfiguredLocationLabel()))
                    .setView(container)
                    .setPositiveButton(R.string.save, (d, w) -> {
                        String value = input.getText() == null
                            ? defaultName
                            : input.getText().toString().trim();
                        if (value.isEmpty()) value = defaultName;
                        result.set(value);
                        resolveLatch(resolved, latch);
                    })
                    .setNegativeButton(R.string.use_default_name, (d, w) -> {
                        result.set(defaultName);
                        resolveLatch(resolved, latch);
                    })
                    .create();

                dialog.setOnCancelListener(d -> {
                    result.set(defaultName);
                    resolveLatch(resolved, latch);
                });
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
                input.requestFocus();
            } catch (RuntimeException error) {
                resolveLatch(resolved, latch);
            }
        });

        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return defaultName;
        }
        return result.get();
    }

    private void resolveLatch(AtomicBoolean resolved, CountDownLatch latch) {
        if (resolved.compareAndSet(false, true)) latch.countDown();
    }

    private Uri saveToDocumentTree(File tempFile, String fileName, Uri treeUri)
        throws IOException {
        DocumentFile tree;
        try {
            tree = DocumentFile.fromTreeUri(context, treeUri);
        } catch (RuntimeException error) {
            throw new IOException(context.getString(R.string.save_location_access_failed), error);
        }
        if (tree == null || !tree.exists() || !tree.isDirectory() || !tree.canWrite()) {
            throw new IOException(context.getString(R.string.save_location_access_failed));
        }

        String uniqueName = uniqueTreeName(tree, fileName);
        DocumentFile output;
        try {
            output = tree.createFile("application/pdf", uniqueName);
        } catch (RuntimeException error) {
            throw new IOException(context.getString(R.string.save_location_access_failed), error);
        }
        if (output == null) {
            throw new IOException(context.getString(R.string.save_location_access_failed));
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(output.getUri(), "w");
             FileInputStream fis = new FileInputStream(tempFile)) {
            if (os == null) throw new IOException("Could not open output stream.");
            copy(fis, os);
        } catch (IOException error) {
            try { output.delete(); } catch (RuntimeException ignored) { }
            throw error;
        }
        return output.getUri();
    }

    private String uniqueTreeName(DocumentFile tree, String fileName) {
        String candidate = fileName;
        String base = removeExtension(fileName);
        int index = 2;
        while (tree.findFile(candidate) != null) {
            candidate = base + "_" + index + ".pdf";
            index++;
        }
        return candidate;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri saveViaMediaStore(File tempFile, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/SignPDF");

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri fileUri = context.getContentResolver().insert(collection, values);
        if (fileUri == null) {
            throw new IOException("MediaStore file creation failed.");
        }

        try (OutputStream os = context.getContentResolver().openOutputStream(fileUri);
             FileInputStream fis = new FileInputStream(tempFile)) {
            if (os == null) throw new IOException("Could not open output stream.");
            copy(fis, os);
        } catch (IOException e) {
            context.getContentResolver().delete(fileUri, null, null);
            throw e;
        }
        return fileUri;
    }

    private Uri saveToExternalFiles(File tempFile, String fileName) throws IOException {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create output directory.");
        }

        File outputFile = uniqueFile(dir, fileName);
        try (FileInputStream fis = new FileInputStream(tempFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            copy(fis, fos);
        }
        return Uri.fromFile(outputFile);
    }

    private void copy(FileInputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[8192];
        int read;
        while ((read = input.read(buf)) != -1) output.write(buf, 0, read);
    }

    public String getDisplayPath(Uri savedUri) {
        if (savedUri == null) return "";
        if (SaveLocationPreferences.hasCustomLocation(context)) {
            String label = SaveLocationPreferences.getLabel(context);
            return label == null || label.trim().isEmpty() ? "선택한 폴더" : label;
        }
        if ("file".equals(savedUri.getScheme())) return savedUri.getPath();
        return "Download/SignPDF";
    }

    public String getConfiguredLocationLabel() {
        if (SaveLocationPreferences.hasCustomLocation(context)) {
            String label = SaveLocationPreferences.getLabel(context);
            if (label != null && !label.trim().isEmpty()) return label;
        }
        return context.getString(R.string.settings_save_location_default);
    }

    public String timestampedName(String prefix, String originalName) {
        String baseName = removeExtension(originalName);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        return prefix + baseName + "_" + timeStamp + ".pdf";
    }

    private String sanitizePdfFileName(String fileName) {
        String name = fileName == null ? "SignPDF.pdf" : fileName.trim();
        if (name.isEmpty()) name = "SignPDF.pdf";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        return name;
    }

    private File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;

        String base = removeExtension(fileName);
        int index = 2;
        do {
            candidate = new File(dir, base + "_" + index + ".pdf");
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private String removeExtension(String filename) {
        if (filename == null) return "document";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) return filename.substring(0, lastDot);
        return filename;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
