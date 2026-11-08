package com.transiva.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

public final class MerchantBranchMapActivity extends MerchantBaseActivity {
    private static final float MIN_PICK_ZOOM = 15f;
    private MapView mapView;
    private GoogleMap map;
    private boolean mapLoaded;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Titik Pickup Cabang"));
        TextView instructions = sub("Memuat Google Maps…");
        root.addView(instructions);
        if (!hasMapsKey()) {
            instructions.setText("Google Maps Merchant belum dikonfigurasi. Hubungi admin Transiva.");
            addBack(root);
            return;
        }

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, dp(390)));
        mapView = new MapView(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(
                        event.getActionMasked() != MotionEvent.ACTION_UP && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
                return super.dispatchTouchEvent(event);
            }
        };
        frame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        TextView pin = tv("📍", 32, TEXT, true);
        pin.setGravity(Gravity.CENTER);
        pin.setTranslationY(-dp(17));
        frame.addView(pin, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        Button confirm = btn("Gunakan titik ini");
        confirm.setEnabled(false);
        confirm.setOnClickListener(v -> {
            if (!mapLoaded || map == null || map.getCameraPosition().zoom < MIN_PICK_ZOOM) return;
            LatLng chosen = map.getCameraPosition().target;
            if (chosen.latitude == 0 || chosen.longitude == 0) {
                instructions.setText("Geser pin sedikit untuk memilih titik pickup.");
                return;
            }
            setResult(RESULT_OK, new Intent().putExtra("lat", chosen.latitude).putExtra("lng", chosen.longitude));
            finish();
        });
        root.addView(confirm);
        addBack(root);

        mapView.onCreate(state == null ? null : state.getBundle("map_state"));
        mapView.getMapAsync(ready -> {
            if (isDestroyed()) return;
            map = ready;
            ready.getUiSettings().setZoomControlsEnabled(true);
            ready.getUiSettings().setMapToolbarEnabled(false);
            double lat = state == null ? getIntent().getDoubleExtra("lat", Double.NaN) : state.getDouble("lat", Double.NaN);
            double lng = state == null ? getIntent().getDoubleExtra("lng", Double.NaN) : state.getDouble("lng", Double.NaN);
            boolean local = Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 85
                    && Math.abs(lng) <= 180 && lat != 0 && lng != 0;
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(local ? new LatLng(lat, lng) : new LatLng(-2.5, 118), local ? (state == null ? 16 : state.getFloat("zoom", 16)) : 4));
            Runnable update = () -> {
                boolean detailed = ready.getCameraPosition().zoom >= MIN_PICK_ZOOM;
                confirm.setEnabled(mapLoaded && detailed);
                instructions.setText(!mapLoaded ? "Memuat Google Maps…" :
                        detailed ? "Geser peta hingga pin berada di titik pickup." : "Perbesar peta untuk memilih titik pickup.");
            };
            ready.setOnMapLoadedCallback(()->{ mapLoaded=true; update.run(); });
            ready.setOnCameraIdleListener(update::run);
            instructions.postDelayed(()->{
                if (!mapLoaded && !isFinishing() && !isDestroyed()) {
                    instructions.setText("Peta belum tampil. Periksa koneksi atau konfigurasi Google Maps Merchant.");
                }
            },10_000);
            update.run();
        });
    }

    private boolean hasMapsKey() {
        try {
            Bundle meta = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData;
            return meta != null && !meta.getString("com.google.android.geo.API_KEY", "").trim().isEmpty();
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void addBack(LinearLayout root) {
        Button back = outlineBtn("Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
    }

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (mapView != null) {
            Bundle state = new Bundle();
            mapView.onSaveInstanceState(state);
            out.putBundle("map_state", state);
        }
        if (map != null) {
            out.putDouble("lat", map.getCameraPosition().target.latitude);
            out.putDouble("lng", map.getCameraPosition().target.longitude);
            out.putFloat("zoom", map.getCameraPosition().zoom);
        }
    }
}
