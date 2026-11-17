package com.transiva.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

final class MerchantPremiumUi {
    static final int BLUE = 0xFF075BDD;
    private final MerchantBaseActivity host;

    MerchantPremiumUi(MerchantBaseActivity host) {
        this.host = host;
    }

    boolean dark() {
        return MerchantAppSettings.isDarkMode(host);
    }

    int accent() {
        return dark() ? 0xFF85BFFF : BLUE;
    }

    int divider() {
        return dark() ? MerchantAppSettings.DARK_BORDER : 0xFFDDE5EF;
    }

    GradientDrawable surface(int radius) {
        return host.stroke(Color.WHITE, divider(), host.dp(radius));
    }

    Drawable touch(Drawable background) {
        return new RippleDrawable(ColorStateList.valueOf(dark() ? 0x30FFFFFF : 0x22075BDD),
                background, background == null ? host.round(Color.WHITE, host.dp(10)) : null);
    }

    Drawable iconDrawable(int drawable, int color, int size) {
        Drawable icon = host.getDrawable(drawable).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, host.dp(size), host.dp(size));
        return icon;
    }

    ImageView icon(int drawable, int color) {
        ImageView icon = new ImageView(host);
        icon.setImageResource(drawable);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    ImageButton iconButton(int drawable, int color, String label) {
        ImageButton button = new ImageButton(host);
        button.setImageResource(drawable);
        button.setImageTintList(ColorStateList.valueOf(color));
        button.setBackground(touch(null));
        button.setPadding(host.dp(12), host.dp(12), host.dp(12), host.dp(12));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= 26) button.setTooltipText(label);
        return button;
    }
}
