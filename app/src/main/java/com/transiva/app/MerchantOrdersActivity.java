package com.transiva.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class MerchantOrdersActivity extends MerchantBaseActivity {
    private static final long FALLBACK_POLL_MS = 15000L;

    private LinearLayout root;
    private LinearLayout list;
    private TextView realtimeStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable task;
    private boolean firstLoad = true;
    private boolean updating = false;
    private boolean loading = false;
    private boolean historyMode = false;
    private boolean realtimeRegistered = false;
    private String focusOrderId = "";
    private JSONArray cached = new JSONArray();
    private Spinner periodSpinner;

    private final BroadcastReceiver realtimeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String orderId = intent == null ? "" : intent.getStringExtra(MerchantRealtime.EXTRA_ORDER_ID);
            if (orderId != null && !orderId.trim().isEmpty()) focusOrderId = orderId.trim();
            load(false);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        focusOrderId = getIntent().getStringExtra("order_id");
        if (focusOrderId == null) focusOrderId = "";
        build();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String id = intent == null ? "" : intent.getStringExtra("order_id");
        if (id != null && !id.trim().isEmpty()) focusOrderId = id.trim();
        if (list != null) load(false);
    }



    @Override protected void onResume() {
        super.onResume();
        if (!realtimeRegistered) {
            MerchantRealtime.register(this, realtimeReceiver);
            realtimeRegistered = true;
        }
        start();
    }

    @Override protected void onPause() {
        super.onPause();
        stop();
        if (realtimeRegistered) {
            try { unregisterReceiver(realtimeReceiver); } catch (Exception ignored) {}
            realtimeRegistered = false;
        }
    }

    private void build() {
        root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Pesanan Merchant"));
        root.addView(sub("FCM real-time aktif • polling hanya sebagai cadangan"));

        realtimeStatus = tv("● Menghubungkan ke server...", 12, Color.parseColor("#B54708"), true);
        realtimeStatus.setPadding(dp(12), dp(9), dp(12), dp(9));
        realtimeStatus.setBackground(round(Color.parseColor("#FFF7E6"), dp(14)));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(12));
        root.addView(realtimeStatus, statusLp);

        LinearLayout tabs = row();
        Button active = btn("Aktif");
        Button hist = outlineBtn("Riwayat");
        tabs.addView(active, new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(hist, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(tabs);
        active.setOnClickListener(v -> {
            if (historyMode) {
                historyMode = false;
                periodSpinner.setVisibility(Spinner.GONE);
                load(true);
            }
        });
        hist.setOnClickListener(v -> {
            if (!historyMode) {
                historyMode = true;
                periodSpinner.setVisibility(Spinner.VISIBLE);
                load(true);
            }
        });

        periodSpinner = new Spinner(this);
        periodSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Hari ini", "7 hari", "30 hari", "Semua"}));
        periodSpinner.setVisibility(Spinner.GONE);
        periodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                if (historyMode) render();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(periodSpinner);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button refresh = outlineBtn("↻ Refresh Pesanan");
        refresh.setOnClickListener(v -> load(false));
        root.addView(refresh);

        Button back = outlineBtn("← Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);
    }

    private void start() {
        stop();
        firstLoad = true;
        load(true);
        task = () -> {
            if (!updating && !loading) load(false);
            handler.postDelayed(task, WaveLoadGuard.jitter(FALLBACK_POLL_MS));
        };
        handler.postDelayed(task, WaveLoadGuard.jitter(FALLBACK_POLL_MS));
    }

    private void stop() {
        if (task != null) handler.removeCallbacks(task);
    }

    private void setConnectionState(boolean connected, String message) {
        if (realtimeStatus == null) return;
        realtimeStatus.setText(message);
        realtimeStatus.setTextColor(Color.parseColor(connected ? "#16803A" : "#D92D20"));
        realtimeStatus.setBackground(round(Color.parseColor(connected ? "#ECFDF3" : "#FEF3F2"), dp(14)));
    }

    private void load(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading && firstLoad) {
            list.removeAllViews();
            list.addView(card("Memuat pesanan..."));
        }
        if (!MerchantConnectivity.isOnline(this)) {
            setConnectionState(false, "● Offline • menunggu koneksi kembali");
        } else {
            realtimeStatus.setText("● Sinkronisasi...");
            realtimeStatus.setTextColor(Color.parseColor("#B54708"));
        }

        java.util.concurrent.Future<?> readTask = MerchantNetworkExecutor.executeRead(this, historyMode ? "orders-history" : "orders-active", () -> {
            try {
                String scope = historyMode ? "history" : "active";
                JSONObject r = new JSONObject(get(BASE + "getMerchantOrders.php?scope=" + scope + "&v=" + System.currentTimeMillis()));
                JSONArray orders = r.optJSONArray("orders");
                if (orders == null) orders = new JSONArray();
                JSONArray finalOrders = orders;
                runOnUiThread(() -> {
                    loading = false;
                    firstLoad = false;
                    cached = finalOrders;
                    setConnectionState(true, "● Real-time aktif • server terhubung");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    firstLoad = false;
                    setConnectionState(false, "● Menghubungkan kembali... data terakhir tetap ditampilkan");
                    if (cached.length() == 0) {
                        list.removeAllViews();
                        list.addView(card("Koneksi gagal saat memuat pesanan. Aplikasi akan mencoba lagi otomatis."));
                    }
                });
            }
        });
        if (readTask == null) loading = false;
    }

    private void render() {
        list.removeAllViews();
        int shown = 0;

        for (int i = 0; i < cached.length(); i++) {
            JSONObject o = cached.optJSONObject(i);
            if (o == null) continue;
            String st = o.optString("status", "pending").toLowerCase(Locale.US);
            if (historyMode && !inPeriod(o)) continue;
            if (!historyMode && !focusOrderId.isEmpty() && sameOrder(o, focusOrderId)) {
                list.addView(orderView(o, true));
                shown++;
            }
        }

        for (int i = 0; i < cached.length(); i++) {
            JSONObject o = cached.optJSONObject(i);
            if (o == null) continue;
            String st = o.optString("status", "pending").toLowerCase(Locale.US);
            if (historyMode && !inPeriod(o)) continue;
            if (!historyMode && !focusOrderId.isEmpty() && sameOrder(o, focusOrderId)) continue;
            list.addView(orderView(o, false));
            shown++;
        }

        if (shown == 0) {
            list.addView(card(historyMode ? "Belum ada riwayat pada periode ini." : "Belum ada pesanan aktif."));
        }
    }

    private boolean sameOrder(JSONObject o, String id) {
        return id.equalsIgnoreCase(s(o, "order_id", "id", "order_numeric_id", "order_db_id"));
    }

    private boolean isFinal(String st) {
        return st.equals("finished") || st.equals("completed") || st.equals("cancelled")
                || st.equals("canceled") || st.equals("merchant_rejected");
    }

    private boolean inPeriod(JSONObject o) {
        int p = periodSpinner.getSelectedItemPosition();
        if (p == 3) return true;
        String raw = s(o, "created_at", "order_date", "created");
        if (raw.isEmpty()) return true;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            long age = System.currentTimeMillis() - f.parse(raw).getTime();
            long days = p == 0 ? 1 : (p == 1 ? 7 : 30);
            return age <= days * 86400000L;
        } catch (Exception e) {
            return true;
        }
    }

    private LinearLayout orderView(JSONObject o, boolean focused) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));

        String displayId = s(o, "order_id", "id");
        String actionId = s(o, "id", "order_numeric_id", "order_db_id", "order_id");
        String status = effectiveStatus(o);
        boolean pending = "pending".equalsIgnoreCase(status);

        int borderColor = pending ? Color.parseColor("#EF4444") : BLUE;
        int bgColor = pending ? Color.parseColor("#FFF5F5") : Color.parseColor("#F0F7FF");
        box.setBackground((focused || pending) ? stroke(bgColor, borderColor, dp(18)) : round(Color.WHITE, dp(18)));
        box.setElevation(pending ? dp(7) : dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);

        if (pending) {
            TextView urgent = tv("⚡ ORDER BARU • SEGERA KONFIRMASI", 12, Color.parseColor("#D92D20"), true);
            urgent.setPadding(dp(10), dp(7), dp(10), dp(7));
            urgent.setGravity(Gravity.CENTER);
            urgent.setBackground(round(Color.parseColor("#FEE4E2"), dp(12)));
            box.addView(urgent);
            String age = orderAge(o);
            if (!age.isEmpty()) {
                TextView ageText = tv(age, 11, Color.parseColor("#B42318"), true);
                ageText.setGravity(Gravity.CENTER);
                ageText.setPadding(0, dp(5), 0, dp(8));
                box.addView(ageText);
            }
        }

        String customer = s(o, "customer_name", "customer", "name");
        String phone = s(o, "customer_phone", "phone");
        String address = s(o, "delivery_address", "destination_address", "address");
        String driver = s(o, "driver", "driver_name");
        String note = s(o, "customer_note", "note", "notes", "catatan");

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ot = tv("Order #" + (displayId.isEmpty() ? "-" : displayId), 17, NAVY, true);
        head.addView(ot, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = tv(statusLabel(status), 12, pending ? Color.parseColor("#D92D20") : BLUE, true);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(round(pending ? Color.parseColor("#FEE4E2") : Color.parseColor("#EAF3FF"), dp(20)));
        head.addView(badge);
        box.addView(head);

        String cook = s(o, "cook_minutes", "estimated_cook_minutes");
        if (!cook.isEmpty()) box.addView(tv("⏱ Estimasi masak: " + cook + " menit", 12, Color.parseColor("#7A4D00"), true));
        if (!customer.isEmpty()) box.addView(tv("Customer: " + customer + (phone.isEmpty() ? "" : " • " + phone), 13, TEXT, true));
        if (!address.isEmpty()) box.addView(tv("Tujuan: " + address, 13, MUTED, false));
        box.addView(tv("Driver: " + (driver.isEmpty() ? "Belum ada driver" : driver), 13, MUTED, false));
        addItems(box, o);

        if (!note.isEmpty()) {
            TextView nv = tv("Catatan customer: “" + note + "”", 13, Color.parseColor("#7A4D00"), true);
            nv.setPadding(dp(12), dp(10), dp(12), dp(10));
            nv.setBackground(round(Color.parseColor("#FFF7E6"), dp(12)));
            box.addView(nv);
        }

        MerchantOrderFinanceView.attach(this, box, o);
        MerchantOrderProgressView.attach(this, box, o);
        if (!historyMode) {
            addActions(box, o, actionId, displayId, status);
            if (!driver.isEmpty()) addDriverChat(box, o, actionId, displayId, driver);
        }
        return box;
    }


    private void addDriverChat(LinearLayout box, JSONObject order, String actionId, String displayId, String driverName) {
        MerchantDriverCommunication.attach(this, box, order, actionId, displayId, driverName);
    }

    private String orderAge(JSONObject o) {
        String raw = s(o, "created_at", "order_date", "created");
        if (raw.isEmpty()) return "";
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            long ageMs = Math.max(0, System.currentTimeMillis() - f.parse(raw).getTime());
            long minutes = ageMs / 60000L;
            if (minutes <= 0) return "Masuk kurang dari 1 menit lalu";
            if (minutes < 60) return "Masuk " + minutes + " menit lalu";
            return "Masuk lebih dari 1 jam lalu";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void addItems(LinearLayout box, JSONObject order) {
        JSONArray items = order.optJSONArray("items");
        box.addView(tv("Detail Pesanan", 14, NAVY, true));
        if (items == null || items.length() == 0) {
            box.addView(tv("Detail item belum dikirim API.", 13, Color.parseColor("#B54708"), false));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            int qty = Math.max(1, it.optInt("qty", it.optInt("quantity", 1)));
            String name = s(it, "name", "menu_name", "food_name", "item_name");
            long unit = it.optLong("price", it.optLong("unit_price", 0));
            long subtotal = it.optLong("subtotal", unit * qty);
            long merchantBase = it.optLong("merchant_price", it.optLong("original_price", 0));
            long originalBase = it.optLong("original_price", merchantBase);
            long optionTotal = Math.max(0, it.optLong("option_total", 0));
            long grossupUnit = Math.max(0, it.optLong("grossup_fee", Math.max(0, unit - optionTotal - merchantBase)));
            double discountPct = Math.max(0d, it.optDouble("discount_percent", 0d));
            long merchantUnit = Math.max(0, merchantBase + optionTotal);
            String variant = s(it, "variant", "variant_name", "option", "options");
            String note = s(it, "note", "notes", "item_note", "catatan");
            box.addView(tv(qty + "x " + (name.isEmpty() ? "Item" : name) + (subtotal > 0 ? "  •  Customer " + rupiah(subtotal) : ""), 14, TEXT, true));
            if (discountPct > 0 && originalBase > merchantBase) {
                box.addView(tv("   Diskon merchant " + (discountPct == Math.rint(discountPct) ? String.valueOf((int) discountPct) : String.valueOf(discountPct)) + "% • Harga bersih dasar " + rupiah(merchantBase), 12, Color.parseColor("#137333"), true));
            }
            if (merchantUnit > 0) box.addView(tv("   Diterima merchant: " + rupiah(merchantUnit * qty) + (grossupUnit > 0 ? " • Gross-up: " + rupiah(grossupUnit * qty) : ""), 12, MUTED, false));
            if (!variant.isEmpty()) box.addView(tv("   Pilihan: " + variant, 12, MUTED, false));
            if (!note.isEmpty()) box.addView(tv("   Catatan: " + note, 12, Color.parseColor("#7A4D00"), false));
        }
    }

    private void addActions(LinearLayout box, JSONObject order, String actionId, String displayId, String status) {
        String st = status == null ? "" : status.trim().toLowerCase(Locale.US);
        if ("pending".equals(st)) {
            LinearLayout a = row();
            if (compactScreen()) a.setOrientation(LinearLayout.VERTICAL);
            Button accept = btn(updating ? "Memproses..." : "✓ Terima");
            Button reject = outlineBtn("✕ Tolak");
            accept.setEnabled(!updating);
            reject.setEnabled(!updating);
            if (compactScreen()) {
                a.addView(accept, new LinearLayout.LayoutParams(-1, dp(50)));
                a.addView(reject, new LinearLayout.LayoutParams(-1, dp(50)));
            } else {
                a.addView(accept, new LinearLayout.LayoutParams(0, dp(50), 1));
                a.addView(reject, new LinearLayout.LayoutParams(0, dp(50), 1));
            }
            box.addView(a);
            accept.setOnClickListener(v -> { if (!updating) showCookTime(actionId, displayId); });
            reject.setOnClickListener(v -> { if (!updating) showRejectReasons(actionId, displayId); });
        } else if ("merchant_accepted".equals(st)) {
            // Setelah Terima tidak ada lagi tombol Terima/Tolak maupun Mulai Siapkan.
            // Server otomatis memasukkan dapur ke status preparing dan countdown ditampilkan di progress card.
        } else if ("preparing".equals(st) || "merchant_preparing".equals(st)) {
            if (MerchantOrderProgressView.countdownFinished(order)) {
                Button r = btn(updating ? "Memproses..." : "✓ Pesanan Siap Diambil");
                r.setEnabled(!updating);
                r.setOnClickListener(v -> { if (!updating) update(actionId, displayId, "ready", "", 0); });
                box.addView(r);
            }
        }
    }

    private void showCookTime(String id, String displayId) {
        if (updating) return;
        final String[] labels = {"10 menit", "15 menit", "20 menit", "30 menit", "45 menit"};
        final int[] mins = {10, 15, 20, 30, 45};
        new AlertDialog.Builder(this)
                .setTitle("Estimasi waktu memasak")
                .setItems(labels, (d, w) -> update(id, displayId, "merchant_accepted", "", mins[w]))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showRejectReasons(String id, String displayId) {
        if (updating) return;
        final String[] reasons = {"Menu habis", "Restoran terlalu ramai", "Restoran akan segera tutup", "Pesanan tidak dapat dibuat", "Lainnya"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alasan Tolak Pesanan")
                .setSingleChoiceItems(reasons, -1, null)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tolak Pesanan", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (updating) return;
            int pos = dialog.getListView().getCheckedItemPosition();
            if (pos < 0) {
                toast("Pilih alasan penolakan terlebih dahulu.");
                return;
            }
            dialog.dismiss();
            if (pos == reasons.length - 1) showCustomRejectReason(id, displayId);
            else update(id, displayId, "merchant_rejected", reasons[pos], 0);
        }));
        dialog.show();
    }

    private void showCustomRejectReason(String id, String displayId) {
        if (updating) return;
        EditText in = input("Tuliskan alasan penolakan", android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Alasan Lainnya")
                .setView(in)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tolak", (d, w) -> {
                    if (updating) return;
                    String reason = in.getText().toString().trim();
                    update(id, displayId, "merchant_rejected", reason.isEmpty() ? "Alasan merchant" : reason, 0);
                })
                .show();
    }

    private String effectiveStatus(JSONObject o) {
        String[] keys = {"global_status", "driver_status", "order_status", "trip_status", "status"};
        String best = "pending";
        int bestRank = 0;
        for (String key : keys) {
            String v = o.optString(key, "").trim().toLowerCase(Locale.US);
            if (v.isEmpty()) continue;
            int r = statusRank(v);
            if (r > bestRank || (bestRank == 0 && !"pending".equals(v))) { best = v; bestRank = r; }
        }
        return best;
    }

    private int statusRank(String s) {
        switch (s == null ? "" : s.trim().toLowerCase(Locale.US)) {
            case "merchant_accepted": case "preparing": case "merchant_preparing": return 1;
            case "ready": case "merchant_ready": case "driver_accepted": case "taken": return 2;
            case "arrived_pickup": return 3;
            case "on_delivery": return 4;
            case "arrived_delivery": return 5;
            case "finished": case "completed": return 6;
            default: return 0;
        }
    }

    private String statusLabel(String s) {
        if (s == null) return "-";
        switch (s.trim().toLowerCase(Locale.US)) {
            case "pending": return "Menunggu Merchant";
            case "merchant_accepted": return "Diterima Merchant";
            case "preparing":
            case "merchant_preparing": return "Sedang Disiapkan";
            case "ready":
            case "merchant_ready": return "Siap Diambil";
            case "merchant_rejected": return "Ditolak Merchant";
            case "driver_accepted":
            case "taken": return "Driver Menuju Pickup";
            case "arrived_pickup": return "Driver Tiba di Resto";
            case "on_delivery": return "Menuju Customer";
            case "arrived_delivery": return "Driver Tiba di Customer";
            case "finished":
            case "completed": return "Selesai";
            case "canceled":
            case "cancelled": return "Dibatalkan";
            default: return s;
        }
    }

    private void update(String id, String displayId, String status, String rejectReason, int cookMinutes) {
        if (updating) return;
        if (id == null || id.trim().isEmpty()) {
            alert("Data tidak lengkap", "ID pesanan tidak ditemukan.");
            return;
        }
        updating = true;
        render(); // immediately disables every status button to prevent duplicate taps
        MerchantOrderRepository.updateStatus(this, id, displayId, status, rejectReason, cookMinutes, (success, message, networkError) -> {
            updating = false;
            if (success) {
                toast(message);
                focusOrderId = displayId == null ? "" : displayId;
                load(false);
            } else {
                render();
                alert(networkError ? "Koneksi" : "Gagal", message);
            }
        });
    }
}
