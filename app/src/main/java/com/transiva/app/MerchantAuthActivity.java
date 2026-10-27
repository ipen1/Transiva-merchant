package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared native authentication shell: accessible contrast, IME insets and step transitions. */
public abstract class MerchantAuthActivity extends Activity {
    protected int pageColor, surfaceColor, inkColor, mutedColor, borderColor;
    protected LinearLayout authRoot;
    protected ScrollView authScroll;

    @Override protected void onCreate(Bundle state) {
        boolean dark = MerchantAppSettings.isDarkMode(this);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        pageColor = Color.parseColor(dark ? "#091625" : "#F4F8FE");
        surfaceColor = Color.parseColor(dark ? "#122235" : "#FFFFFF");
        inkColor = Color.parseColor(dark ? "#EEF5FF" : "#102D50");
        mutedColor = Color.parseColor(dark ? "#A9B8CC" : "#667085");
        borderColor = Color.parseColor(dark ? "#2D435C" : "#D9E5F2");
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    protected LinearLayout authShell(String title, String subtitle) {
        authScroll = new ScrollView(this);
        authScroll.setFillViewport(true);
        authScroll.setBackgroundColor(pageColor);
        authRoot = new LinearLayout(this);
        authRoot.setOrientation(LinearLayout.VERTICAL);
        int side = Math.max(22, (getResources().getConfiguration().screenWidthDp - 520) / 2);
        authRoot.setPadding(authDp(side), authDp(18), authDp(side), authDp(28));
        authScroll.addView(authRoot, new ScrollView.LayoutParams(-1, -2));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = authText("TRANSIVA / MERCHANT", 12, true);
        brand.setLetterSpacing(.09f);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView theme = authText("◐  Tema", 13, true);
        theme.setGravity(Gravity.CENTER);
        theme.setMinHeight(authDp(48));
        theme.setMinWidth(authDp(72));
        theme.setContentDescription("Pilih tema aplikasi: " + MerchantAppSettings.themeLabel(this));
        theme.setOnClickListener(v -> {
            if (canChangeTheme()) MerchantAppSettings.showThemePicker(this);
            else Toast.makeText(this, "Tunggu sampai proses selesai", Toast.LENGTH_SHORT).show();
        });
        toolbar.addView(theme);
        authRoot.addView(toolbar);
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(authDp(22), authDp(24), authDp(22), authDp(24));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(7, 36, 76), Color.rgb(11, 120, 255)});
        bg.setCornerRadius(authDp(26)); hero.setBackground(bg);
        TextView badge = authText("BISNIS LOKAL, PELUANG BESAR", 10, true);
        badge.setTextColor(Color.rgb(192, 227, 255)); hero.addView(badge);
        TextView heading = authText(title, 27, true); heading.setTextColor(Color.WHITE);
        heading.setPadding(0, authDp(10), 0, authDp(8)); hero.addView(heading);
        TextView sub = authText(subtitle, 14, false); sub.setTextColor(Color.rgb(225, 240, 255));
        sub.setLineSpacing(authDp(3), 1); hero.addView(sub);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, authDp(10), 0, authDp(18)); authRoot.addView(hero, heroLp);
        return authRoot;
    }
    protected LinearLayout authCard() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(authDp(20), authDp(22), authDp(20), authDp(22));
        card.setBackground(authShape(surfaceColor, 24));
        card.setElevation(authDp(2)); authRoot.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }
    protected boolean canChangeTheme() { return true; }
    protected void mountAuth() {
        setContentView(authScroll); MerchantWindowInsets.applyIme(this, authScroll);
        MerchantAppSettings.apply(this);
    }
    protected TextView authText(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(inkColor);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t;
    }
    protected GradientDrawable authShape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color);
        d.setCornerRadius(authDp(radius)); d.setStroke(authDp(1), borderColor); return d;
    }
    protected void animateStep(View content) {
        content.animate().cancel(); content.setAlpha(0f); content.setTranslationY(authDp(10));
        content.animate().alpha(1f).translationY(0).setDuration(220).start();
        authScroll.post(() -> authScroll.smoothScrollTo(0, 0));
    }
    protected int authDp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
