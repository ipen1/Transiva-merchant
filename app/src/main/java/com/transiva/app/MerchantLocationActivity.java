package com.transiva.app;

import android.os.Bundle;
import android.widget.LinearLayout;

/** Legacy destination retained for compatibility; existing pickup coordinates are immutable here. */
public class MerchantLocationActivity extends MerchantBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);setContentView(page(root));
        root.addView(title("Lokasi toko"));
        root.addView(card("Lokasi toko menggunakan titik yang sudah tersimpan. Pengambilan dan perubahan lokasi melalui pengaturan telah dinonaktifkan."));
        root.addView(sub("Hubungi admin Transiva jika lokasi toko perlu dikoreksi."));
    }
}
