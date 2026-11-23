package com.transiva.app;

import android.app.Activity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.CaptureActivity;

public final class MerchantPosScanActivity extends CaptureActivity {
    static void launch(Activity host) {
        new IntentIntegrator(host).setCaptureActivity(MerchantPosScanActivity.class)
                .setOrientationLocked(false)
                .setPrompt("Arahkan kamera ke barcode produk").setBeepEnabled(true).initiateScan();
    }
}
