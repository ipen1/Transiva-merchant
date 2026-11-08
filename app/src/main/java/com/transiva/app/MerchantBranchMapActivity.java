package com.transiva.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

public final class MerchantBranchMapActivity extends MerchantBaseActivity {
    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private MapView mapView;
    private MapLibreMap map;
    private boolean styleReady;
    private static final double MIN_PICK_ZOOM = 14;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Titik Pickup Cabang"));
        TextView instructions = sub("Geser peta hingga pin berada di tempat driver mengambil pesanan.");
        root.addView(instructions);
        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, dp(390)));
        MapLibre.getInstance(getApplicationContext());
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
            if (!styleReady || map == null || map.getCameraPosition().zoom < MIN_PICK_ZOOM) return;
            LatLng chosen = map.getCameraPosition().target;
            if (chosen == null || chosen.getLatitude() == 0 || chosen.getLongitude() == 0) return;
            Intent result = new Intent();
            result.putExtra("lat", chosen.getLatitude());
            result.putExtra("lng", chosen.getLongitude());
            setResult(RESULT_OK, result);
            finish();
        });
        root.addView(confirm);
        Button back = outlineBtn("Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        mapView.onCreate(state);
        mapView.getMapAsync(ready -> {
            map = ready;
            if (state == null) {
                double lat = getIntent().getDoubleExtra("lat", Double.NaN);
                double lng = getIntent().getDoubleExtra("lng", Double.NaN);
                boolean local = Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 85
                        && Math.abs(lng) <= 180 && (lat != 0 || lng != 0);
                LatLng start = local ? new LatLng(lat, lng) : new LatLng(-2.5, 118.0);
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(start, local ? 16 : 4));
            }
            Runnable updatePoint = () -> {
                boolean detailed = ready.getCameraPosition().zoom >= MIN_PICK_ZOOM;
                confirm.setEnabled(styleReady && detailed);
                instructions.setText(!styleReady ? "Memuat peta cabang…"
                        : detailed ? "Geser peta hingga pin berada di titik pickup." : "Perbesar peta untuk memilih titik pickup yang akurat.");
            };
            ready.addOnCameraIdleListener(updatePoint::run);
            updatePoint.run();
            ready.setStyle(new Style.Builder().fromUri(STYLE_URL), style -> {
                styleReady = true;
                updatePoint.run();
            });
        });
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }
}
