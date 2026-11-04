package com.transiva.app;

import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Android 16 edge-to-edge compatibility for programmatic Views on all screen sizes. */
final class MerchantWindowInsets {
    private MerchantWindowInsets() {}

    static void enable(Window window, boolean lightBars) {
        // Keep insets dispatch for older Android versions and full-size dialogs.
        // enableEdgeToEdge also bundles legacy system-bar color calls.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        setBarAppearance(window, lightBars);
    }

    static void setBarAppearance(Window window, boolean lightBars) {
        // Older versions retain the icon appearance of their window theme.
        if (Build.VERSION.SDK_INT < 35) return;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    static void requestWhenAttached(View root) {
        if (ViewCompat.isAttachedToWindow(root)) {
            ViewCompat.requestApplyInsets(root);
            return;
        }
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                ViewCompat.requestApplyInsets(view);
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
    }

    static void apply(View root) { apply(root, false); }
    static void applyIme(View root) { apply(root, true); }

    private static void apply(View root, boolean includeIme) {
        if (root == null) return;
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
        requestWhenAttached(root);
    }
}
