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
    private String screenAccountKey="";
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
    private static final String ORDER_CACHE_PREF="merchant_order_cache_v2";
    private Spinner periodSpinner;
    private Spinner outletSpinner;
    private int outletFilter;
    private long activePageId, nextActiveId;
    private boolean activePagePending, nextActivePending;
    private final java.util.ArrayList<Integer> outletIds = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> outletNames = new java.util.ArrayList<>();
    private boolean historyHasMore;
    private long historyBeforeId;

    private final BroadcastReceiver realtimeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String orderId = intent == null ? "" : intent.getStringExtra(MerchantRealtime.EXTRA_ORDER_ID);
            if (orderId != null && !orderId.trim().isEmpty()) focusOrderId = orderId.trim();
            load(false);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        screenAccountKey=currentAccountCacheName();
        focusOrderId = getIntent().getStringExtra("order_id");
        if (focusOrderId == null) focusOrderId = "";
        if(b!=null && focusOrderId.isEmpty()){historyMode=b.getBoolean("history",false);focusOrderId=b.getString("focus_order",focusOrderId);}
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
        if (isBranchChange(intent)) return;
        setIntent(intent);
        String id = intent == null ? "" : intent.getStringExtra("order_id");
        if (id != null && !id.trim().isEmpty()) {
            focusOrderId = id.trim();
            activePageId = 0;
            activePagePending = false;
            outletFilter = 0;
            if (outletSpinner != null && !outletIds.isEmpty()) outletSpinner.setSelection(0);
            invalidateRead();
        }
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
        header.addView(title("Aktivitas Semua Cabang"),new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=outlineBtn("↻");refresh.setContentDescription("Perbarui pesanan");refresh.setPadding(0,0,0,0);refresh.setElevation(0);
        refresh.setOnClickListener(v->load(false));header.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);

        realtimeStatus = tv("● Menghubungkan ke server...", 12, Color.parseColor("#B54708"), true);
        realtimeStatus.setPadding(dp(2), dp(3), dp(2), dp(5));
        realtimeStatus.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, 0, 0, dp(5));
        root.addView(realtimeStatus, statusLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(stroke(Color.WHITE, Color.parseColor("#DDE7F4"), dp(15)));
        Button active = btn("Aktif");
        Button hist = outlineBtn("Riwayat");
        for (Button tab : new Button[]{active, hist}) {
            tab.setTextSize(13);
            tab.setPadding(dp(8), 0, dp(8), 0);
            tab.setElevation(0);
        }
        LinearLayout.LayoutParams activeLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        activeLp.rightMargin = dp(3);
        tabs.addView(active, activeLp);
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        historyLp.leftMargin = dp(3);
        tabs.addView(hist, historyLp);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(-1, -2);
        tabsLp.bottomMargin = dp(10);
        root.addView(tabs, tabsLp);
        Runnable updateTabs = () -> {
            active.setTextColor(historyMode ? BLUE : Color.WHITE);
            hist.setTextColor(historyMode ? Color.WHITE : BLUE);
            active.setBackground(historyMode ? round(Color.TRANSPARENT, dp(11)) : round(BLUE, dp(11)));
            hist.setBackground(historyMode ? round(BLUE, dp(11)) : round(Color.TRANSPARENT, dp(11)));
            active.setSelected(!historyMode);
            hist.setSelected(historyMode);
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

        outletSpinner = new Spinner(this);
        root.addView(outletSpinner);
        outletSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= outletIds.size() || outletIds.get(pos) == outletFilter) return;
                if (updating) {
                    outletSpinner.setSelection(Math.max(0, outletIds.indexOf(outletFilter)));
                    return;
                }
                outletFilter = outletIds.get(pos);
                activePageId = 0;
                activePagePending = false;
                focusOrderId = "";
                invalidateRead();
                cached = new JSONArray();
                firstLoad = true;
                load(true);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

    }

    private void start() {
        stop();
        firstLoad = true;
        load(true);
        task = () -> {
            if (!historyMode && !updating && !loading) load(false);
            handler.postDelayed(task, WaveLoadGuard.jitter(FALLBACK_POLL_MS));
        };
        handler.postDelayed(task, WaveLoadGuard.jitter(FALLBACK_POLL_MS));
    }

    private void stop() {
        if (task != null) handler.removeCallbacks(task);
    }

    private void setConnectionState(boolean connected, String message) {
        if (realtimeStatus == null) return;
        realtimeStatus.setText(connected ? "● Terhubung • pesanan tersinkron" : message);
        realtimeStatus.setTextColor(Color.parseColor(MerchantAppSettings.isDarkMode(this) ? (connected?"#70E0AE":"#FFB4AB") : (connected ? "#16803A" : "#D92D20")));
        realtimeStatus.setBackgroundColor(Color.TRANSPARENT);
    }

    private void setOutletOptions(JSONArray options) {
        if (options == null) return;
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        ids.add(0);
        names.add("Semua cabang");
        for (int i = 0; i < options.length(); i++) {
            JSONObject outlet = options.optJSONObject(i);
            if (outlet == null || outlet.optInt("id") <= 0) continue;
            ids.add(outlet.optInt("id"));
            names.add(outlet.optString("name", "Cabang"));
        }
        if (ids.equals(outletIds) && names.equals(outletNames)) return;
        outletIds.clear(); outletIds.addAll(ids);
        outletNames.clear(); outletNames.addAll(names);
        outletSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        outletSpinner.setSelection(Math.max(0, ids.indexOf(outletFilter)));
    }

    private void load(boolean showLoading) { load(showLoading,false); }

    private void load(boolean showLoading, boolean append) {
        if(append && (!historyMode || !historyHasMore || degradedOffline || loading || updating))return;
        if(!sameAccount()){invalidateRead();finish();return;}
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
        final long before=append?historyBeforeId:0;
        final String outletParam = outletFilter > 0 ? "&outlet_id=" + outletFilter : "";
        final String activeCursor = !requestedHistory && activePageId > 0
                ? "&active_after_id=" + activePageId + "&active_after_pending=" + (activePagePending ? "1" : "0") : "";
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
                JSONObject r = new JSONObject(get(BASE + "getMerchantOrders.php?outlets=all&scope=" + scope + outletParam + activeCursor + (before>0?"&before_id="+before:"") + "&v=" + System.currentTimeMillis()));
                final long received=android.os.SystemClock.elapsedRealtime();
                if(!r.optBoolean("success",false))throw new Exception(r.optString("message","Gagal memuat pesanan"));
                JSONArray orders = r.optJSONArray("orders");
                if (orders == null) orders = new JSONArray();
                JSONArray finalOrders = orders;
                runOnUiThread(() -> {
                    if(!sameAccount()||isFinishing()||isDestroyed()||requestGeneration!=readGeneration)return;
                    MerchantServerClock.sync(r,sent,received);
                    loading = false;activeRead=null;
                    if(requestedHistory!=historyMode){load(true);return;}
                    firstLoad = false;
                    setOutletOptions(r.optJSONArray("outlet_options"));
                    nextActiveId = requestedHistory ? 0 : r.optLong("next_active_id", 0);
                    nextActivePending = r.optBoolean("next_active_pending");
                    if(append){
                        JSONArray merged=new JSONArray();java.util.HashSet<String> ids=new java.util.HashSet<>();
                        for(int i=0;i<cached.length();i++){JSONObject o=cached.optJSONObject(i);if(o!=null&&ids.add(s(o,"id","order_id")))merged.put(o);}
                        for(int i=0;i<finalOrders.length();i++){JSONObject o=finalOrders.optJSONObject(i);if(o!=null&&ids.add(s(o,"id","order_id")))merged.put(o);}
                        cached=merged;
                    }else cached=finalOrders;
                    long next=r.optLong("next_before_id",0);
                    historyHasMore=requestedHistory&&r.optBoolean("has_more",false)&&next>0&&(!append||next<before);
                    historyBeforeId=historyHasMore?next:0;
                    degradedOffline=r.optBoolean("stale",false);
                    saveOrderCache(scope, cached, r.optLong("server_time_epoch_ms",System.currentTimeMillis()));
                    setConnectionState(!degradedOffline, degradedOffline ? "● Sinkronisasi tertunda • mode baca saja" : "● Real-time aktif • server terhubung");
                    render();
                    drainRefresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if(!sameAccount()||isFinishing()||isDestroyed()||requestGeneration!=readGeneration)return;
                    loading = false;activeRead=null;
                    if(requestedHistory!=historyMode){load(true);return;}
                    firstLoad = false;
                    setConnectionState(false, "● Offline • mode baca saja • data terakhir");
                    degradedOffline=true;
                    if ((!append || cached.length()==0) && (historyMode || activePageId==0)) cached=loadOrderCache(historyMode?"history":"active");
                    historyHasMore=false;historyBeforeId=0;
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


    private String currentAccountCacheName(){
        SessionManager session = new SessionManager(this);
        return ORDER_CACHE_PREF + "_" + session.getId() + "_" + "all_outlets";
    }

    private String orderCacheName(){
        return (screenAccountKey.isEmpty()?currentAccountCacheName():screenAccountKey) + "_outlet_" + outletFilter;
    }
    private boolean sameAccount(){return !screenAccountKey.isEmpty()&&screenAccountKey.equals(currentAccountCacheName());}
    private void saveOrderCache(String scope,JSONArray orders,long serverTimeMs){
        if(!sameAccount() || ("active".equals(scope) && activePageId > 0))return;
        try{getSharedPreferences(orderCacheName(),MODE_PRIVATE).edit().putString(scope,orders.toString()).putLong(scope+"_saved",System.currentTimeMillis()).putLong(scope+"_server",serverTimeMs).apply();}catch(Exception ignored){}
    }
    private JSONArray loadOrderCache(String scope){
        if(!sameAccount())return new JSONArray();
        try{long saved=getSharedPreferences(orderCacheName(),MODE_PRIVATE).getLong(scope+"_saved",0L);long age=System.currentTimeMillis()-saved;if(saved<=0||age<0||age>86400000L)return new JSONArray();String raw=getSharedPreferences(orderCacheName(),MODE_PRIVATE).getString(scope,"[]");return new JSONArray(raw==null?"[]":raw);}catch(Exception ignored){return new JSONArray();}
    }

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

    private void moveActivePage(long id, boolean pending) {
        activePageId = id;
        activePagePending = pending;
        focusOrderId = "";
        firstLoad = true;
        load(true);
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

        if (!historyMode && activePageId > 0) {
            Button first = outlineBtn("Kembali ke pesanan prioritas");
            first.setEnabled(!loading && !updating);
            first.setOnClickListener(v -> moveActivePage(0, false));
            list.addView(first, 0);
        }
        if (!historyMode && nextActiveId > 0 && !degradedOffline) {
            Button more = outlineBtn("Pesanan aktif berikutnya");
            more.setEnabled(!loading && !updating);
            more.setOnClickListener(v -> moveActivePage(nextActiveId, nextActivePending));
            list.addView(more);
        }
        if(historyMode && historyHasMore && !degradedOffline){
            Button more=outlineBtn(loading?"Memuat...":"Muat riwayat berikutnya");more.setEnabled(!loading&&!updating);
            more.setOnClickListener(v->load(false,true));list.addView(more);
        }
        if (shown == 0) {
            list.addView(emptyOrders());
        }
    }

    private LinearLayout emptyOrders() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(22), dp(18), dp(22));
        box.setBackground(stroke(Color.WHITE, Color.parseColor("#DFE9F5"), dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        box.setLayoutParams(params);
        TextView icon = tv(historyMode ? "↶" : "☷", 25, BLUE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(SOFT_BLUE, dp(14)));
        box.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = tv(historyMode ? "Belum ada riwayat" : "Belum ada pesanan aktif", 15, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(10), 0, dp(3));
        box.addView(heading);
        String message = historyMode ? "Coba pilih periode lain untuk melihat pesanan."
                : "Pesanan baru akan muncul di sini.";
        if (degradedOffline) message = "Data terakhir ditampilkan. Menunggu koneksi kembali.";
        TextView description = tv(message, 12, MUTED, false);
        description.setGravity(Gravity.CENTER);
        box.addView(description);
        return box;
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
        box.setPadding(dp(11), dp(8), dp(11), dp(8));

        String outletName = o.optString("restaurant_name");
        if (!outletName.isEmpty()) box.addView(tv(outletName, 12, BLUE, true));
        String displayId = s(o, "order_id", "id");
        String actionId = s(o, "id", "order_numeric_id", "order_db_id", "order_id");
        String status = effectiveStatus(o);
        boolean pending = "pending".equalsIgnoreCase(status);

        boolean dark=MerchantAppSettings.isDarkMode(this);
        int edge=focused?BLUE:Color.parseColor(dark?"#2D435C":"#DCE6F1");
        box.setBackground(stroke(Color.WHITE,edge,dp(16)));box.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));box.setLayoutParams(lp);
        LinearLayout header=row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=tv(pending?"Pesanan baru":statusLabel(status),16,NAVY,true);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        // Keep the full identifier in the disclosure and every API action.
        String shortId=displayId.lastIndexOf('-')>=0?displayId.substring(displayId.lastIndexOf('-')+1):(displayId.length()>8?displayId.substring(displayId.length()-8):displayId);
        TextView ref=tv("#"+(shortId.isEmpty()?"—":shortId),11,MUTED,true);
        ref.setPadding(dp(6),dp(3),dp(6),dp(3));
        ref.setBackground(round(Color.parseColor(dark?"#1B334A":"#EDF5FF"),dp(8)));
        header.addView(ref);box.addView(header);
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
            TextView who=tv("Untuk "+customer,13,TEXT,true);who.setPadding(0,dp(4),0,dp(2));box.addView(who);
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
            addCancellationActions(box,o,actionId,displayId);
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
        TextView infoToggle=tv("Info pelanggan & pengantaran  ▾",12,BLUE,true);
        infoToggle.setMinHeight(dp(48));infoToggle.setGravity(Gravity.CENTER_VERTICAL);
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
        TextView itemHeading=tv("Yang dipesan",12,MUTED,true);itemHeading.setPadding(0,dp(4),0,dp(2));box.addView(itemHeading);
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
            TextView itemName=tv(qty+" × "+(name.isEmpty()?"Menu":name),15,TEXT,true);itemName.setPadding(0,dp(2),0,dp(2));box.addView(itemName);
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

    private void addCancellationActions(LinearLayout box, JSONObject order, String id, String displayId) {
        String global = order.optString("global_status", order.optString("status"));
        if (!order.isNull("picked_up_at") && !order.optString("picked_up_at").isEmpty()) return;
        switch (global) {
            case "merchant_accepted": case "preparing": case "ready": case "driver_accepted":
            case "taken": case "accepted": case "arrived_pickup": break;
            default: return;
        }
        JSONObject request = order.optJSONObject("food_cancel_request");
        boolean pending = request != null && "pending".equals(request.optString("state"));
        if (pending) box.addView(tv("Pengajuan pembatalan · " + request.optString("reason"), 13, TEXT, true));
        Button cancel = outlineBtn(pending ? "Setujui pembatalan" : "Batalkan pesanan");
        if(!pending){
            cancel.setBackgroundColor(Color.TRANSPARENT);
            cancel.setElevation(0);
            cancel.setTextSize(12);
            cancel.setTextColor(Color.parseColor(MerchantAppSettings.isDarkMode(this)?"#FDA4AF":"#B42318"));
            cancel.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        }
        cancel.setEnabled(!degradedOffline && !updating);
        cancel.setOnClickListener(v -> confirmFoodCancellation(id, displayId, pending));
        box.addView(cancel);
        if (pending) {
            Button decline = outlineBtn("Tolak pengajuan");
            decline.setEnabled(!degradedOffline && !updating);
            decline.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Tolak pengajuan pembatalan?")
                    .setMessage("Pesanan akan tetap berjalan.")
                    .setNegativeButton("Kembali", null)
                    .setPositiveButton("Tolak", (dialog, which) -> update(id, displayId, "cancel_declined", "", 0))
                    .show());
            box.addView(decline);
        }
    }

    private void confirmFoodCancellation(String id, String displayId, boolean pending) {
        android.widget.CheckBox restore = new android.widget.CheckBox(this);
        restore.setText("Kembalikan juga stok lama atau menu siap jika masih layak dijual");
        new AlertDialog.Builder(this)
                .setTitle(pending ? "Setujui pembatalan?" : "Batalkan pesanan?")
                .setMessage("Pesanan akan dibatalkan dan pembayaran pelanggan dikembalikan jika sudah ditahan.")
                .setView(restore)
                .setNegativeButton("Kembali", null)
                .setPositiveButton("Konfirmasi", (dialog, which) ->
                        update(id, displayId, pending ? "cancel_approved" : "merchant_cancel", "", 0, restore.isChecked()))
                .show();
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
            // Estimasi memasak bukan syarat kesiapan makanan.
            Button r = btn(updating ? "Memproses..." : "✓ Pesanan Siap Diambil");
            r.setEnabled(!updating);
            r.setOnClickListener(v -> { if(degradedOffline){toast("Offline: aksi transaksi dinonaktifkan sampai koneksi kembali.");return;} if (!updating) update(actionId, displayId, "ready", "", 0); });
            box.addView(r);
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
            case "finished": case "completed": case "finish": case "done": case "delivered": case "selesai": return 6;
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
            case "completed": case "finish": case "done": case "delivered": case "selesai": return "Selesai";
            case "canceled":
            case "cancelled": return "Dibatalkan";
            default: return s;
        }
    }

    private void update(String id, String displayId, String status, String rejectReason, int cookMinutes) {
        update(id, displayId, status, rejectReason, cookMinutes, false);
    }

    private void update(String id, String displayId, String status, String rejectReason, int cookMinutes, boolean restoreStock) {
        if(!sameAccount()){finish();return;}
        if (updating || degradedOffline) return;
        if (id == null || id.trim().isEmpty()) {
            alert("Data tidak lengkap", "ID pesanan tidak ditemukan.");
            return;
        }
        invalidateRead();
        updating = true;
        render(); // immediately disables every status button to prevent duplicate taps
        int outletId = 0;
        for (int i = 0; i < cached.length(); i++) {
            JSONObject order = cached.optJSONObject(i);
            if (order != null && id.equals(s(order,"id","order_numeric_id","order_db_id","order_id"))) { outletId = order.optInt("restaurant_id"); break; }
        }
        MerchantOrderRepository.updateStatus(this, id, displayId, outletId, status, rejectReason, cookMinutes, restoreStock, (success, message, networkError, receipt) -> {
            if(!sameAccount()||isFinishing()||isDestroyed())return;
            updating = false;
            if (success) {
                toast(message);
                if (status.startsWith("cancel_") || "merchant_cancel".equals(status)) {
                    // Keep the affected action disabled until a fresh server snapshot is received.
                    cached = new JSONArray();
                    getSharedPreferences(orderCacheName(),MODE_PRIVATE).edit().clear().apply();
                    firstLoad = true;
                    load(true);
                    return;
                }
                focusOrderId = "merchant_rejected".equals(status)?"":displayId == null ? "" : displayId;
                try{cached=MerchantOrderUiState.acknowledge(cached,id,displayId,status,rejectReason,cookMinutes,receipt,historyMode,MerchantServerClock.now());}catch(Exception ignored){}
                degradedOffline=false;
                saveOrderCache(historyMode?"history":"active",cached,MerchantServerClock.now());
                String otherScope=historyMode?"active":"history";getSharedPreferences(orderCacheName(),MODE_PRIVATE).edit().remove(otherScope).remove(otherScope+"_saved").remove(otherScope+"_server").apply();
                render();
                load(false);
            } else {
                if(networkError)degradedOffline=true;
                render();
                if(networkError || status.startsWith("cancel_") || "merchant_cancel".equals(status))load(false);else drainRefresh();
                alert(networkError ? "Memeriksa status" : "Gagal", message);
            }
        });
    }
}
