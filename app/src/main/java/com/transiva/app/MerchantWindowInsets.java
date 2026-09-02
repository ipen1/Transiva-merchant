package com.transiva.app;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Android 16 edge-to-edge compatibility for programmatic Views on all screen sizes. */
final class MerchantWindowInsets {
    private MerchantWindowInsets() {}

    static void apply(Activity a, View root) { apply(a, root, false); }
    static void applyIme(Activity a, View root) { apply(a, root, true); }

    private static void apply(Activity a, View root, boolean includeIme) {
        if (root == null) return;
        WindowCompat.setDecorFitsSystemWindows(a.getWindow(), false);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, in) -> {
            Insets bars = in.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int bottom = bars.bottom;
            if (includeIme) {
                Insets ime = in.getInsets(WindowInsetsCompat.Type.ime());
                bottom = Math.max(bottom, ime.bottom);
            }
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bottom);
            return in;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
