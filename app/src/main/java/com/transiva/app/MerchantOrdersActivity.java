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
import android.view.View;
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
    private long readGeneration;
    private java.util.concurrent.Future<?> activeRead;
    private boolean refreshQueued;
    private boolean historyMode = false;
    private boolean realtimeRegistered = false;
    private String focusOrderId = "";
    private JSONArray cached = new JSONArray();
    private boolean degradedOffline = false;
    private static final String ORDER_CACHE_PREF="merchant_order_cache_v1";
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
        if(b!=null){historyMode=b.getBoolean("history",false);focusOrderId=b.getString("focus_order",focusOrderId);}
        build();
        if(b!=null)periodSpinner.setSelection(b.getInt("period",0));
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("history",historyMode);state.putString("focus_order",focusOrderId);
        if(periodSpinner!=null)state.putInt("period",periodSpinner.getSelectedItemPosition());
        super.onSaveInstanceState(state);
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
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title("Pesanan"),new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=outlineBtn("↻");refresh.setContentDescription("Perbarui pesanan");
        refresh.setOnClickListener(v->load(false));header.addView(refresh,new LinearLayout.LayoutParams(dp(48),-2));root.addView(header);

        realtimeStatus = tv("● Menghubungkan ke server...", 12, Color.parseColor("#B54708"), true);
        realtimeStatus.setPadding(dp(2), dp(3), dp(2), dp(5));
        realtimeStatus.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(5));
        root.addView(realtimeStatus, statusLp);

        LinearLayout tabs = row();
        Button active = btn("Aktif");
        Button hist = outlineBtn("Riwayat");
        tabs.addView(active, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(hist, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(tabs);
        Runnable updateTabs=()->{
            active.setTextColor(historyMode?BLUE:Color.WHITE);hist.setTextColor(historyMode?Color.WHITE:BLUE);
            active.setBackground(historyMode?stroke(Color.WHITE,Color.parseColor("#B9D7FF"),dp(12)):round(BLUE,dp(12)));
            hist.setBackground(historyMode?round(BLUE,dp(12)):stroke(Color.WHITE,Color.parseColor("#B9D7FF"),dp(12)));
        };
        updateTabs.run();
        active.setOnClickListener(v -> {
            if (historyMode) {
                historyMode = false;updateTabs.run();
                periodSpinner.setVisibility(Spinner.GONE);
                load(true);
            }
        });
        hist.setOnClickListener(v -> {
            if (!historyMode) {
                historyMode = true;updateTabs.run();
                periodSpinner.setVisibility(Spinner.VISIBLE);
                load(true);
            }
        });

        periodSpinner = new Spinner(this);
        periodSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Hari ini", "7 hari", "30 hari", "Semua"}));
        periodSpinner.setVisibility(historyMode?Spinner.VISIBLE:Spinner.GONE);
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
        realtimeStatus.setText(connected ? "● Pesanan tersinkron" : message);
        realtimeStatus.setTextColor(Color.parseColor(MerchantAppSettings.isDarkMode(this) ? (connected?"#70E0AE":"#FFB4AB") : (connected ? "#16803A" : "#D92D20")));
        realtimeStatus.setBackgroundColor(Color.TRANSPARENT);
    }

    private void load(boolean showLoading) {
        if(updating){refreshQueued=true;return;}
        if (loading) {
            refreshQueued=true;
            if(showLoading){list.removeAllViews();list.addView(card("Memuat pesanan…"));}
            return;
        }
        final long requestGeneration=++readGeneration;
        refreshQueued=false;
        final boolean requestedHistory=historyMode;
        final String scope=requestedHistory?"history":"active";
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

        java.util.concurrent.Future<?> readTask = MerchantNetworkExecutor.executeRead(this, "orders:"+requestGeneration, () -> {
            try {
                long sent=android.os.SystemClock.elapsedRealtime();
                JSONObject r = new JSONObject(get(BASE + "getMerchantOrders.php?scope=" + scope + "&v=" + System.currentTimeMillis()));
                final long received=android.os.SystemClock.elapsedRealtime();
                if(!r.optBoolean("success",false))throw new Exception(r.optString("message","Gagal memuat pesanan"));
                JSONArray orders = r.optJSONArray("orders");
                if (orders == null) orders = new JSONArray();
                JSONArray finalOrders = orders;
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()||requestGeneration!=readGeneration)return;
                    MerchantServerClock.sync(r,sent,received);
                    loading = false;activeRead=null;
                    if(requestedHistory!=historyMode){load(true);return;}
                    firstLoad = false;
                    cached = finalOrders;
                    degradedOffline=false;
                    saveOrderCache(scope, finalOrders, r.optLong("server_time_epoch_ms",System.currentTimeMillis()));
                    setConnectionState(true, "● Real-time aktif • server terhubung");
                    render();
                    drainRefresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()||requestGeneration!=readGeneration)return;
                    loading = false;activeRead=null;
                    if(requestedHistory!=historyMode){load(true);return;}
                    firstLoad = false;
                    setConnectionState(false, "● Offline • mode baca saja • data terakhir");
                    degradedOffline=true;
                    if(cached.length()==0) cached=loadOrderCache(historyMode?"history":"active");
                    if(cached.length()>0) render();
                    if (cached.length() == 0) {
                        list.removeAllViews();
                        list.addView(card("Koneksi gagal saat memuat pesanan. Aplikasi akan mencoba lagi otomatis."));
                    }
                    drainRefresh();
                });
            }
        });
        activeRead=readTask;
        if (readTask == null) {loading=false;realtimeStatus.setText("Sinkronisasi sedang sibuk. Akan dicoba kembali.");}
    }
    private void drainRefresh(){if(refreshQueued&&!updating&&!loading){refreshQueued=false;handler.post(()->load(false));}}
    private void invalidateRead(){readGeneration++;if(activeRead!=null)activeRead.cancel(true);activeRead=null;loading=false;refreshQueued=false;}


    private void saveOrderCache(String scope, JSONArray orders, long serverTimeMs){try{getSharedPreferences(ORDER_CACHE_PREF,MODE_PRIVATE).edit().putString(scope,orders.toString()).putLong(scope+"_saved",System.currentTimeMillis()).putLong(scope+"_server",serverTimeMs).apply();}catch(Exception ignored){}}
    private JSONArray loadOrderCache(String scope){try{String raw=getSharedPreferences(ORDER_CACHE_PREF,MODE_PRIVATE).getString(scope,"[]");return new JSONArray(raw==null?"[]":raw);}catch(Exception ignored){return new JSONArray();}}

    private boolean countdownRenderQueued;
    void onKitchenCountdownFinished(){
        if(countdownRenderQueued||isFinishing()||isDestroyed())return;
        countdownRenderQueued=true;
        list.post(()->{countdownRenderQueued=false;if(!isFinishing()&&!isDestroyed())refreshReadyActions();});
    }
    private void refreshReadyActions(){
        // Existing transaction handlers are rebuilt only if a formerly hidden ready action is now due.
        boolean needsAction=false;
        for(int i=0;i<cached.length();i++){
            JSONObject order=cached.optJSONObject(i);if(order==null)continue;
            String state=kitchenActionStatus(order,effectiveStatus(order));
            if(("preparing".equals(state)||"merchant_preparing".equals(state))&&MerchantOrderProgressView.countdownFinished(order)){
                String key=s(order,"id","order_numeric_id","order_db_id","order_id")+"|"+MerchantServerClock.readyAt(order);
                if(expiredReadyActions.add(key))needsAction=true;
            }
        }
        if(needsAction&&!historyMode)render();
    }
    private final java.util.HashSet<String> expiredReadyActions=new java.util.HashSet<>();

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
        box.setPadding(dp(12), dp(10), dp(12), dp(10));

        String displayId = s(o, "order_id", "id");
        String actionId = s(o, "id", "order_numeric_id", "order_db_id", "order_id");
        String status = effectiveStatus(o);
        boolean pending = "pending".equalsIgnoreCase(status);

        boolean dark=MerchantAppSettings.isDarkMode(this);
        int edge=focused?BLUE:Color.parseColor(dark?"#2D435C":"#DCE6F1");
        box.setBackground(stroke(Color.WHITE,edge,dp(16)));box.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));box.setLayoutParams(lp);
        TextView heading=tv(pending?"Pesanan baru":statusLabel(status),17,NAVY,true);box.addView(heading);
        // Keep the full identifier in the disclosure and every API action.
        String shortId=displayId.lastIndexOf('-')>=0?displayId.substring(displayId.lastIndexOf('-')+1):(displayId.length()>8?displayId.substring(displayId.length()-8):displayId);
        TextView ref=tv("#"+(shortId.isEmpty()?"—":shortId),11,MUTED,false);box.addView(ref);
        if(pending){
            TextView prompt=tv("Periksa pesanan, lalu pilih Terima atau Tolak.",12,MUTED,false);
            prompt.setPadding(0,dp(5),0,dp(3));box.addView(prompt);
            String age=orderAge(o);
            if(!age.isEmpty())box.addView(tv(age,11,MUTED,false));
            long created=MerchantServerClock.createdAt(o);
            MerchantCountdownView responseTimer=new MerchantCountdownView(this,created>0?created+5*60000L:0,5*60000L,"Konfirmasi","Segera konfirmasi pesanan");
            box.addView(responseTimer);
        }
        String customer=s(o,"customer_name","customer","name"),phone=s(o,"customer_phone","phone");
        String address=s(o,"delivery_address","destination_address","address"),driver=s(o,"driver","driver_name");
        String note=s(o,"customer_note","note","notes","catatan");
        if(!customer.isEmpty()){
            TextView who=tv("Untuk "+customer,13,TEXT,true);who.setPadding(0,dp(8),0,dp(4));box.addView(who);
        }
        String kitchen=kitchenActionStatus(o,status);
        if("preparing".equals(kitchen)||"merchant_preparing".equals(kitchen)||"ready".equals(kitchen)||"merchant_ready".equals(kitchen)){
            boolean ready="ready".equals(kitchen)||"merchant_ready".equals(kitchen);
            box.addView(tv(ready?"✓ Dapur · Siap diambil":"● Dapur · Sedang disiapkan",12,ready?android.graphics.Color.parseColor("#16A374"):BLUE,true));
        }
        addItems(box,o);
        if(!note.isEmpty()){
            TextView nv=tv("Catatan: "+note,13,TEXT,true);nv.setPadding(dp(10),dp(8),dp(10),dp(8));
            nv.setBackground(round(Color.parseColor(dark?"#23364A":"#EDF5FF"),dp(10)));box.addView(nv);
        }
        MerchantOrderFinanceView.attach(this,box,o);
        // Transaction buttons stay above secondary information and never depend on disclosure state.
        if(!historyMode){
            addActions(box,o,actionId,displayId,kitchenActionStatus(o,status));
            if(!driver.isEmpty())addDriverChat(box,o,actionId,displayId,driver);
        }
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(tv("Nomor lengkap: "+displayId,12,MUTED,false));
        if(!phone.isEmpty())info.addView(tv("Telepon pelanggan: "+phone,12,TEXT,false));
        if(!address.isEmpty())info.addView(tv("Alamat pengantaran: "+address,12,TEXT,false));
        info.addView(tv("Kurir: "+(driver.isEmpty()?"Belum ditugaskan":driver),12,MUTED,false));
        String cook=s(o,"cook_minutes","estimated_cook_minutes");
        try{if(Integer.parseInt(cook)>0)info.addView(tv("Waktu persiapan: "+cook+" menit",12,MUTED,false));}catch(Exception ignored){}
        info.setVisibility(View.GONE);
        Button infoToggle=outlineBtn("Info pelanggan & pengantaran ▾");
        infoToggle.setOnClickListener(v->{boolean show=info.getVisibility()!=View.VISIBLE;info.setVisibility(show?View.VISIBLE:View.GONE);infoToggle.setText(show?"Tutup informasi ▴":"Info pelanggan & pengantaran ▾");});
        box.addView(infoToggle);box.addView(info);
        MerchantOrderProgressView.attach(this,box,o);
        return box;
    }


    private void addDriverChat(LinearLayout box, JSONObject order, String actionId, String displayId, String driverName) {
        MerchantDriverCommunication.attach(this, box, order, actionId, displayId, driverName);
    }

    private String orderAge(JSONObject order) {
        long created=MerchantServerClock.createdAt(order);if(created<=0)return "";
        long minutes=Math.max(0,MerchantServerClock.now()-created)/60000L;
        if(minutes==0)return "Masuk kurang dari 1 menit lalu";
        return minutes<60?"Masuk "+minutes+" menit lalu":"Masuk lebih dari 1 jam lalu";
    }

    private void addItems(LinearLayout box, JSONObject order) {
        JSONArray items = order.optJSONArray("items");
        TextView itemHeading=tv("Yang dipesan",12,MUTED,true);itemHeading.setPadding(0,dp(6),0,dp(4));box.addView(itemHeading);
        if (items == null || items.length() == 0) {
            box.addView(tv("Rincian menu belum tersedia. Coba perbarui pesanan.", 13, Color.parseColor("#B54708"), false));
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
            TextView itemName=tv(qty+" × "+(name.isEmpty()?"Menu":name),15,TEXT,true);itemName.setPadding(0,dp(4),0,dp(3));box.addView(itemName);
            if (discountPct > 0 && originalBase > merchantBase) {
                box.addView(tv("   Diskon merchant " + (discountPct == Math.rint(discountPct) ? String.valueOf((int) discountPct) : String.valueOf(discountPct)) + "% • Harga bersih dasar " + rupiah(merchantBase), 12, Color.parseColor("#137333"), true));
            }
            if (!variant.isEmpty()) box.addView(tv("   Pilihan: " + variant, 12, MUTED, false));
            if (!note.isEmpty()) box.addView(tv("   Catatan: " + note, 12, Color.parseColor("#7A4D00"), false));
        }
    }

    private String kitchenActionStatus(JSONObject order,String global){
        if(isFinal(global)||"pending".equals(global)||"on_delivery".equals(global)||"arrived_delivery".equals(global))return global;
        String kitchen=order.optString("merchant_status","");
        if("preparing".equals(kitchen)||"merchant_preparing".equals(kitchen)||"ready".equals(kitchen)||"merchant_ready".equals(kitchen))return kitchen;
        return global;
    }

    private void addActions(LinearLayout box, JSONObject order, String actionId, String displayId, String status) {
        String st = status == null ? "" : status.trim().toLowerCase(Locale.US);
        if ("pending".equals(st)) {
            LinearLayout a = actionRow();
            Button accept = btn(updating ? "Memproses..." : "Terima pesanan");
            Button reject = outlineBtn("Tolak");
            accept.setEnabled(!updating);
            reject.setEnabled(!updating);
            a.addView(accept, actionLp());
            a.addView(reject, actionLp());
            box.addView(a);
            accept.setOnClickListener(v -> { if(degradedOffline){toast("Offline: aksi transaksi dinonaktifkan sampai koneksi kembali.");return;} if (!updating) showCookTime(actionId, displayId); });
            reject.setOnClickListener(v -> { if(degradedOffline){toast("Offline: aksi transaksi dinonaktifkan sampai koneksi kembali.");return;} if (!updating) showRejectReasons(actionId, displayId); });
        } else if ("merchant_accepted".equals(st)) {
            // Setelah Terima tidak ada lagi tombol Terima/Tolak maupun Mulai Siapkan.
            // Server otomatis memasukkan dapur ke status preparing dan countdown ditampilkan di progress card.
        } else if ("preparing".equals(st) || "merchant_preparing".equals(st)) {
            if (MerchantOrderProgressView.countdownFinished(order)) {
                Button r = btn(updating ? "Memproses..." : "✓ Pesanan Siap Diambil");
                r.setEnabled(!updating);
                r.setOnClickListener(v -> { if(degradedOffline){toast("Offline: aksi transaksi dinonaktifkan sampai koneksi kembali.");return;} if (!updating) update(actionId, displayId, "ready", "", 0); });
                box.addView(r);
            }
        }
    }

    private void showCookTime(String id, String displayId) {
        if (updating) return;
        final String[] labels = {"10 menit", "15 menit", "20 menit", "30 menit", "45 menit"};
        final int[] mins = {10, 15, 20, 30, 45};
        new AlertDialog.Builder(this)
                .setTitle("Berapa lama menyiapkan pesanan?")
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
        for(String key:new String[]{"global_status","status","driver_status","order_status","trip_status"}){
            String value=o.optString(key,"").trim().toLowerCase(Locale.US);if(MerchantOrderUiState.terminal(value))return value;
        }
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
        invalidateRead();
        updating = true;
        render(); // immediately disables every status button to prevent duplicate taps
        MerchantOrderRepository.updateStatus(this, id, displayId, status, rejectReason, cookMinutes, (success, message, networkError, receipt) -> {
            updating = false;
            if (success) {
                toast(message);
                focusOrderId = "merchant_rejected".equals(status)?"":displayId == null ? "" : displayId;
                try{cached=MerchantOrderUiState.acknowledge(cached,id,displayId,status,rejectReason,cookMinutes,receipt,historyMode,MerchantServerClock.now());}catch(Exception ignored){}
                degradedOffline=false;
                saveOrderCache(historyMode?"history":"active",cached,MerchantServerClock.now());
                getSharedPreferences(ORDER_CACHE_PREF,MODE_PRIVATE).edit().remove(historyMode?"active":"history").apply();
                render();
                load(false);
            } else {
                if(networkError)degradedOffline=true;
                render();
                if(networkError)load(false);else drainRefresh();
                alert(networkError ? "Memeriksa status" : "Gagal", message);
            }
        });
    }
}
