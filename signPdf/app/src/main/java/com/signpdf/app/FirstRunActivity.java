package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.signpdf.app.util.SaveLocationPreferences;

/**
 * Launcher-only onboarding screen. It asks for the SignPDF PDF folder once,
 * then hands normal app startup to MainActivity.
 */
public class FirstRunActivity extends AppCompatActivity {

    private static final String PREFS = "signpdf_first_run";
    private static final String KEY_FOLDER_PROMPT_DONE = "folder_prompt_done";

    private ActivityResultLauncher<Intent> folderLauncher;
    private boolean dialogVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        folderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> handleFolderResult(result.getResultCode(), result.getData()));

        if (SaveLocationPreferences.hasCustomLocation(this)) {
            markPromptDone();
            openMain();
            return;
        }

        boolean promptDone = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_FOLDER_PROMPT_DONE, false);
        if (promptDone) {
            openMain();
            return;
        }

        showInitialFolderDialog();
    }

    private void showInitialFolderDialog() {
        if (dialogVisible || isFinishing()) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
            .setTitle(R.string.first_run_folder_title)
            .setMessage(R.string.first_run_folder_message)
            .setPositiveButton(R.string.first_run_choose_folder, (dialog, which) -> {
                dialogVisible = false;
                chooseFolder();
            })
            .setNegativeButton(R.string.first_run_later, (dialog, which) -> {
                dialogVisible = false;
                markPromptDone();
                openMain();
            })
            .setOnCancelListener(dialog -> {
                dialogVisible = false;
                markPromptDone();
                openMain();
            })
            .show();
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        folderLauncher.launch(intent);
    }

    private void handleFolderResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            showInitialFolderDialog();
            return;
        }

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The grant can still be used for the current session on providers
            // that do not support persisted permissions.
        }

        DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
        String label = folder == null ? null : folder.getName();
        if (label == null || label.trim().isEmpty()) label = uri.getLastPathSegment();
        if (label == null || label.trim().isEmpty()) label = getString(R.string.settings_save_location);

        SaveLocationPreferences.set(this, uri, label);
        markPromptDone();

        String selectedLabel = label;
        new AlertDialog.Builder(this)
            .setTitle(R.string.first_run_folder_done_title)
            .setMessage(getString(R.string.first_run_folder_done_message, selectedLabel))
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                Toast.makeText(this, R.string.first_run_folder_settings_hint, Toast.LENGTH_LONG).show();
                openMain();
            })
            .setCancelable(false)
            .show();
    }

    private void markPromptDone() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FOLDER_PROMPT_DONE, true)
            .apply();
    }

    private void openMain() {
        if (isFinishing()) return;
        Intent source = getIntent();
        Intent main = new Intent(this, MainActivity.class);
        if (source != null && source.getExtras() != null) {
            main.putExtras(source.getExtras());
        }
        startActivity(main);
        finish();
    }
}
