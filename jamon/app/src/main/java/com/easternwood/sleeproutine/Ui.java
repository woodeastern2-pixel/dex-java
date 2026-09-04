package com.easternwood.sleeproutine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared, borderless visual language for the Jamon redesign. */
final class Ui {
    static final int BG = Color.rgb(3, 8, 23);
    static final int BG_TOP = Color.rgb(7, 20, 49);
    static final int CARD = Color.rgb(10, 21, 46);
    static final int CARD_SOFT = Color.rgb(13, 27, 56);
    static final int CARD_LIFT = Color.rgb(17, 34, 67);
    static final int TEXT = Color.rgb(247, 245, 255);
    static final int MUTED = Color.rgb(157, 167, 194);
    static final int ACCENT = Color.rgb(171, 136, 255);
    static final int ACCENT_DARK = Color.rgb(71, 48, 132);
    static final int ACCENT_BLUE = Color.rgb(104, 139, 255);
    static final int CYAN = Color.rgb(126, 225, 220);
    static final int MOON = Color.rgb(247, 205, 128);
    static final int PURPLE = ACCENT;
    static final int HAIRLINE = Color.argb(72, 128, 144, 184);

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.16f);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView button(Context context, CharSequence value, boolean primary) {
        TextView view = text(context, value, 15f, TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 56));
        view.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        view.setBackground(primary ? primaryBackground(context) : filledBackground(CARD_LIFT, 8, context));
        view.setClickable(true);
        view.setFocusable(true);
        view.setStateListAnimator(null);
        return view;
    }

    static GradientDrawable primaryBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(143, 91, 224), Color.rgb(105, 140, 255)}
        );
        drawable.setCornerRadius(dp(context, 8));
        return drawable;
    }

    static GradientDrawable filledBackground(int color, float radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    /** Compatibility helper: sections are now borderless filled surfaces. */
    static LinearLayout card(Context context) {
        return card(context, CARD, 8, Color.TRANSPARENT);
    }

    static LinearLayout card(Context context, int color, int radius, int ignoredBorder) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        layout.setBackground(filledBackground(color, Math.min(radius, 8), context));
        layout.setElevation(0f);
        return layout;
    }

    static LinearLayout reviewCard(Context context) {
        return card(context, Color.rgb(8, 25, 52), 6, Color.TRANSPARENT);
    }

    static LinearLayout heroCard(Context context) {
        return card(context, CARD, 8, Color.TRANSPARENT);
    }

    static TextView chip(Context context, CharSequence value, boolean selected) {
        TextView view = text(context, value, 13f, selected ? TEXT : MUTED, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setAutoSizeTextTypeUniformWithConfiguration(11, 13, 1, 2);
        view.setMinHeight(dp(context, 46));
        view.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        selectChip(view, selected, context);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    static void selectChip(TextView view, boolean selected, Context context) {
        view.setTextColor(selected ? TEXT : MUTED);
        view.setBackground(selected
                ? filledBackground(Color.argb(92, 137, 94, 225), 6, context)
                : filledBackground(Color.TRANSPARENT, 0, context));
        view.setCompoundDrawableTintList(ColorStateList.valueOf(selected ? ACCENT : MUTED));
    }

    static void selectMood(TextView view, boolean selected, Context context) {
        view.setTextColor(selected ? TEXT : MUTED);
        ColorDrawable clear = new ColorDrawable(Color.TRANSPARENT);
        if (!selected) {
            view.setBackground(clear);
            return;
        }
        GradientDrawable line = new GradientDrawable();
        line.setColor(ACCENT);
        Drawable[] layers = {clear, line};
        LayerDrawable underline = new LayerDrawable(layers);
        underline.setLayerGravity(1, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        underline.setLayerWidth(1, dp(context, 42));
        underline.setLayerHeight(1, dp(context, 2));
        view.setBackground(underline);
    }

    static TextView pill(Context context, CharSequence value, int textColor, int fillColor, int ignoredBorder) {
        TextView view = text(context, value, 11f, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        view.setBackground(filledBackground(fillColor, 6, context));
        return view;
    }

    static GradientDrawable circle(int color, int ignoredStrokeWidth, int ignoredStrokeColor, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    static GradientDrawable roundRect(int color, float radius, int strokeWidth, int strokeColor, Context context) {
        GradientDrawable drawable = filledBackground(color, Math.min(radius, 8), context);
        if (strokeWidth > 0 && Color.alpha(strokeColor) > 0) {
            drawable.setStroke(dp(context, strokeWidth), Color.argb(54, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)));
        }
        return drawable;
    }

    static LinearLayout column(Context context, int horizontalPadding) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, horizontalPadding), dp(context, 16), dp(context, horizontalPadding), dp(context, 112));
        return layout;
    }

    static LinearLayout nightColumn(Context context, int horizontalPadding) {
        return column(context, horizontalPadding);
    }

    static ScrollView screen(Context context) {
        NightScrollView view = new NightScrollView(context);
        view.setFillViewport(true);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setClipToPadding(false);
        return view;
    }

    static LinearLayout.LayoutParams matchWrap(Context context, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(context, topMargin);
        return params;
    }

    static LinearLayout.LayoutParams wrapWrap(Context context, int endMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = dp(context, endMargin);
        return params;
    }

    static View space(Context context, int height) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return view;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(HAIRLINE);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    static TextView label(Context context, CharSequence value) {
        TextView view = text(context, value, 11f, CYAN, true);
        view.setLetterSpacing(0.14f);
        return view;
    }

    static ImageView icon(Context context, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(R.drawable.jamon_icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setContentDescription(context.getString(R.string.app_name));
        image.setMinimumWidth(dp(context, sizeDp));
        image.setMinimumHeight(dp(context, sizeDp));
        return image;
    }

    static ImageView artwork(Context context, int drawableRes, int sizeDp, float radiusDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(drawableRes);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(filledBackground(CARD_LIFT, radiusDp, context));
        image.setClipToOutline(true);
        image.setMinimumWidth(dp(context, sizeDp));
        image.setMinimumHeight(dp(context, sizeDp));
        return image;
    }

    static ImageView lineIcon(Context context, int drawableRes, int tint, int contentDescriptionRes) {
        ImageView image = new ImageView(context);
        image.setImageResource(drawableRes);
        image.setColorFilter(tint);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setContentDescription(context.getString(contentDescriptionRes));
        image.setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
        return image;
    }

    static LinearLayout sectionHeader(final Activity activity, int titleRes) {
        return sectionHeader(activity, activity.getString(titleRes));
    }

    static LinearLayout sectionHeader(final Activity activity, CharSequence title) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = lineIcon(activity, R.drawable.ic_back, TEXT, R.string.back);
        back.setOnClickListener(v -> activity.finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView titleView = text(activity, title, 20f, TEXT, true);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(titleView, titleParams);
        row.addView(new View(activity), new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        return row;
    }

    static void prepareWindow(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(BG_TOP);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(0);
    }

    static FrameLayout withBottomNav(Activity activity, View content, int selected) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
        nav.setBackgroundColor(Color.rgb(5, 12, 29));
        int[] icons = {R.drawable.ic_nav_today, R.drawable.ic_nav_sound,
                R.drawable.ic_nav_history, R.drawable.ic_nav_settings};
        int[] labels = {R.string.nav_today, R.string.nav_sound, R.string.nav_history, R.string.settings};
        for (int i = 0; i < labels.length; i++) {
            final int destination = i;
            int color = i == selected ? ACCENT : MUTED;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            ImageView icon = lineIcon(activity, icons[i], color, labels[i]);
            item.addView(icon, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 34)));
            TextView label = text(activity, activity.getString(labels[i]), 11f, color, i == selected);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(-1, dp(activity, 20)));
            item.setMinimumHeight(dp(activity, 62));
            item.setOnClickListener(v -> navigate(activity, destination, selected));
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(activity, 62), 1f));
        }
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(nav, navParams);
        return root;
    }

    private static void navigate(Activity activity, int destination, int selected) {
        if (destination == selected) {
            return;
        }
        Class<?> target;
        if (destination == 0) {
            target = MainActivity.class;
        } else if (destination == 1) {
            target = SoundLibraryActivity.class;
        } else if (destination == 2) {
            target = HistoryActivity.class;
        } else {
            target = SettingsActivity.class;
        }
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private static final class NightScrollView extends ScrollView {
        NightScrollView(Context context) {
            super(context);
            setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{BG_TOP, BG, BG}
            ));
        }
    }
}
