package com.transiva.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class MerchantDashboardActivity extends MerchantBaseActivity {
    private LinearLayout ordersTile;
    private MerchantPremiumUi ui;
    private View storeDot;
    private TextView statusText, descText, networkText, clusterText, todayText, ratingText, reviewText, revenueText, orderBadgeText, orderTileTitle, orderTileSub;
    private Button storeStatusBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean dashboardLoaded;
    private boolean storeUpdating;
    private Runnable autoTask;
    private boolean realtimeRegistered = false;
    private LinearLayout incoming;
    private final java.util.Set<String> expiredIncoming=new java.util.HashSet<>();
    private boolean incomingChecked;
    private JSONArray pendingOrders=new JSONArray();
    private boolean incomingVisible, incomingReading, incomingQueued, incomingUpdating, incomingStale=true, incomingMore;
    private final Runnable incomingPoll=new Runnable() {
        @Override public void run() {
            loadIncoming();
            if (incomingVisible) handler.postDelayed(this,WaveLoadGuard.jitter(15000L));
        }
    };
    private final BroadcastReceiver realtimeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadAll();
            loadIncoming();
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
        incomingVisible=true;
        incomingPoll.run();
    }

    @Override protected void onPause(){
        super.onPause();
        stopAuto();
        incomingVisible=false;
        handler.removeCallbacks(incomingPoll);
        incomingStale=true;
        renderIncoming();
        if(realtimeRegistered){
            try { unregisterReceiver(realtimeReceiver); } catch(Exception ignored){}
            realtimeRegistered = false;
        }
    }

    private void build() {
        ui = new MerchantPremiumUi(this);
        LinearLayout root = column();
        setContentView(page(root));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = column();
        TextView brand = tv("TRANSIVA MERCHANT", 11, ui.accent(), true);
        brand.setLetterSpacing(.055f);
        identity.addView(brand);
        String merchant = username().isEmpty() ? "Merchant" : username();
        TextView name = tv("Halo, " + merchant, 26, NAVY, true);
        name.setLetterSpacing(-.025f);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(name);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton more = ui.iconButton(R.drawable.ic_merchant_more, ui.accent(), "Lainnya");
        more.setBackground(ui.touch(stroke(Color.WHITE, ui.accent(), dp(12))));
        more.setOnClickListener(v -> open(MerchantMoreActivity.class));
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        incoming=column();
        incoming.setVisibility(View.GONE);
        addBlock(root,incoming,8);
        addBlock(root, storeCard(), 10);
        addBlock(root, statistics(), 10);
        addBlock(root, branchActions(), 10);

        TextView section = tv("Kelola usaha", 20, NAVY, true);
        section.setLetterSpacing(-.02f);
        addBlock(root, section, 14);

        LinearLayout first = actions();
        ordersTile = orderTile();
        addPair(first, ordersTile);
        addPair(first, tile(R.drawable.ic_merchant_menu, "Semua Menu", "Atur ketersediaan", true,
                () -> open(MerchantMenuListActivity.class)));
        addBlock(root, first, 8);

        LinearLayout second = actions();
        addPair(second, tile(R.drawable.ic_merchant_plus, "Tambah Menu", "", false,
                () -> open(MerchantAddMenuActivity.class)));
        addPair(second, tile(R.drawable.ic_merchant_activity, "Operasional", "", false,
                () -> open(MerchantOperationsActivity.class)));
        addBlock(root, second, 8);

        LinearLayout third = actions();
        addPair(third, tile(R.drawable.ic_merchant_wallet, "Keuangan", "", false,
                () -> open(MerchantFinanceActivity.class)));
        addPair(third, tile(R.drawable.ic_merchant_analytics, "Analitik", "", false,
                () -> open(MerchantAnalyticsActivity.class)));
        addBlock(root, third, 8);

        LinearLayout followers = tile(R.drawable.ic_merchant_followers, "Pengikut Toko", "", false, this::openFollowers);
        followers.addView(ui.icon(R.drawable.ic_merchant_chevron, MUTED),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        addBlock(root, followers, 8);
    }

    private LinearLayout storeCard() {
        LinearLayout hero = column();
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF052C68, 0xFF084596, 0xFF0759C6});
        gradient.setCornerRadius(dp(16));
        hero.setBackground(gradient);
        hero.setPadding(dp(14), dp(10), dp(12), dp(11));

        LinearLayout top = actions();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = column();
        LinearLayout heading = row();
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setPadding(0, 0, 0, 0);
        storeDot = new View(this);
        storeDot.setBackground(round(0xFFC5D7EC, dp(8)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.rightMargin = dp(8);
        heading.addView(storeDot, dotParams);
        statusText = tv("Memuat toko…", 19, Color.WHITE, true);
        heading.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(heading);
        networkText = tv("Menghubungkan…", 12, 0xFFE4EFFF, false);
        info.addView(networkText);
        boolean stacked = top.getOrientation() == LinearLayout.VERTICAL;
        top.addView(info, new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
        storeStatusBtn = outlineBtn("Memuat…");
        GradientDrawable outline = round(0x18FFFFFF, dp(10));
        outline.setStroke(dp(1), 0xFFE4EFFF);
        storeStatusBtn.setBackground(ui.touch(outline));
        storeStatusBtn.setElevation(0);
        storeStatusBtn.setTextColor(Color.WHITE);
        storeStatusBtn.setTextSize(13);
        storeStatusBtn.setOnClickListener(v -> toggleStore());
        storeStatusBtn.setEnabled(false);
        LinearLayout.LayoutParams storeParams = new LinearLayout.LayoutParams(stacked ? -1 : -2, -2);
        storeParams.leftMargin = stacked ? 0 : dp(8);
        storeParams.topMargin = stacked ? dp(5) : 0;
        top.addView(storeStatusBtn, storeParams);
        hero.addView(top);

        LinearLayout location = row();
        location.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        location.setPadding(0, dp(3), 0, 0);
        addIcon(location, R.drawable.ic_merchant_pin, 0xFFE4EFFF, 15);
        clusterText = tv("Memuat area…", 12, 0xFFE4EFFF, false);
        location.addView(clusterText, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(location);
        descText = tv("", 12, 0xFFE4EFFF, false);
        descText.setVisibility(View.GONE);
        hero.addView(descText);
        return hero;
    }

    private View statistics() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(ui.surface(14));
        LinearLayout metrics = column();
        metrics.setPadding(dp(11), dp(34), dp(11), dp(7));
        LinearLayout first = metricRow();
        todayText = stat(first, "Pesanan hari ini");
        revenueText = stat(first, "Omzet hari ini");
        metrics.addView(first);
        metrics.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout second = metricRow();
        ratingText = stat(second, "Rating");
        reviewText = stat(second, "Ulasan");
        metrics.addView(second);
        panel.addView(metrics, new FrameLayout.LayoutParams(-1, -2));

        ImageButton refresh = ui.iconButton(R.drawable.ic_merchant_refresh, ui.accent(), "Perbarui ringkasan");
        refresh.setOnClickListener(v -> loadAll());
        panel.addView(refresh, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END));
        return panel;
    }

    private LinearLayout metricRow() {
        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        setMiddleDivider(row);
        return row;
    }

    private TextView stat(LinearLayout row, String label) {
        LinearLayout metric = column();
        metric.setPadding(dp(7), dp(5), dp(7), dp(6));
        metric.addView(tv(label, 12, MUTED, true));
        TextView value = tv("–", 22, NAVY, true);
        value.setLetterSpacing(-.02f);
        metric.addView(value);
        row.addView(metric, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private LinearLayout branchActions() {
        LinearLayout branches = actions();
        branches.setBackground(ui.surface(14));
        branches.setGravity(Gravity.CENTER_VERTICAL);
        boolean stacked = branches.getOrientation() == LinearLayout.VERTICAL;
        setMiddleDivider(branches);
        branches.addView(branchLink(R.drawable.ic_merchant_store, "Kelola toko & cabang",
                () -> open(MerchantBranchesActivity.class)),
                new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
        branches.addView(branchLink(R.drawable.ic_merchant_package, "Pesanan semua cabang",
                () -> open(MerchantOrdersActivity.class)),
                new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
        return branches;
    }

    private TextView branchLink(int icon, String label, Runnable action) {
        TextView link = tv(label, 12, ui.accent(), true);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(dp(9), dp(6), dp(9), dp(6));
        link.setMinimumHeight(dp(48));
        link.setCompoundDrawablePadding(dp(7));
        link.setCompoundDrawablesRelative(ui.iconDrawable(icon, ui.accent(), 19), null, null, null);
        link.setBackground(ui.touch(null));
        link.setFocusable(true);
        link.setOnClickListener(v -> action.run());
        return link;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout actions() {
        LinearLayout layout = new LinearLayout(this);
        layout.setBaselineAligned(false);
        if (screenWidthDp() < 320 || getResources().getConfiguration().fontScale > 1.3f) {
            layout.setOrientation(LinearLayout.VERTICAL);
        }
        return layout;
    }

    private void addBlock(LinearLayout parent, View view, int space) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(space);
        parent.addView(view, params);
    }

    private void addPair(LinearLayout parent, View view) {
        boolean stacked = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(stacked ? -1 : 0, stacked ? -2 : -1, stacked ? 0 : 1);
        if (parent.getChildCount() == 1) {
            if (stacked) params.topMargin = dp(8);
            else params.leftMargin = dp(8);
        }
        parent.addView(view, params);
    }

    private void setMiddleDivider(LinearLayout row) {
        GradientDrawable line = new GradientDrawable();
        line.setColor(ui.divider());
        line.setSize(dp(1), dp(1));
        row.setDividerDrawable(line);
        row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(ui.divider());
        return line;
    }

    private void openFollowers() {
        Intent intent = new Intent(this, MerchantKabarMitraActivity.class);
        intent.putExtra("show_followers", true);
        startActivity(intent);
    }

    private LinearLayout tile(int icon, String label, String detail, boolean primary, Runnable action) {
        LinearLayout tile = baseTile(primary);
        addIcon(tile, icon, primary ? Color.WHITE : ui.accent(), 24);
        LinearLayout labels = column();
        labels.addView(tv(label, 15, primary ? Color.WHITE : TEXT, true));
        if (!detail.isEmpty()) labels.addView(tv(detail, 12, 0xFFE7F1FF, false));
        tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private void addIcon(LinearLayout row, int icon, int color, int size) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
        params.rightMargin = dp(size < 20 ? 6 : 9);
        row.addView(ui.icon(icon, color), params);
    }

    private LinearLayout orderTile() {
        LinearLayout tile = baseTile(false);
        addIcon(tile, R.drawable.ic_merchant_bag, ui.accent(), 24);
        LinearLayout labels = column();
        orderTileTitle = tv("Pesanan", 15, TEXT, true);
        orderTileSub = tv("Menunggu data", 12, MUTED, false);
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

    private LinearLayout baseTile(boolean primary) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setMinimumHeight(dp(primary ? 58 : 49));
        tile.setPadding(dp(11), dp(7), dp(10), dp(7));
        tile.setBackground(ui.touch(primary ? round(MerchantPremiumUi.BLUE, dp(14)) : ui.surface(14)));
        tile.setFocusable(true);
        return tile;
    }

    private void setOrderTileActive(int count) {
        boolean active = count > 0;
        ordersTile.setBackground(ui.touch(active
                ? stroke(ui.dark() ? 0xFF17344F : 0xFFE9F3FF, ui.accent(), dp(14))
                : ui.surface(14)));
        orderBadgeText.setVisibility(active ? View.VISIBLE : View.GONE);
        orderBadgeText.setText(count > 99 ? "99+" : String.valueOf(count));
        orderTileTitle.setTextColor(active ? ui.accent() : TEXT);
        orderTileSub.setText(active ? "Perlu respons" : "Belum ada yang baru");
        orderBadgeText.setContentDescription(active ? count + " pesanan baru" : null);
    }

    private void loadIncoming() {
        String token=sessionManager.getToken();
        if (!incomingVisible || token==null || token.isEmpty()) return;
        if (incomingReading || incomingUpdating) {
            incomingQueued=true;
            return;
        }
        incomingReading=true;
        java.util.concurrent.Future<?> request=MerchantNetworkExecutor.executeRead(this,"incoming-orders",()->{
            long sent=android.os.SystemClock.elapsedRealtime();
            try {
                JSONObject result=new JSONObject(get(BASE+"getMerchantOrders.php?outlets=all&scope=incoming"));
                long received=android.os.SystemClock.elapsedRealtime();
                if (!result.optBoolean("success")) throw new IllegalStateException();
                runOnUiThread(()->{
                    if (!ownsIncomingResult(token)) { incomingReading=false; return; }
                    incomingChecked=true;
                    MerchantServerClock.sync(result,sent,received);
                    pendingOrders=new JSONArray();
                    JSONArray orders=result.optJSONArray("orders");
                    if (orders!=null) for (int i=0;i<orders.length() && pendingOrders.length()<3;i++) {
                        JSONObject order=orders.optJSONObject(i);
                        if (order!=null && "pending".equals(order.optString("status"))) pendingOrders.put(order);
                    }
                    incomingStale=result.optBoolean("stale");
                    incomingMore=result.optBoolean("has_more");
                    completeIncomingRead();
                });
            } catch (Exception error) {
                runOnUiThread(()->{
                    if (!ownsIncomingResult(token)) { incomingReading=false; return; }
                    incomingChecked=true;
                    incomingStale=true;
                    completeIncomingRead();
                });
            }
        });
        if (request==null) {
            incomingChecked=true;
            incomingStale=true;
            completeIncomingRead();
        }
    }

    private boolean ownsIncomingResult(String token) {
        return !isFinishing() && !isDestroyed() && token.equals(sessionManager.getToken());
    }

    private void completeIncomingRead() {
        incomingReading=false;
        if (!incomingVisible) { incomingStale=true; return; }
        renderIncoming();
        if (incomingQueued) {
            incomingQueued=false;
            handler.post(this::loadIncoming);
        }
    }

    private void renderIncoming() {
        if (incoming==null) return;
        incoming.removeAllViews();
        incoming.setVisibility(pendingOrders.length()>0 || (incomingChecked && incomingStale)?View.VISIBLE:View.GONE);
        if (incomingChecked && incomingStale) {
            TextView offline=tv("Pesanan belum bisa diperbarui",15,NAVY,true);
            incoming.addView(offline);
            Button retry=outlineBtn("Coba lagi");
            retry.setEnabled(!incomingReading);
            retry.setOnClickListener(v->loadIncoming());
            incoming.addView(retry);
        }
        for (int i=0;i<pendingOrders.length();i++) {
            JSONObject order=pendingOrders.optJSONObject(i);
            if (order==null) continue;
            boolean canRespond=incomingVisible && !incomingUpdating && !incomingStale
                    && MerchantConnectivity.isOnline(this);
            addBlock(incoming,MerchantIncomingOrderCard.build(this,order,canRespond,
                    (status,reason)->respondToIncoming(order,status,reason),
                    ()->{
                        if (expiredIncoming.add(order.optString("order_id",order.optString("id")))) loadIncoming();
                    }),i==0?0:8);
        }
        if (incomingMore && pendingOrders.length()>0) {
            Button all=outlineBtn("Lihat semua pesanan");
            all.setOnClickListener(v->open(MerchantOrdersActivity.class));
            addBlock(incoming,all,6);
        }
    }

    private void respondToIncoming(JSONObject order,String status,String reason) {
        if (!incomingVisible || incomingStale || incomingUpdating || !MerchantConnectivity.isOnline(this)) return;
        if (MerchantIncomingOrderCard.expired(order)) { loadIncoming(); return; }
        String id=order.optString("id");
        String display=order.optString("order_id",id);
        String token=sessionManager.getToken();
        if (id.isEmpty() || token==null || token.isEmpty()) return;
        incomingUpdating=true;
        renderIncoming();
        MerchantOrderRepository.updateStatus(this,id,display,order.optInt("restaurant_id"),status,reason,
                0,false,(success,message,networkError,receipt)->{
                    if (!ownsIncomingResult(token)) return;
                    incomingUpdating=false;
                    incomingStale=true;
                    if (success) {
                        for (int i=0;i<pendingOrders.length();i++) {
                            JSONObject row=pendingOrders.optJSONObject(i);
                            if (row!=null && id.equals(row.optString("id"))) { pendingOrders.remove(i); break; }
                        }
                        MerchantRealtime.publish(this,"update",display);
                        if (incomingVisible) toast(message);
                    } else if (incomingVisible) alert(networkError?"Memeriksa status":"Pesanan",message);
                    renderIncoming();
                    loadIncoming();
                });
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
        }
        MerchantNetworkExecutor.executeRead(this, "dashboard", () -> {
            try {
                JSONObject dashboard = new JSONObject(get(BASE + "getMerchantDashboard.php?v=" + System.currentTimeMillis()));
                if (!dashboard.optBoolean("success")) throw new IllegalStateException();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dashboardLoaded = true;
                    networkText.setText("Terhubung");
                    showDash(dashboard);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    boolean connected = MerchantConnectivity.isOnline(this);
                    networkText.setText(connected ? "Ringkasan belum diperbarui" : "Tidak ada koneksi");
                });
            }
        });
    }

    private void showDash(JSONObject d){
        try {
            boolean verified = d.optInt("is_verified", 0) == 1;
            boolean open = verified && d.optInt("is_open", 0) == 1;
            statusText.setText(!verified ? "Menunggu verifikasi" : open ? "Toko buka" : "Toko tutup");
            storeDot.setBackground(round(open ? 0xFF3DEA9E : 0xFFFFC477, dp(8)));
            descText.setText(!verified ? "Toko dapat dibuka setelah diverifikasi admin." : "");
            descText.setVisibility(!verified ? android.view.View.VISIBLE : android.view.View.GONE);
            todayText.setText(String.valueOf(d.optInt("today_orders", 0)));
            SpannableString rating = new SpannableString(String.format(new java.util.Locale("id", "ID"),
                    "%.1f ★", d.optDouble("rating", 0)));
            rating.setSpan(new ForegroundColorSpan(ui.dark() ? 0xFFFFD369 : 0xFFAE7100),
                    rating.length() - 1, rating.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ratingText.setText(rating);
            reviewText.setText(String.valueOf(d.optInt("review_count", 0)));
            if(revenueText != null) revenueText.setText(rupiah(d.optLong("today_revenue", 0)));
            JSONObject cluster = d.optJSONObject("cluster");
            if(clusterText != null){
                TransivaCluster.Item area = TransivaCluster.fromServer(cluster);
                if (area == null || area.id <= 0) clusterText.setText("Wilayah merchant belum terdeteksi");
                else {
                    String region = area.region == null || area.region.name.isEmpty()
                            ? "Regional belum terdeteksi" : "Regional " + area.region.name;
                    String clusterName = area.name.isEmpty() ? "belum terdeteksi" : area.name;
                    clusterText.setText(region + " · Cluster " + clusterName);
                }
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
