package com.transiva.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Shows the badge selection and a draft preview. The server renders the published photo. */
final class MerchantPhotoBadge {
    private static final String[] KEYS = {"", "hot", "cool", "new", "best_seller", "discount", "favorite"};
    private static final String[] LABELS = {"Tanpa lencana", "Hot", "Cool", "New", "Best Seller", "Diskon", "Favorit"};
    private static final int[] COLORS = {0, 0xFFB91C38, 0xFF0B568A, 0xFF5E35B1, 0xFF8F4706, 0xFF116449, 0xFFA21B74};
    private final MerchantBaseActivity activity;
    private final TextView sticker;
    private final Button button;
    private String saved = "", selected = "";

    MerchantPhotoBadge(MerchantBaseActivity activity, FrameLayout preview, Button button, Runnable changed) {
        this.activity = activity;
        this.button = button;
        sticker = activity.tv("", 9, Color.WHITE, true);
        sticker.setSingleLine();
        sticker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sticker.setPadding(activity.dp(4), activity.dp(2), activity.dp(4), activity.dp(2));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        position.topMargin = position.rightMargin = activity.dp(3);
        preview.addView(sticker, position);
        button.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Lencana foto")
                .setSingleChoiceItems(LABELS, index(selected), (dialog, item) -> {
                    selected = KEYS[item];
                    updateLabel();
                    dialog.dismiss();
                    changed.run();
                })
                .setNegativeButton("Batal", null)
                .show());
        updateLabel();
        showDraft(false);
    }

    void load(String stored, String draft) {
        saved = KEYS[index(stored)];
        selected = draft == null ? saved : KEYS[index(draft)];
        updateLabel();
    }

    String value() { return selected; }
    boolean changed() { return !saved.equals(selected); }
    void setEnabled(boolean enabled) { button.setEnabled(enabled); }

    void showDraft(boolean useOriginal) {
        int i = index(selected);
        sticker.setVisibility(useOriginal && i != 0 ? View.VISIBLE : View.GONE);
        if (i == 0) return;
        sticker.setText(LABELS[i].toUpperCase(java.util.Locale.ROOT));
        sticker.setBackground(activity.round(COLORS[i], activity.dp(4)));
    }

    private void updateLabel() {
        button.setText("Lencana foto · " + LABELS[index(selected)]);
    }

    private static int index(String key) {
        for (int i = 1; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }
}
