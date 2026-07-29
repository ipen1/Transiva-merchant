package com.transiva.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantOrdersActivity extends MerchantBaseActivity {
    private LinearLayout root, list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable task;
    private boolean firstLoad = true;
    private boolean updating = false;
    private String focusOrderId = "";

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        focusOrderId = getIntent().getStringExtra("order_id");
        if(focusOrderId == null) focusOrderId = "";
        build();
    }
    @Override protected void onResume(){ super.onResume(); start(); }
    @Override protected void onPause(){ super.onPause(); stop(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Pesanan Merchant"));
        root.addView(sub(focusOrderId.isEmpty() ? "Detail item dan proses pesanan aktif" : "Membuka order #" + focusOrderId));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        Button back = outlineBtn("← Kembali"); back.setOnClickListener(v -> finish()); root.addView(back);
    }

    private void start(){
        stop(); firstLoad = true; load();
        task = () -> { if(!updating) load(); handler.postDelayed(task, 15000); };
        handler.postDelayed(task, 15000);
    }
    private void stop(){ if(task != null) handler.removeCallbacks(task); }

    private void load(){
        final String u = username();
        if(u.isEmpty()){
            list.removeAllViews(); list.addView(card("Sesi merchant tidak ditemukan. Silakan login ulang.")); return;
        }
        if(firstLoad){ list.removeAllViews(); list.addView(card("Memuat pesanan...")); }
        new Thread(() -> {
            try {
                String res = get(BASE + "getMerchantOrders.php?v=" + System.currentTimeMillis());
                runOnUiThread(() -> { firstLoad = false; show(res); });
            } catch(Exception e){
                runOnUiThread(() -> { firstLoad = false; list.removeAllViews(); list.addView(card("Koneksi gagal saat memuat pesanan.")); });
            }
        }).start();
    }

    private void show(String json){
        list.removeAllViews();
        try {
            JSONObject obj = new JSONObject(json);
            if(!obj.optBoolean("success", true)){ list.addView(card(obj.optString("message","Gagal memuat pesanan"))); return; }
            JSONArray arr = obj.optJSONArray("orders"); if(arr == null) arr = obj.optJSONArray("data");
            if(arr == null || arr.length() == 0){ list.addView(card("Belum ada pesanan aktif.")); return; }

            int shown = 0;
            for(int i=0;i<arr.length();i++){
                JSONObject o = arr.optJSONObject(i); if(o == null) continue;
                String st = o.optString("status","pending").trim().toLowerCase();
                if(isFinal(st)) continue;
                if(!focusOrderId.isEmpty() && sameOrder(o, focusOrderId)) list.addView(orderView(o, true));
            }
            for(int i=0;i<arr.length();i++){
                JSONObject o = arr.optJSONObject(i); if(o == null) continue;
                String st = o.optString("status","pending").trim().toLowerCase();
                if(isFinal(st)) continue;
                if(!focusOrderId.isEmpty() && sameOrder(o, focusOrderId)) { shown++; continue; }
                list.addView(orderView(o, false)); shown++;
            }
            if(shown == 0 && focusOrderId.isEmpty()) list.addView(card("Belum ada pesanan aktif."));
        } catch(Exception e){ list.addView(card("Response pesanan tidak valid.")); }
    }

    private boolean sameOrder(JSONObject o, String id){
        return id.equalsIgnoreCase(s(o,"order_id","id","order_numeric_id","order_db_id"));
    }

    private boolean isFinal(String st){
        return st.equals("finished") || st.equals("completed") || st.equals("cancelled") || st.equals("canceled") || st.equals("merchant_rejected");
    }

    private LinearLayout orderView(JSONObject o, boolean focused){
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(focused ? stroke(Color.parseColor("#F0F7FF"), BLUE, dp(18)) : round(Color.WHITE, dp(18)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(12)); box.setLayoutParams(lp);

        String displayId = s(o,"order_id","id");
        String actionId = s(o,"id","order_numeric_id","order_db_id","order_id");
        String status = o.optString("status","pending");
        long price = o.optLong("price", o.optLong("total", o.optLong("grand_total", 0)));
        String customer = s(o,"customer_name","customer","name");
        String phone = s(o,"customer_phone","phone");
        String address = s(o,"delivery_address","destination_address","address");
        String driver = s(o,"driver","driver_name");
        String note = s(o,"note","notes","customer_note","catatan");

        LinearLayout head = new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView orderTitle = tv("Order #" + (displayId.isEmpty()?"-":displayId), 17, NAVY, true);
        head.addView(orderTitle, new LinearLayout.LayoutParams(0,-2,1f));
        TextView badge = tv(statusLabel(status), 12, BLUE, true); badge.setPadding(dp(10),dp(5),dp(10),dp(5)); badge.setBackground(round(Color.parseColor("#EAF3FF"), dp(20))); head.addView(badge);
        box.addView(head);

        if(!customer.isEmpty()) box.addView(tv("Customer: " + customer + (phone.isEmpty()?"":" • " + phone), 13, TEXT, true));
        if(!address.isEmpty()) box.addView(tv("Tujuan: " + address, 13, MUTED, false));
        box.addView(tv("Driver: " + (driver.isEmpty() ? "Belum ada driver" : driver), 13, MUTED, false));

        addItems(box, o);
        if(!note.isEmpty()){
            TextView noteView = tv("Catatan customer: “" + note + "”", 13, Color.parseColor("#7A4D00"), true);
            noteView.setPadding(dp(12),dp(10),dp(12),dp(10)); noteView.setBackground(round(Color.parseColor("#FFF7E6"), dp(12)));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1,-2); nlp.setMargins(0,dp(8),0,dp(8)); noteView.setLayoutParams(nlp); box.addView(noteView);
        }
        box.addView(tv("Total: " + rupiah(price), 16, NAVY, true));

        addActions(box, actionId, displayId, status);
        return box;
    }

    private void addItems(LinearLayout box, JSONObject order){
        JSONArray items = order.optJSONArray("items");
        if(items == null) items = order.optJSONArray("order_items");
        if(items == null) items = order.optJSONArray("details");
        if(items == null){
            String raw = s(order,"items_json","order_items_json","detail_items");
            try { if(raw.startsWith("[")) items = new JSONArray(raw); } catch(Exception ignored){}
        }

        TextView h = tv("Detail Pesanan", 14, NAVY, true); h.setPadding(0,dp(10),0,dp(4)); box.addView(h);
        if(items == null || items.length() == 0){
            String fallback = s(order,"item_summary","items_text","order_detail","menu_name","food_name");
            box.addView(tv(fallback.isEmpty() ? "Detail item belum dikirim oleh API." : fallback, 13, fallback.isEmpty()?Color.parseColor("#B54708"):TEXT, false));
            return;
        }
        for(int i=0;i<items.length();i++){
            JSONObject it = items.optJSONObject(i);
            if(it == null) continue;
            int qty = Math.max(1, it.optInt("qty", it.optInt("quantity", 1)));
            String name = s(it,"name","menu_name","food_name","item_name");
            long unit = it.optLong("price", it.optLong("unit_price", 0));
            long subtotal = it.optLong("subtotal", unit * qty);
            String variant = s(it,"variant","variant_name","option","options");
            String note = s(it,"note","notes","item_note","catatan");
            box.addView(tv(qty + "x " + (name.isEmpty()?"Item":name) + (subtotal>0?"  •  " + rupiah(subtotal):""), 14, TEXT, true));
            if(!variant.isEmpty()) box.addView(tv("   " + variant, 12, MUTED, false));
            if(!note.isEmpty()) box.addView(tv("   Catatan: " + note, 12, Color.parseColor("#7A4D00"), false));
        }
    }

    private void addActions(LinearLayout box, String actionId, String displayId, String status){
        String st = status == null ? "" : status.trim().toLowerCase();
        if("pending".equals(st)){
            LinearLayout actions = row();
            Button accept = btn("Terima"); Button reject = outlineBtn("Tolak");
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, dp(48), 1f); aLp.setMargins(0, dp(8), dp(4), 0);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, dp(48), 1f); rLp.setMargins(dp(4), dp(8), 0, 0);
            actions.addView(accept, aLp); actions.addView(reject, rLp); box.addView(actions);
            accept.setOnClickListener(v -> update(actionId, displayId, "merchant_accepted", ""));
            reject.setOnClickListener(v -> showRejectReasons(actionId, displayId));
        } else if("merchant_accepted".equals(st)){
            Button preparing = btn("Mulai Siapkan Pesanan"); preparing.setOnClickListener(v -> update(actionId, displayId, "preparing", "")); box.addView(preparing);
        } else if("preparing".equals(st) || "merchant_preparing".equals(st)){
            Button ready = btn("✓ Pesanan Siap Diambil"); ready.setOnClickListener(v -> update(actionId, displayId, "ready", "")); box.addView(ready);
        }
    }

    private void showRejectReasons(String id, String displayId){
        final String[] reasons = {"Menu habis", "Restoran terlalu ramai", "Restoran akan segera tutup", "Pesanan tidak dapat dibuat", "Lainnya"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Alasan Tolak Pesanan")
                .setSingleChoiceItems(reasons, -1, null)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tolak Pesanan", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int pos = dialog.getListView().getCheckedItemPosition();
            if(pos < 0){ toast("Pilih alasan penolakan terlebih dahulu."); return; }
            dialog.dismiss();
            if(pos == reasons.length - 1) showCustomRejectReason(id, displayId); else update(id, displayId, "merchant_rejected", reasons[pos]);
        }));
        dialog.show();
    }

    private void showCustomRejectReason(String id, String displayId){
        EditText input = input("Tuliskan alasan penolakan", android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Alasan Lainnya").setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tolak", (d,w) -> {
                    String reason = input.getText().toString().trim();
                    if(reason.isEmpty()) reason = "Alasan merchant";
                    update(id, displayId, "merchant_rejected", reason);
                }).show();
    }

    private String statusLabel(String s){
        if(s == null) return "-";
        switch(s.trim().toLowerCase()){
            case "pending": return "Menunggu Merchant";
            case "merchant_accepted": return "Diterima Merchant";
            case "preparing": case "merchant_preparing": return "Sedang Disiapkan";
            case "ready": case "merchant_ready": return "Siap Diambil";
            case "merchant_rejected": return "Ditolak Merchant";
            case "taken": return "Driver Menuju Pickup";
            case "arrived_pickup": return "Driver Tiba di Resto";
            case "on_delivery": return "Menuju Customer";
            case "arrived_delivery": return "Driver Tiba di Customer";
            case "finished": case "completed": return "Selesai";
            case "canceled": case "cancelled": return "Dibatalkan";
        }
        return s;
    }

    private void update(String id, String displayId, String status, String rejectReason){
        if(id == null || id.trim().isEmpty()){
            alert("Data tidak lengkap", "ID pesanan tidak ditemukan. Muat ulang halaman pesanan lalu coba lagi."); return;
        }
        updating = true; toast("Memproses order #" + displayId + "...");
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("id", id); p.put("order_id", displayId); p.put("status", status);
                if(!rejectReason.isEmpty()) { p.put("reject_reason", rejectReason); p.put("merchant_reject_reason", rejectReason); }
                JSONObject r = new JSONObject(postJson(BASE + "updateMerchantOrder.php", p));
                runOnUiThread(() -> {
                    updating = false;
                    if(r.optBoolean("success", false)){
                        toast(r.optString("message", "Status pesanan berhasil diperbarui")); load();
                    } else { alert("Gagal", r.optString("message", "Gagal mengubah status pesanan.")); load(); }
                });
            } catch(Exception e){ runOnUiThread(() -> { updating = false; alert("Error","Gagal mengubah status pesanan."); load(); }); }
        }).start();
    }
}
