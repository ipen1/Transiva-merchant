package com.transiva.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Button;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class MerchantPosCamera {
    private String lastCode="";
    private long lastSeen;

    boolean accepts(String code,long now) {
        if(code==null||code.isEmpty())return false;
        boolean repeated=code.equals(lastCode)&&now-lastSeen<950L;
        lastCode=code;
        lastSeen=now;
        return !repeated;
    }

    void reset() {
        lastCode="";
        lastSeen=0;
    }

    static boolean hasFlash(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    static Button flashButton(Context context,BooleanSupplier enabled,Consumer<Boolean> change) {
        Button flash=new Button(context);
        flash.setAllCaps(false);
        flash.setText(enabled.getAsBoolean()?"Matikan flash":"Nyalakan flash");
        flash.setOnClickListener(view->{
            change.accept(!enabled.getAsBoolean());
            flash.setText(enabled.getAsBoolean()?"Matikan flash":"Nyalakan flash");
        });
        return flash;
    }

    static void setFlash(DecoratedBarcodeView scanner,boolean on) {
        if(on)scanner.setTorchOn();else scanner.setTorchOff();
    }
}
