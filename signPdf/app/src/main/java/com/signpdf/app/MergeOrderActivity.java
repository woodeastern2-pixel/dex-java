package com.signpdf.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

/** Reviews and reorders PDFs selected by the Android system document picker. */
public class MergeOrderActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_URIS = "selected_pdf_uris";

    private final ArrayList<Uri> orderedUris = new ArrayList<>();
    private final ArrayList<String> displayNames = new ArrayList<>();
    private final ArrayList<String> numberedNames = new ArrayList<>();

    private ArrayAdapter<String> adapter;
    private ListView listView;
    private TextView countView;
    private MaterialButton confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Uri> incoming = getIntent()
            .getParcelableArrayListExtra(EXTRA_SELECTED_URIS);
        if (incoming != null) orderedUris.addAll(incoming);
        if (orderedUris.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        for (Uri uri : orderedUris) displayNames.add(getFileName(uri));
        buildUi();
        refreshList(0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(20));
        root.setBackgroundColor(Color.parseColor("#F6F8FC"));

        TextView title = new TextView(this);
        title.setText(R.string.merge_order_title);
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#15233A"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(R.string.merge_order_hint);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#637083"));
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(6);
        root.addView(hint, hintParams);

        countView = new TextView(this);
        countView.setTextSize(14);
        countView.setTextColor(Color.parseColor("#2159C9"));
        countView.setTypeface(countView.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(14);
        countParams.bottomMargin = dp(8);
        root.addView(countView, countParams);

        adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_single_choice,
            numberedNames);
        listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout orderRow = new LinearLayout(this);
        orderRow.setOrientation(LinearLayout.HORIZONTAL);
        orderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams orderParams = matchWrap();
        orderParams.topMargin = dp(10);
        root.addView(orderRow, orderParams);

        MaterialButton up = button(R.string.move_up);
        MaterialButton down = button(R.string.move_down);
        MaterialButton remove = button(R.string.merge_order_remove);
        orderRow.addView(up, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        downParams.leftMargin = dp(6);
        orderRow.addView(down, downParams);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        removeParams.leftMargin = dp(6);
        orderRow.addView(remove, removeParams);

        up.setOnClickListener(v -> moveSelected(-1));
        down.setOnClickListener(v -> moveSelected(1));
        remove.setOnClickListener(v -> removeSelected());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = matchWrap();
        bottomParams.topMargin = dp(12);
        root.addView(bottom, bottomParams);

        MaterialButton cancel = button(R.string.cancel);
        cancel.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));

        confirmButton = button(R.string.confirm);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        confirmParams.leftMargin = dp(10);
        bottom.addView(confirmButton, confirmParams);
        confirmButton.setOnClickListener(v -> returnOrderedFiles());

        setContentView(root);
    }

    private void moveSelected(int direction) {
        int selected = listView.getCheckedItemPosition();
        if (selected == ListView.INVALID_POSITION) selected = 0;
        int target = selected + direction;
        if (target < 0 || target >= orderedUris.size()) return;

        Uri uri = orderedUris.remove(selected);
        orderedUris.add(target, uri);
        String name = displayNames.remove(selected);
        displayNames.add(target, name);
        refreshList(target);
    }

    private void removeSelected() {
        int selected = listView.getCheckedItemPosition();
        if (selected == ListView.INVALID_POSITION) selected = 0;
        if (selected < 0 || selected >= orderedUris.size()) return;

        orderedUris.remove(selected);
        displayNames.remove(selected);
        int next = orderedUris.isEmpty() ? -1 : Math.min(selected, orderedUris.size() - 1);
        refreshList(next);
    }

    private void refreshList(int selectedPosition) {
        numberedNames.clear();
        for (int i = 0; i < displayNames.size(); i++) {
            numberedNames.add((i + 1) + ".  " + displayNames.get(i));
        }
        adapter.notifyDataSetChanged();
        countView.setText(getString(R.string.merge_order_count, orderedUris.size()));

        boolean valid = orderedUris.size() >= 2;
        confirmButton.setEnabled(valid);
        confirmButton.setAlpha(valid ? 1f : 0.45f);

        listView.clearChoices();
        if (selectedPosition >= 0 && selectedPosition < orderedUris.size()) {
            listView.setItemChecked(selectedPosition, true);
            listView.setSelection(selectedPosition);
        }
    }

    private void returnOrderedFiles() {
        if (orderedUris.size() < 2) {
            Toast.makeText(this, R.string.merge_pick_two, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent data = new Intent();
        data.putParcelableArrayListExtra(EXTRA_SELECTED_URIS, new ArrayList<>(orderedUris));
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (RuntimeException ignored) { }
        }
        if (result == null || result.trim().isEmpty()) result = uri.getLastPathSegment();
        return result == null || result.trim().isEmpty() ? "PDF" : result;
    }

    private MaterialButton button(int textRes) {
        MaterialButton button = new MaterialButton(this);
        button.setText(textRes);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
