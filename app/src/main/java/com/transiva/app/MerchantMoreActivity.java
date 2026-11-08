package com.transiva.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public final class MerchantMoreActivity extends MerchantBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Menu Merchant"));
        link(root, "Profil toko", MerchantRestaurantProfileActivity.class);
        link(root, "Rating & ulasan", MerchantReviewsActivity.class);
        link(root, "Trans Asisten", MerchantTransAssistantActivity.class);
        link(root, "Notifikasi", MerchantNotificationCenterActivity.class);
        link(root, "Diagnostik", MerchantDiagnosticsActivity.class);
        link(root, "Pengaturan", MerchantSettingsActivity.class);
        Button back = outlineBtn("Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
    }

    private void link(LinearLayout parent, String label, Class<?> destination) {
        Button button = outlineBtn(label);
        button.setOnClickListener(v -> open(destination));
        parent.addView(button);
    }
}
