package com.transiva.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public final class MerchantOutletOrdersActivity extends MerchantBaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<OrderRow> rows = new ArrayList<>();
    private LinearLayout list;
    private TextView info;
    private boolean visible, loading;
    private long generation, clockOffset;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { load(); handler.postDelayed(this, 12_000); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateTimers(); handler.postDelayed(this, 1000); }
    };
    private final BroadcastReceiver push = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { load(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Pesanan Semua Cabang"));
        root.addView(sub("Pesanan baru dari toko utama dan semua cabang, diurutkan dari yang paling mendesak."));
        info = sub("Menyiapkan daftar…");
        root.addView(info);
        Button reload = outlineBtn("Perbarui");
        reload.setOnClickListener(v -> load());
        root.addView(reload);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        MerchantRealtime.register(this, push);
        handler.post(refresh);
        handler.post(tick);
    }

    @Override protected void onPause() {
        visible = false;
        generation++;
        loading = false;
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(push);
        super.onPause();
    }

    private void load() {
        if (!visible || loading) return;
        loading = true;
        long request = ++generation;
        if (MerchantNetworkExecutor.executeRead(this, "outlet-orders", () -> {
            try {
                JSONObject result = new JSONObject(get(BASE+"merchant_outlet_orders.php"));
                if (!result.optBoolean("success")) throw new IllegalStateException();
                long offset = result.getLong("server_time_epoch_ms") - SystemClock.elapsedRealtime();
                JSONArray orders = result.getJSONArray("orders");
                runOnUiThread(() -> {
                    if (!visible || request != generation) return;
                    loading = false;
                    clockOffset = offset;
                    render(orders);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!visible || request != generation) return;
                    loading = false;
                    info.setText("Sinkronisasi tertunda. Daftar akan diperbarui lagi.");
                });
            }
        }) == null) loading = false;
    }

    private void render(JSONArray data) {
        rows.clear();
        list.removeAllViews();
        for (int i = 0; i < data.length(); i++) {
            JSONObject order = data.optJSONObject(i);
            if (order == null) continue;
            String outlet = order.optString("restaurant_id");
            String id = order.optString("order_id");
            String label = order.optString("restaurant_name") + " · " + rupiah(order.optLong("price")) + "\n" + id;
            Button button = outlineBtn(label);
            button.setOnClickListener(v -> startActivity(new Intent(this, MerchantOrdersActivity.class)
                    .putExtra(EXTRA_BRANCH, outlet).putExtra("order_id", id)));
            list.addView(button);
            rows.add(new OrderRow(button, label, order.optLong("expires_at_ms")));
        }
        updateTimers();
        info.setText(rows.isEmpty() ? "Belum ada pesanan baru." : rows.size()+" pesanan menunggu respons.");
    }

    private void updateTimers() {
        long now = SystemClock.elapsedRealtime()+clockOffset;
        Iterator<OrderRow> it = rows.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            OrderRow row = it.next();
            long seconds = (row.expires - now + 999) / 1000;
            if (seconds <= 0) {
                list.removeView(row.button);
                it.remove();
                removed = true;
            } else {
                row.button.setText(row.label + String.format(Locale.US, "\nRespons dalam %d:%02d", seconds/60, seconds%60));
            }
        }
        if (removed) info.setText(rows.isEmpty()?"Belum ada pesanan baru.":rows.size()+" pesanan menunggu respons.");
    }

    private static final class OrderRow {
        final Button button;
        final String label;
        final long expires;
        OrderRow(Button button, String label, long expires) {
            this.button=button; this.label=label; this.expires=expires;
        }
    }
}
