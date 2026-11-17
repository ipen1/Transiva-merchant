package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom navigation tunggal untuk seluruh sisi Merchant. */
public final class MerchantBottomNavigation {
    private static final String ACTIVE_BG = "#DDEAFF";
    private static final String ACTIVE_COLOR = "#075BDD";
    private static final String INACTIVE_COLOR = "#4F5D70";

    public enum ActiveItem { HOME, ORDERS, STORIES, REVIEWS, SETTINGS }

    private MerchantBottomNavigation() {}

    public static View build(Activity activity, ActiveItem activeItem) {
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 4));
        nav.setBackgroundColor(MerchantAppSettings.isDarkMode(activity) ? Color.parseColor("#0C1C2E") : Color.WHITE);
        nav.setElevation(dp(activity, 2));

        add(nav, item(activity, "Beranda", R.drawable.ic_merchant_home, ActiveItem.HOME, activeItem, MerchantDashboardActivity.class));
        add(nav, item(activity, "Aktivitas", R.drawable.ic_merchant_activity, ActiveItem.ORDERS, activeItem, MerchantOrdersActivity.class));
        add(nav, item(activity, "Cerita", R.drawable.ic_merchant_stories, ActiveItem.STORIES, activeItem, MerchantKabarMitraActivity.class));
        add(nav, item(activity, "Ulasan", R.drawable.ic_merchant_reviews, ActiveItem.REVIEWS, activeItem, MerchantReviewsActivity.class));
        add(nav, item(activity, "Pengaturan", R.drawable.ic_merchant_profile, ActiveItem.SETTINGS, activeItem, MerchantSettingsActivity.class));

        nav.setAlpha(0f);
        nav.setTranslationY(dp(activity, 10));
        nav.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        return nav;
    }

    public static ActiveItem resolve(Activity activity) {
        if (activity instanceof MerchantOrdersActivity) return ActiveItem.ORDERS;
        if (activity instanceof MerchantKabarMitraActivity) return ActiveItem.STORIES;
        if (activity instanceof MerchantReviewsActivity) return ActiveItem.REVIEWS;
        if (activity instanceof MerchantSettingsActivity || activity instanceof MerchantRestaurantProfileActivity || activity instanceof MerchantOperationsActivity || activity instanceof MerchantDiagnosticsActivity || activity instanceof MerchantDeviceSessionsActivity || activity instanceof MerchantPrivacyActivity || activity instanceof MerchantLocationActivity) return ActiveItem.SETTINGS;
        return ActiveItem.HOME;
    }

    private static void add(LinearLayout nav, View item) {
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private static View item(Activity activity, String label, int drawableId,
                             ActiveItem item, ActiveItem activeItem, Class<?> target) {
        boolean active = item == activeItem;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        boolean samePage = activity.getClass() == target
                && !(activity instanceof MerchantKabarMitraActivity && activity.getIntent().getBooleanExtra("show_followers",false));
        root.setClickable(!samePage); root.setFocusable(!samePage);
        root.setMinimumHeight(dp(activity,48));
        root.setContentDescription(label);

        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(MerchantAppSettings.isDarkMode(activity) ? Color.parseColor("#17334E") : Color.parseColor(ACTIVE_BG));
            bg.setCornerRadius(dp(activity, 14));
            root.setBackground(bg);

        }

        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawableId);
        boolean dark = MerchantAppSettings.isDarkMode(activity);
        int tint = Color.parseColor(active ? (dark ? "#85BFFF" : ACTIVE_COLOR)
                : (dark ? "#B7C9DB" : INACTIVE_COLOR));
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.setSelected(active);
        int iconDp = activity.getResources().getConfiguration().screenWidthDp < 380 ? 20 : 22;
        root.addView(icon, new LinearLayout.LayoutParams(dp(activity, iconDp), dp(activity, iconDp)));

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(11f);
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setTextColor(tint);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(activity, 2), 0, 0);
        root.addView(title, titleLp);

        if (!samePage) {
            root.setOnClickListener(v -> v.animate().scaleX(.90f).scaleY(.90f).setDuration(70L)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L)
                            .setInterpolator(new DecelerateInterpolator(1.7f))
                            .withEndAction(() -> open(activity, target)).start()).start());
        }
        return root;
    }

    private static void open(Activity activity, Class<?> target) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Intent i = new Intent(activity, target);
        if(target == MerchantKabarMitraActivity.class) i.putExtra("show_followers",false);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(i);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
