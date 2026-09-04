package com.easternwood.sleeproutine;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Minimal aggregate history, backed by the recovered local session contract. */
public class HistoryActivity extends JamonActivity {

    private View createScreen() {
        ScrollView scroll = Ui.screen(this);
        LinearLayout column = Ui.column(this, 20);
        scroll.addView(column, new FrameLayout.LayoutParams(-1, -2));

        column.addView(Ui.sectionHeader(this, R.string.history_title));
        column.addView(Ui.text(this, getString(R.string.history_heading), 27, Ui.TEXT, true), Ui.matchWrap(this, 18));
        column.addView(Ui.text(this, getString(R.string.history_support), 14, Ui.MUTED, false), Ui.matchWrap(this, 8));

        LinearLayout totals = new LinearLayout(this);
        totals.setOrientation(LinearLayout.HORIZONTAL);
        totals.setPadding(0, Ui.dp(this, 33), 0, Ui.dp(this, 29));
        totals.addView(metric(getString(R.string.history_total),
                getString(R.string.history_minutes_value, Prefs.getTotalMinutes(this))),
                new LinearLayout.LayoutParams(0, -2, 1));
        View divider = new View(this);
        divider.setBackgroundColor(Ui.HAIRLINE);
        totals.addView(divider, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 74)));
        totals.addView(metric(getString(R.string.history_routines),
                getString(R.string.history_routines_value, Prefs.getRoutineCount(this))),
                new LinearLayout.LayoutParams(0, -2, 1));
        column.addView(totals, Ui.matchWrap(this, 16));
        column.addView(Ui.divider(this));

        LinearLayout rhythm = new LinearLayout(this);
        rhythm.setOrientation(LinearLayout.HORIZONTAL);
        rhythm.setGravity(Gravity.BOTTOM);
        rhythm.setPadding(0, Ui.dp(this, 35), 0, Ui.dp(this, 20));
        int completed = Math.min(7, Prefs.getRoutineCount(this));
        for (int i = 0; i < 7; i++) {
            View bar = new View(this);
            bar.setBackground(Ui.filledBackground(i < completed ? Ui.ACCENT : Ui.CARD_LIFT, 3, this));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    Ui.dp(this, i < completed ? 34 + (i % 3) * 12 : 12), 1);
            if (i > 0) params.leftMargin = Ui.dp(this, 8);
            rhythm.addView(bar, params);
        }
        column.addView(rhythm);
        TextView empty = Ui.text(this, getString(R.string.history_empty), 13, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(Prefs.getRoutineCount(this) == 0 ? View.VISIBLE : View.GONE);
        column.addView(empty, Ui.matchWrap(this, 18));
        return Ui.withBottomNav(this, scroll, 2);
    }

    private LinearLayout metric(CharSequence label, CharSequence value) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.addView(Ui.text(this, value, 31, Ui.TEXT, true));
        TextView caption = Ui.text(this, label, 12, Ui.MUTED, false);
        caption.setGravity(Gravity.CENTER);
        block.addView(caption, Ui.matchWrap(this, 7));
        return block;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareWindow(this);
        setContentView(createScreen());
    }
}
