package com.transiva.app;

import android.app.Activity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.journeyapps.barcodescanner.CaptureActivity;

public final class MerchantPosScanActivity extends CaptureActivity {
    private boolean torchOn;
    @Override protected com.journeyapps.barcodescanner.DecoratedBarcodeView initializeContent() {
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);
        com.journeyapps.barcodescanner.DecoratedBarcodeView scanner=
                new com.journeyapps.barcodescanner.DecoratedBarcodeView(this);
        root.addView(scanner,new android.widget.FrameLayout.LayoutParams(-1,-1));
        if(MerchantPosCamera.hasFlash(this)){
            android.widget.Button flash=MerchantPosCamera.flashButton(this,()->torchOn,on->{
                torchOn=on;
                MerchantPosCamera.setFlash(scanner,on);
            });
            android.widget.FrameLayout.LayoutParams controls=new android.widget.FrameLayout.LayoutParams(-2,-2,
                    android.view.Gravity.TOP|android.view.Gravity.END);
            controls.setMargins(16,48,16,16);
            root.addView(flash,controls);
        }
        setContentView(root);
        return scanner;
    }

    static void launch(Activity host) {
        new IntentIntegrator(host).setCaptureActivity(MerchantPosScanActivity.class)
                .setOrientationLocked(false)
                .setPrompt("Arahkan kamera ke barcode produk").setBeepEnabled(true).initiateScan();
    }
}
