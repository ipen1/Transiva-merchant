package com.transiva.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;

public class MerchantDashboardActivity extends MerchantBaseActivity {
    private LinearLayout root, grid, ordersTile;
    private TextView nameText, statusText, descText, networkText, clusterText, todayText, ratingText, reviewText, revenueText, orderBadgeText, orderTileIcon, orderTileTitle, orderTileSub;
    private Button storeStatusBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashSet<String> notifiedOrders = new HashSet<>();
    private boolean firstLoad = true;
    private boolean storeUpdating;
    private Runnable autoTask;
    private boolean realtimeRegistered = false;
    private final BroadcastReceiver realtimeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadAll();
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        build();
    }

    @Override protected void onResume(){
        super.onResume();
        if(!realtimeRegistered){
            MerchantRealtime.register(this, realtimeReceiver);
            realtimeRegistered = true;
        }
        startAuto();
    }

    @Override protected void onPause(){
        super.onPause();
        stopAuto();
        if(realtimeRegistered){
            try { unregisterReceiver(realtimeReceiver); } catch(Exception ignored){}
            realtimeRegistered = false;
        }
    }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        LinearLayout header = actionRow(); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = new LinearLayout(this); identity.setOrientation(LinearLayout.VERTICAL);
        identity.addView(tv("TRANSIVA MERCHANT", 10, BLUE, true));
        nameText = tv("Halo, " + (username().isEmpty() ? "Merchant" : username()), 21, NAVY, true);
        identity.addView(nameText); header.addView(identity, new LinearLayout.LayoutParams(stackControls()?-1:0,-2,stackControls()?0:1));
        Button more = outlineBtn("Lainnya ⋯");
        more.setOnClickListener(v -> showMore());
        header.addView(more, new LinearLayout.LayoutParams(stackControls()?-1:dp(100),-2)); root.addView(header);

        LinearLayout statusCard = new LinearLayout(this); statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(12),dp(10),dp(12),dp(10));
        statusCard.setBackground(stroke(Color.WHITE,Color.parseColor("#E2ECF8"),dp(16)));
        root.addView(statusCard,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout storeRow = actionRow(); storeRow.setGravity(Gravity.CENTER_VERTICAL);
        statusText = tv("Memuat toko…",17,NAVY,true);
        storeRow.addView(statusText,actionLp());
        storeStatusBtn=outlineBtn("Memuat…"); storeStatusBtn.setEnabled(false);
        storeStatusBtn.setOnClickListener(v->toggleStore()); storeRow.addView(storeStatusBtn,actionLp()); statusCard.addView(storeRow);
        descText = tv("",12,MUTED,false); descText.setVisibility(android.view.View.GONE);statusCard.addView(descText);
        networkText=tv("● Menghubungkan…",11,MUTED,false);statusCard.addView(networkText);
        clusterText=tv("Memuat area…",11,MUTED,false); clusterText.setPadding(0,dp(4),0,0);statusCard.addView(clusterText);
        LinearLayout stats1=row(); statusCard.addView(stats1);
        LinearLayout stats2=stats1;
        if(screenWidthDp()<600 || stackControls()){stats2=row();statusCard.addView(stats2);}
        todayText=stat(stats1,"Pesanan hari ini","0");revenueText=stat(stats1,"Omzet hari ini","Rp 0");
        ratingText=stat(stats2,"Rating","0.0 ★");reviewText=stat(stats2,"Ulasan","0");

        TextView section=tv("Kelola usaha",14,NAVY,true);section.setPadding(0,dp(12),0,dp(6));root.addView(section);
        grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);root.addView(grid);
        LinearLayout r1=shortcutRow();grid.addView(r1);
        ordersTile=orderTile(r1);tile(r1,"＋","Tambah Menu",()->open(MerchantMenuListActivity.class));
        LinearLayout r2=shortcutRow();grid.addView(r2);
        tile(r2,"◷","Operasional",()->open(MerchantOperationsActivity.class));
        tile(r2,"Rp","Keuangan",()->open(MerchantFinanceActivity.class));
        LinearLayout r3=shortcutRow();grid.addView(r3);
        tile(r3,"▥","Analitik",()->open(MerchantAnalyticsActivity.class));
        tile(r3,"♧","Pengikut Toko",()->openFollowers());
        Button refresh=outlineBtn("↻ Perbarui ringkasan");refresh.setOnClickListener(v->loadAll());root.addView(refresh);
    }
    private LinearLayout shortcutRow(){LinearLayout r=row();if(stackControls())r.setOrientation(LinearLayout.VERTICAL);return r;}
    private void openFollowers(){
        Intent i=new Intent(this,MerchantKabarMitraActivity.class);
        i.putExtra("show_followers",true);startActivity(i);
    }
    private void showMore(){
        new android.app.AlertDialog.Builder(this).setTitle("Menu Merchant")
            .setItems(new String[]{"Profil toko","Rating & ulasan","Trans Asisten","Notifikasi","Diagnostik","Pengaturan"},(d,w)->{
                switch(w){
                    case 0:open(MerchantRestaurantProfileActivity.class);break;
                    case 1:open(MerchantReviewsActivity.class);break;
                    case 2:open(MerchantTransAssistantActivity.class);break;
                    case 3:open(MerchantNotificationCenterActivity.class);break;
                    case 4:open(MerchantDiagnosticsActivity.class);break;
                    case 5:open(MerchantSettingsActivity.class);break;
                }
            }).setNegativeButton("Tutup",null).show();
    }

    private TextView stat(LinearLayout parent, String label, String value){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(8), dp(8), dp(6));
        box.setBackground(round(Color.parseColor("#F1F6FF"), dp(16)));
        TextView l = tv(label, 11, MUTED, false);
        TextView v = tv(value, 15, NAVY, true);
        l.setGravity(Gravity.CENTER); v.setGravity(Gravity.CENTER);
        box.addView(l); box.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), dp(6), dp(3), 0);
        parent.addView(box, lp);
        return v;
    }

    private void tile(LinearLayout parent, String icon, String text, final Runnable action){
        LinearLayout t = baseTile();
        TextView ic = tv(icon, 20, BLUE, true);
        TextView tx = tv(text, 14, TEXT, true);
        ic.setGravity(Gravity.CENTER); tx.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.addView(ic, new LinearLayout.LayoutParams(dp(32),dp(40)));
        t.addView(tx, new LinearLayout.LayoutParams(0,-2,1));
        t.setOnClickListener(v -> action.run());
        parent.addView(t, tileLp());
    }

    private LinearLayout orderTile(LinearLayout parent){
        LinearLayout t=baseTile();
        orderTileIcon=tv("◉",20,BLUE,true);orderTileIcon.setGravity(Gravity.CENTER);
        t.addView(orderTileIcon,new LinearLayout.LayoutParams(dp(32),dp(40)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        orderTileTitle=tv("Pesanan",14,TEXT,true);orderTileSub=tv("Belum ada yang baru",11,MUTED,false);
        labels.addView(orderTileTitle);labels.addView(orderTileSub);t.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        orderBadgeText=tv("",11,Color.WHITE,true);orderBadgeText.setGravity(Gravity.CENTER);
        orderBadgeText.setPadding(dp(5),dp(3),dp(5),dp(3));
        orderBadgeText.setBackground(round(Color.parseColor("#DC2626"),dp(10)));orderBadgeText.setVisibility(TextView.GONE);
        t.addView(orderBadgeText);t.setOnClickListener(v->open(MerchantOrdersActivity.class));
        parent.addView(t,tileLp());return t;
    }
    private LinearLayout baseTile(){
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.HORIZONTAL);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinimumHeight(dp(64));t.setPadding(dp(8),dp(8),dp(8),dp(8));
        t.setBackground(stroke(Color.WHITE,Color.parseColor("#E2ECF8"),dp(14)));
        t.setClickable(true);t.setFocusable(true);return t;
    }
    private LinearLayout.LayoutParams tileLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(stackControls()?-1:0,-2,stackControls()?0:1);
        lp.setMargins(dp(3),dp(3),dp(3),dp(3));return lp;
    }

    private void setOrderTileActive(int count){
        if(ordersTile == null) return;
        if(count > 0){
            ordersTile.setBackground(stroke(Color.parseColor("#EAF3FF"), BLUE, dp(20)));
            ordersTile.setElevation(0);
            if(orderBadgeText != null){
                orderBadgeText.setText(count > 99 ? "99+" : String.valueOf(count));
                orderBadgeText.setVisibility(TextView.VISIBLE);
            }
            if(orderTileIcon != null) orderTileIcon.setText("●");
            if(orderTileTitle != null) orderTileTitle.setTextColor(BLUE);
            if(orderTileSub != null) {
                orderTileSub.setText(count + " order baru");
                orderTileSub.setTextColor(Color.parseColor("#EF4444"));
            }
        }else{
            ordersTile.setBackground(round(Color.WHITE, dp(20)));
            ordersTile.setElevation(0);
            if(orderBadgeText != null) orderBadgeText.setVisibility(TextView.GONE);
            if(orderTileIcon != null) orderTileIcon.setText("◉");
            if(orderTileTitle != null) orderTileTitle.setTextColor(TEXT);
            if(orderTileSub != null) {
                orderTileSub.setText("Belum ada yang baru");
                orderTileSub.setTextColor(MUTED);
            }
        }
    }

    private void startAuto(){
        stopAuto();
        firstLoad = true;
        loadAll();
        autoTask = () -> { loadAll(); handler.postDelayed(autoTask, WaveLoadGuard.jitter(90000L)); };
        handler.postDelayed(autoTask, WaveLoadGuard.jitter(90000L));
    }

    private void stopAuto(){
        if(autoTask != null) handler.removeCallbacks(autoTask);
    }

    private void loadAll(){
        final String u = username();
        if(u.isEmpty()){ alert("Sesi", "Silakan login ulang."); return; }
        if(networkText != null){
            networkText.setText(MerchantConnectivity.isOnline(this) ? "● Menghubungkan..." : "● Tidak ada koneksi internet");
            networkText.setTextColor(Color.parseColor(MerchantConnectivity.isOnline(this) ? "#B54708" : "#D92D20"));
        }
        MerchantNetworkExecutor.executeRead(this, "dashboard", () -> {
            try {
                String dash = get(BASE + "getMerchantDashboard.php?v=" + System.currentTimeMillis());
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed()) return;
                    if(networkText != null){
                        networkText.setText("● Terhubung");
                        networkText.setTextColor(Color.parseColor("#16803A"));
                    }
                    showDash(dash);
                });
            } catch(Exception e){
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed())return;
                    if(networkText != null){
                        networkText.setText("● Menghubungkan kembali... data terakhir tetap ditampilkan");
                        networkText.setTextColor(Color.parseColor("#D92D20"));
                    }
                });
            }
        });
    }

    private void showDash(String json){
        try {
            JSONObject d = new JSONObject(json);
            boolean verified = d.optInt("is_verified", 0) == 1;
            boolean open = verified && d.optInt("is_open", 0) == 1;
            statusText.setText(!verified ? "Menunggu verifikasi" : (open ? "● Toko buka" : "● Toko tutup"));
            statusText.setTextColor(MerchantAppSettings.isDarkMode(this) ? Color.parseColor(open?"#70E0AE":"#EEF5FF") : (open?Color.parseColor("#16803A"):NAVY));
            descText.setText(!verified ? "Toko dapat dibuka setelah diverifikasi admin." : "");
            descText.setVisibility(!verified ? android.view.View.VISIBLE : android.view.View.GONE);
            todayText.setText(String.valueOf(d.optInt("today_orders", 0)));
            ratingText.setText(String.format(java.util.Locale.US, "%.1f ⭐", d.optDouble("rating", 0)));
            reviewText.setText(String.valueOf(d.optInt("review_count", 0)));
            if(revenueText != null) revenueText.setText(rupiah(d.optLong("today_revenue", 0)));
            JSONObject cluster = d.optJSONObject("cluster");
            if(clusterText != null){
                TransivaCluster.Item area = TransivaCluster.fromServer(cluster);
                clusterText.setText(TransivaCluster.label(area));
            }
            int activeOrders = d.optInt("active_orders", 0);
            setOrderTileActive(d.optInt("pending_orders", 0));
            if(activeOrders > 0 && descText != null){
                descText.setText((!verified ? "Menunggu verifikasi • " : "")+activeOrders+" pesanan aktif");
                descText.setVisibility(android.view.View.VISIBLE);
            }
            firstLoad = false;
            storeStatusBtn.setText(!verified ? "Menunggu admin" : (open ? "Tutup toko" : "Buka toko"));
            storeStatusBtn.setEnabled(verified && !storeUpdating);
            storeStatusBtn.setTag(open ? "1" : "0");
        } catch(Exception e){ descText.setVisibility(android.view.View.VISIBLE);descText.setText("Ringkasan belum terbaca. Coba perbarui."); }
    }

    private void checkOrders(String json){
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("orders");
            int pending = 0;
            HashSet<String> current = new HashSet<>();
            if(arr != null){
                for(int i=0;i<arr.length();i++){
                    JSONObject o = arr.optJSONObject(i);
                    if(o == null) continue;
                    String st = o.optString("status","").trim().toLowerCase();
                    if(!"pending".equals(st)) continue;
                    pending++;
                    String id = s(o,"order_id","id");
                    if(id.isEmpty()) continue;
                    current.add(id);
                    if(!notifiedOrders.contains(id)){
                        if(!firstLoad) toast("Pesanan merchant baru #" + id);
                        notifiedOrders.add(id);
                    }
                }
            }
            notifiedOrders.retainAll(current);
            firstLoad = false;
            setOrderTileActive(pending);
        } catch(Exception ignored){ setOrderTileActive(0); }
    }

    private void toggleStore(){
        if(storeUpdating || storeStatusBtn.getTag()==null) return;
        storeUpdating=true;
        final String u = username();
        final int current = "1".equals(String.valueOf(storeStatusBtn.getTag())) ? 1 : 0;
        final int next = current == 1 ? 0 : 1;
        storeStatusBtn.setEnabled(false);
        storeStatusBtn.setText("Memproses...");
        boolean accepted=MerchantNetworkExecutor.executeWrite("restaurant-open-toggle", () -> {
            try {
                JSONObject p = new JSONObject();
                p.put("is_open", next);
                JSONObject r = new JSONObject(postJson(BASE + "updateRestaurantStatus.php", p));
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()) return;
                    storeUpdating=false;
                    toast(r.optString("message", r.optBoolean("success") ? "Berhasil" : "Gagal"));
                    storeStatusBtn.setEnabled(true);
                    loadAll();
                });
            } catch(Exception e){ runOnUiThread(() -> { if(isFinishing()||isDestroyed())return;storeUpdating=false;storeStatusBtn.setEnabled(true); alert("Error","Koneksi gagal"); loadAll(); }); }
        });
        if(!accepted){storeUpdating=false;storeStatusBtn.setEnabled(true);loadAll();}
    }
}
