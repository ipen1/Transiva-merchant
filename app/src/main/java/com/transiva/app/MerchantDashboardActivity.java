package com.transiva.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;

public class MerchantDashboardActivity extends MerchantBaseActivity {
    private LinearLayout ordersTile;
    private TextView statusText, descText, networkText, clusterText, todayText, ratingText, reviewText, revenueText, orderBadgeText, orderTileIcon, orderTileTitle, orderTileSub;
    private Button storeStatusBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean dashboardLoaded;
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

    private void build() {
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = column();
        TextView brand = tv("TRANSIVA MERCHANT", 10, accent(), true);
        brand.setLetterSpacing(.10f);
        identity.addView(brand);
        TextView name = tv("Halo, " + (username().isEmpty() ? "Merchant" : username()), 23, NAVY, true);
        name.setLetterSpacing(-.025f);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(name);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        Button more = dashboardAction("Lainnya ⋯", () -> open(MerchantMoreActivity.class));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-2, -2);
        moreParams.leftMargin = dp(8);
        header.addView(more, moreParams);
        root.addView(header);

        LinearLayout summary = column();
        summary.setPadding(dp(12), dp(9), dp(12), dp(10));
        summary.setBackground(stroke(Color.WHITE, 0xFFDCE8F5, dp(18)));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(6);
        root.addView(summary, summaryParams);

        LinearLayout storeRow = row();
        storeRow.setGravity(Gravity.CENTER_VERTICAL);
        storeRow.setPadding(0, 0, 0, 0);
        if (stackControls()) storeRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout storeInfo = column();
        statusText = tv("Memuat toko…", 16, NAVY, true);
        networkText = tv("Menghubungkan…", 11, MUTED, false);
        storeInfo.addView(statusText);
        storeInfo.addView(networkText);
        storeRow.addView(storeInfo, new LinearLayout.LayoutParams(stackControls() ? -1 : 0, -2, stackControls() ? 0 : 1));
        storeStatusBtn = dashboardAction("Memuat…", this::toggleStore);
        storeStatusBtn.setEnabled(false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(stackControls() ? -1 : -2, -2);
        statusParams.leftMargin = dp(stackControls() ? 0 : 6);
        storeRow.addView(storeStatusBtn, statusParams);
        summary.addView(storeRow);

        clusterText = tv("Memuat area…", 11, MUTED, false);
        summary.addView(clusterText);
        descText = tv("", 12, MUTED, false);
        descText.setVisibility(View.GONE);
        summary.addView(descText);

        LinearLayout stats1 = pairRow();
        LinearLayout stats2 = screenWidthDp() >= 620 && !stackControls() ? stats1 : pairRow();
        summary.addView(stats1);
        if (stats2 != stats1) summary.addView(stats2);
        todayText = stat(stats1, "Pesanan hari ini", "–");
        revenueText = stat(stats1, "Omzet hari ini", "–");
        ratingText = stat(stats2, "Rating", "–");
        reviewText = stat(stats2, "Ulasan", "–");

        LinearLayout branches = actionPair();
        addPairItem(branches, dashboardAction("Kelola toko & cabang", () -> open(MerchantBranchesActivity.class)));
        addPairItem(branches, dashboardAction("Pesanan semua cabang", () -> open(MerchantOrdersActivity.class)));
        root.addView(branches);

        TextView section = tv("Kelola usaha", 15, NAVY, true);
        section.setPadding(0, dp(10), 0, dp(5));
        root.addView(section);

        LinearLayout first = actionPair();
        ordersTile = orderTile();
        addPairItem(first, ordersTile);
        addPairItem(first, tile("☰", "Semua Menu", true, () -> open(MerchantMenuListActivity.class)));
        root.addView(first);

        LinearLayout second = actionPair();
        addPairItem(second, tile("+", "Tambah Menu", false, () -> open(MerchantAddMenuActivity.class)));
        addPairItem(second, tile("◷", "Operasional", false, () -> open(MerchantOperationsActivity.class)));
        root.addView(second);

        LinearLayout third = actionPair();
        addPairItem(third, tile("Rp", "Keuangan", false, () -> open(MerchantFinanceActivity.class)));
        addPairItem(third, tile("▥", "Analitik", false, () -> open(MerchantAnalyticsActivity.class)));
        root.addView(third);

        LinearLayout fourth = actionPair();
        addPairItem(fourth, tile("♧", "Pengikut Toko", false, this::openFollowers));
        addPairItem(fourth, tile("↻", "Perbarui ringkasan", false, this::loadAll));
        root.addView(fourth);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout pairRow() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(0, dp(5), 0, 0);
        layout.setBaselineAligned(false);
        return layout;
    }

    private LinearLayout actionPair() {
        LinearLayout layout = pairRow();
        if (screenWidthDp() < 320 || getResources().getConfiguration().fontScale > 1.3f) {
            layout.setOrientation(LinearLayout.VERTICAL);
        }
        return layout;
    }

    private void addPairItem(LinearLayout parent, View view) {
        boolean stacked = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(stacked ? -1 : 0, stacked ? -2 : -1, stacked ? 0 : 1);
        if (parent.getChildCount() > 0) {
            if (stacked) params.topMargin = dp(5);
            else params.leftMargin = dp(6);
        }
        parent.addView(view, params);
    }

    private boolean dark() {
        return MerchantAppSettings.isDarkMode(this);
    }

    private int accent() {
        return dark() ? 0xFF83C4FF : 0xFF155BB4;
    }

    private Drawable tileBackground(boolean highlighted) {
        int surface = highlighted ? (dark() ? 0xFF183650 : 0xFFEAF3FF) : Color.WHITE;
        int border = highlighted ? (dark() ? 0xFF355878 : 0xFFBFDAFC) : 0xFFDCE8F5;
        return new RippleDrawable(ColorStateList.valueOf(dark() ? 0x3383C4FF : 0x24155BB4),
                stroke(surface, border, dp(13)), null);
    }

    private Button dashboardAction(String label, Runnable action) {
        Button button = outlineBtn(label);
        button.setTextColor(accent());
        button.setTextSize(12);
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setMaxLines(Integer.MAX_VALUE);
        button.setElevation(0);
        button.setPadding(dp(9), dp(5), dp(9), dp(5));
        button.setBackground(tileBackground(false));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void openFollowers() {
        Intent intent = new Intent(this, MerchantKabarMitraActivity.class);
        intent.putExtra("show_followers", true);
        startActivity(intent);
    }

    private TextView stat(LinearLayout parent, String label, String value) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), dp(3), dp(8), dp(4));
        box.setBackground(round(dark() ? MerchantAppSettings.DARK_CARD_SOFT : 0xFFF1F6FD, dp(10)));
        box.addView(tv(label, 11, MUTED, false));
        TextView metric = tv(value, 15, NAVY, true);
        box.addView(metric);
        addPairItem(parent, box);
        return metric;
    }

    private LinearLayout tile(String icon, String label, boolean highlighted, Runnable action) {
        LinearLayout tile = baseTile(highlighted);
        addIcon(tile, icon);
        TextView text = tv(label, 13, highlighted ? accent() : TEXT, true);
        tile.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private TextView addIcon(LinearLayout tile, String icon) {
        TextView view = tv(icon, 15, accent(), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(dark() ? 0xFF233F59 : 0xFFEAF3FF, dp(9)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
        params.rightMargin = dp(7);
        tile.addView(view, params);
        return view;
    }

    private LinearLayout orderTile() {
        LinearLayout tile = baseTile(false);
        orderTileIcon = addIcon(tile, "◉");
        LinearLayout labels = column();
        orderTileTitle = tv("Pesanan", 13, TEXT, true);
        orderTileSub = tv("Menunggu data", 11, MUTED, false);
        labels.addView(orderTileTitle);
        labels.addView(orderTileSub);
        tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        orderBadgeText = tv("", 11, Color.WHITE, true);
        orderBadgeText.setGravity(Gravity.CENTER);
        orderBadgeText.setPadding(dp(5), dp(2), dp(5), dp(2));
        orderBadgeText.setBackground(round(0xFFB42318, dp(8)));
        orderBadgeText.setVisibility(View.GONE);
        tile.addView(orderBadgeText);
        tile.setOnClickListener(v -> open(MerchantOrdersActivity.class));
        return tile;
    }

    private LinearLayout baseTile(boolean highlighted) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setMinimumHeight(dp(54));
        tile.setPadding(dp(8), dp(5), dp(8), dp(5));
        tile.setBackground(tileBackground(highlighted));
        tile.setFocusable(true);
        return tile;
    }

    private void setOrderTileActive(int count) {
        boolean active = count > 0;
        ordersTile.setBackground(tileBackground(active));
        orderBadgeText.setVisibility(active ? View.VISIBLE : View.GONE);
        orderBadgeText.setText(count > 99 ? "99+" : String.valueOf(count));
        orderTileIcon.setText(active ? "●" : "◉");
        orderTileTitle.setTextColor(active ? accent() : TEXT);
        orderTileSub.setText(active ? "Perlu respons" : "Belum ada yang baru");
        orderBadgeText.setContentDescription(active ? count + " pesanan baru" : null);
    }

    private void startAuto(){
        stopAuto();
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
        boolean online = MerchantConnectivity.isOnline(this);
        if (!online || !dashboardLoaded) {
            networkText.setText(online ? "Menghubungkan…" : "Tidak ada koneksi");
            networkText.setTextColor(MUTED);
        }
        MerchantNetworkExecutor.executeRead(this, "dashboard", () -> {
            try {
                JSONObject dashboard = new JSONObject(get(BASE + "getMerchantDashboard.php?v=" + System.currentTimeMillis()));
                if (!dashboard.optBoolean("success")) throw new IllegalStateException();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dashboardLoaded = true;
                    networkText.setText("Terhubung");
                    networkText.setTextColor(MUTED);
                    showDash(dashboard);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    boolean connected = MerchantConnectivity.isOnline(this);
                    networkText.setText(connected ? "Ringkasan belum diperbarui" : "Tidak ada koneksi");
                    networkText.setTextColor(MUTED);
                });
            }
        });
    }

    private void showDash(JSONObject d){
        try {
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
            storeStatusBtn.setText(storeUpdating ? "Memproses…" : !verified ? "Menunggu admin" : (open ? "Tutup toko" : "Buka toko"));
            storeStatusBtn.setEnabled(verified && !storeUpdating);
            storeStatusBtn.setTag(open ? "1" : "0");
        } catch(Exception e){ descText.setVisibility(android.view.View.VISIBLE);descText.setText("Ringkasan belum terbaca. Coba perbarui."); }
    }

    private void toggleStore(){
        if(storeUpdating || storeStatusBtn.getTag()==null) return;
        storeUpdating=true;
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
