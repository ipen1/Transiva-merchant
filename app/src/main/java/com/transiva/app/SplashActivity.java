package com.transiva.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Native branded launch. Security and resource synchronization retain their existing policy. */
public class SplashActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable launch = this::routeNext;
    private boolean routed, securityCheckStarted, ready, resumed;
    private long shownAt;
    private TextView statusText;
    private LinearLayout hero;
    private ProgressBar loading;
    private boolean animate;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        shownAt = SystemClock.elapsedRealtime();
        animate = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.parseColor("#06142E"));
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0A244A"), Color.parseColor("#06142E"), Color.parseColor("#040B1D")}));
        // Radial light is a drawable, with no continuously redrawn full-screen effects.
        View glow = new View(this);
        GradientDrawable light = new GradientDrawable();
        light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        light.setGradientRadius(dp(230));
        light.setColors(new int[]{0x403B93FF, 0x000A244A});
        glow.setBackground(light);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(dp(460), dp(460), Gravity.CENTER);
        root.addView(glow, glowLp);
        glow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Weighted vertical slots keep branding and footer separate on short/landscape screens.
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean compact = getResources().getConfiguration().screenHeightDp < 520;
        column.setPadding(dp(24), dp(compact ? 8 : 12), dp(24), dp(compact ? 8 : 18));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        View topSpace = new View(this);
        column.addView(topSpace, new LinearLayout.LayoutParams(1, 0, .9f));
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        column.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        int logoSize = compact ? 64 : 142;
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.transiva_icon_512);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Transiva");
        hero.addView(logo, new LinearLayout.LayoutParams(dp(logoSize), dp(logoSize)));

        TextView merchant = text("MERCHANT", 12, "sans-serif-medium", 0xFFD3E9FF);
        merchant.setLetterSpacing(.24f);
        merchant.setPadding(dp(17), dp(8), dp(14), dp(8));
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(0x183B93FF); badge.setCornerRadius(dp(30)); badge.setStroke(dp(1), 0x4568B1FF);
        merchant.setBackground(badge);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.topMargin = dp(compact ? 8 : 24);
        hero.addView(merchant, badgeLp);
        TextView title = text("Tumbuh bersama Transiva", compact ? 20 : 24, "sans-serif-medium", Color.WHITE);
        title.setPadding(0, dp(compact ? 10 : 16), 0, dp(compact ? 6 : 8));
        hero.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text("Bisnis lokal, peluang besar.", 14, "sans-serif", 0xFFB7CCE5);
        hero.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        if (compact) subtitle.setVisibility(View.GONE);
        column.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF79C6FF));
        loading.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF203B60));
        loading.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(loading, new LinearLayout.LayoutParams(dp(96), dp(4)));
        statusText = text("Menyiapkan ruang usaha Anda…", 12, "sans-serif", 0xFFC1D2E8);
        statusText.setPadding(0, dp(compact ? 8 : 12), 0, dp(compact ? 10 : 20));
        column.addView(statusText, new LinearLayout.LayoutParams(-1, -2));
        TextView footer = text("KARYA DIGITAL INDONESIA", 10, "sans-serif-medium", 0xFF94ADC9);
        footer.setLetterSpacing(.13f);
        if (compact) footer.setVisibility(View.GONE);
        column.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        MerchantWindowInsets.apply(this, root);
        if (animate && state == null) {
            hero.setAlpha(0f); hero.setTranslationY(dp(14));
            hero.animate().alpha(1f).translationY(0f).setDuration(650)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        // Start the existing checks immediately; the visual hold overlaps their work.
        main.post(this::startSecurityCheck);
    }

    private TextView text(String value, int size, String family, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean usable() { return !isFinishing() && !isDestroyed(); }

    private void startSecurityCheck() {
        if (!usable() || routed || securityCheckStarted || ready || !resumed) return;
        securityCheckStarted = true;
        SessionManager session = new SessionManager(this);
        String role = session.getRole() == null ? "" : session.getRole().trim().toLowerCase(java.util.Locale.ROOT);
        if (!session.isLoggedIn() || !"merchant".equals(role)) {
            continueToApp();
            return;
        }
        statusText.setText("Memeriksa keamanan perangkat…");
        RootSecurityGuard.checkBeforeContinue(this, this::continueToApp);
    }

    private void continueToApp() {
        if (!usable() || routed || ready) return;
        ready = true;
        statusText.setText("Ruang usaha Anda siap dibuka");
        MerchantResourceUpdater.checkAsync(this, false, null);
        scheduleRoute();
    }

    private void scheduleRoute() {
        main.removeCallbacks(launch);
        if (!ready || !resumed || !usable() || routed) return;
        long minimum = animate ? 1500L : 0L;
        main.postDelayed(launch, Math.max(0L, minimum - (SystemClock.elapsedRealtime() - shownAt)));
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        loading.setVisibility(animate ? View.VISIBLE : View.INVISIBLE);
        if (ready) scheduleRoute(); else main.post(this::startSecurityCheck);
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(launch);
        hero.animate().cancel(); hero.setAlpha(1f); hero.setTranslationY(0f);
        loading.setVisibility(View.INVISIBLE);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        hero.animate().cancel();
        super.onDestroy();
    }

    private void routeNext() {
        if (routed || !ready || !resumed || !usable()) return;
        routed = true;
        SessionManager session = new SessionManager(this);
        String role = session.getRole() == null ? "" : session.getRole().trim().toLowerCase(java.util.Locale.ROOT);
        Intent intent;
        if (!session.isLoggedIn() || !"merchant".equals(role)) {
            if (session.isLoggedIn()) session.forceLogout("merchant_app_role_mismatch");
            intent = new Intent(this, LoginActivity.class);
        } else {
            intent = new Intent(this, PinActivity.class);
            intent.putExtra("native_role", "merchant");
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
