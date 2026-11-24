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
    private LinearLayout storeHero;
    private ImageView storeIllustration;
    private TextView storeSubtitle;
    private Boolean storeOpen;
    private boolean storeVerified, storePaused, storeNeedsRefresh;
    private int storeStatusGeneration;
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
    private boolean premium;
    private TextView accessLabel;
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
        accessLabel = tv(isStaff() ? "Staf cabang" : "Pemilik", 11, MUTED, true);
        identity.addView(accessLabel);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cashier = tv("Kasir", 15, 0xFF0955B5, true);
        cashier.setGravity(Gravity.CENTER);
        cashier.setPadding(dp(10), 0, dp(10), 0);
        android.graphics.drawable.Drawable receipt = ui.icon(R.drawable.ic_merchant_receipt, 0xFF0955B5).getDrawable();
        receipt.setBounds(0, 0, dp(19), dp(19));
        cashier.setCompoundDrawablesRelative(receipt, null, null, null);
        cashier.setCompoundDrawablePadding(dp(5));
        cashier.setBackground(ui.touch(stroke(0xFFE4EFFF, 0xFF9BBDF4, dp(12))));
        cashier.setOnClickListener(v -> {
            if (!dashboardLoaded) { toast("Status Premium sedang dimuat."); loadAll(); }
            else if (premium) open(MerchantPosActivity.class);
            else alert("Kasir Premium", "Hubungi Admin Transiva untuk mengaktifkan Premium.");
        });
        cashier.setFocusable(true);
        LinearLayout.LayoutParams cashierParams = new LinearLayout.LayoutParams(-2, dp(48));
        cashierParams.setMarginEnd(dp(6));
        header.addView(cashier, cashierParams);
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
        if (!isStaff()) addBlock(root, branchActions(), 10);

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
        storeHero = column();
        storeHero.setMinimumHeight(dp(84));
        storeHero.setPadding(dp(10), dp(9), dp(10), dp(9));

        boolean stacked = screenWidthDp() < 340 || getResources().getConfiguration().fontScale > 1.2f;
        LinearLayout top = stacked ? column() : row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = row();
        identity.setGravity(Gravity.CENTER_VERTICAL);

        storeIllustration = new ImageView(this);
        storeIllustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        storeIllustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        artParams.setMarginEnd(dp(8));
        identity.addView(storeIllustration, artParams);

        LinearLayout info = column();
        statusText = tv("", 20, NAVY, true);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        storeSubtitle = tv("", 12, NAVY, true);
        clusterText = tv("Memuat area…", 11, MUTED, false);
        clusterText.setCompoundDrawablePadding(dp(4));
        info.addView(statusText);
        info.addView(storeSubtitle);
        info.addView(clusterText);
        identity.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(identity, new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));

        storeStatusBtn = btn("");
        storeStatusBtn.setTextSize(12);
        storeStatusBtn.setOnClickListener(v -> { if (storeNeedsRefresh) loadAll(); else toggleStore(); });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(stacked ? -1 : -2, -2);
        actionParams.setMarginStart(stacked ? 0 : dp(8));
        actionParams.topMargin = stacked ? dp(6) : 0;
        top.addView(storeStatusBtn, actionParams);
        storeHero.addView(top, new LinearLayout.LayoutParams(-1, -2));

        networkText = tv("Menghubungkan…", 11, MUTED, false);
        descText = tv("", 11, MUTED, false);
        descText.setVisibility(View.GONE);
        storeHero.addView(networkText);
        storeHero.addView(descText);
        renderStoreStatus();
        return storeHero;
    }

    private void renderStoreStatus() {
        boolean open = storeVerified && Boolean.TRUE.equals(storeOpen);
        boolean dark = ui.dark();
        int primary = open || dark ? Color.WHITE : 0xFF233144;
        int secondary = open ? 0xFFEAF4FF : dark ? 0xFFD7E2EE : 0xFF3B485C;
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                open ? new int[]{0xFF07316B, 0xFF084AA8, 0xFF0763DC}
                        : dark ? new int[]{0xFF243141, 0xFF303E50}
                        : new int[]{0xFFECF0F5, 0xFFDDE4ED});
        background.setCornerRadius(dp(13));
        if (!open) background.setStroke(dp(1), dark ? 0xFF637388 : 0xFF9BA9BA);
        storeHero.setBackground(background);
        storeIllustration.setImageResource(open ? R.drawable.ic_merchant_store_open : R.drawable.ic_merchant_store_closed);
        statusText.setTextColor(primary);
        storeSubtitle.setTextColor(primary);
        clusterText.setTextColor(secondary);
        clusterText.setCompoundDrawablesRelative(ui.iconDrawable(R.drawable.ic_merchant_pin, secondary, 14), null, null, null);
        networkText.setTextColor(secondary);
        descText.setTextColor(secondary);

        statusText.setText(storeOpen == null ? "Memuat status toko…" :
                !storeVerified ? "Menunggu verifikasi" : open ? "Toko buka" : "Toko tutup");
        storeSubtitle.setText(storeNeedsRefresh ? "Status sedang diperiksa" :
                open && !storePaused ? "Menerima pesanan baru" : "Pesanan baru dijeda");
        storeSubtitle.setVisibility(storeOpen != null && storeVerified ? View.VISIBLE : View.GONE);

        boolean canToggle = storeOpen != null && storeVerified && !storeUpdating && !storeNeedsRefresh;
        storeStatusBtn.setText(storeUpdating ? "Memproses…" : storeNeedsRefresh ? "Perbarui status" :
                storeOpen == null ? "Memuat…" : !storeVerified ? "Menunggu admin" : open ? "Tutup toko" : "Buka toko");
        int actionColor = storeOpen == null || !storeVerified || storeNeedsRefresh ? 0xFF536174 :
                open ? 0xFFC51F34 : 0xFF08754C;
        GradientDrawable actionBackground = round(actionColor, dp(9));
        if (open && !storeNeedsRefresh) actionBackground.setStroke(dp(1), 0xFFFBE4E8);
        storeStatusBtn.setBackground(ui.touch(actionBackground));
        storeStatusBtn.setAlpha(storeUpdating ? .75f : 1f);
        storeStatusBtn.setEnabled(canToggle || storeNeedsRefresh && !storeUpdating);
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
                JSONObject result=new JSONObject(get(BASE+"getMerchantOrders.php?scope=incoming"+(isStaff()?"":"&outlets=all")));
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
            networkText.setVisibility(View.VISIBLE);
            networkText.setText(online ? "Menghubungkan…" : "Tidak ada koneksi");
        }
        final int generation = storeStatusGeneration;
        MerchantNetworkExecutor.executeRead(this, "dashboard:" + generation, () -> {
            try {
                JSONObject dashboard = new JSONObject(get(BASE + "getMerchantDashboard.php?v=" + System.currentTimeMillis()));
                if (!dashboard.optBoolean("success")) throw new IllegalStateException();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != storeStatusGeneration || storeUpdating) return;
                    dashboardLoaded = true;
                    networkText.setVisibility(View.GONE);
                    showDash(dashboard);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != storeStatusGeneration || storeUpdating) return;
                    networkText.setVisibility(View.VISIBLE);
                    boolean connected = MerchantConnectivity.isOnline(this);
                    networkText.setText(connected ? "Ringkasan belum diperbarui" : "Tidak ada koneksi");
                });
            }
        });
    }

    private void showDash(JSONObject d){
        try {
            premium = d.optBoolean("premium");
            String until = d.isNull("premium_until") ? "" : d.optString("premium_until");
            accessLabel.setText(isStaff() ? "Staf cabang" : premium ? "Pemilik · Premium sampai " + until : "Pemilik · Free");
            storeVerified = d.optInt("is_verified", 0) == 1;
            storeOpen = storeVerified && d.optInt("is_open", 0) == 1;
            storePaused = d.optBoolean("is_paused");
            storeNeedsRefresh = false;
            renderStoreStatus();
            descText.setText(!storeVerified ? "Toko dapat dibuka setelah diverifikasi admin." : "");
            descText.setVisibility(storeVerified ? View.GONE : View.VISIBLE);
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
                descText.setText((!storeVerified ? "Menunggu verifikasi • " : "")+activeOrders+" pesanan aktif");
                descText.setVisibility(android.view.View.VISIBLE);
            }
        } catch(Exception e){ descText.setVisibility(android.view.View.VISIBLE);descText.setText("Ringkasan belum terbaca. Coba perbarui."); }
    }

    private void toggleStore() {
        if (storeUpdating || storeNeedsRefresh || !storeVerified || storeOpen == null) return;
        final int next = storeOpen ? 0 : 1;
        storeUpdating = true;
        storeStatusGeneration++;
        renderStoreStatus();

        boolean accepted = MerchantNetworkExecutor.executeWrite("restaurant-open-toggle", () -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("is_open", next);
                JSONObject response = new JSONObject(postJson(BASE + "updateRestaurantStatus.php", payload));
                runOnUiThread(() -> finishStoreUpdate(response, next));
            } catch (Exception error) {
                runOnUiThread(() -> finishStoreUpdate(null, next));
            }
        });
        if (!accepted) {
            storeUpdating = false;
            renderStoreStatus();
            toast("Perubahan status belum dikirim. Coba lagi.");
            loadAll();
        }
    }

    private void finishStoreUpdate(JSONObject response, int requestedStatus) {
        if (isFinishing() || isDestroyed()) return;
        // Only a response sent after this write can refresh the confirmed status.
        storeStatusGeneration++;
        storeUpdating = false;
        if (response == null) {
            storeNeedsRefresh = true;
            networkText.setText("Status belum terkonfirmasi. Memeriksa ulang…");
            networkText.setVisibility(View.VISIBLE);
        } else if (response.optBoolean("success")) {
            storeOpen = response.optInt("is_open", requestedStatus) == 1;
            networkText.setVisibility(View.GONE);
            toast(response.optString("message", "Status toko diperbarui."));
        } else {
            if (response.has("is_verified")) storeVerified = response.optInt("is_verified") == 1;
            if (response.has("is_open")) storeOpen = response.optInt("is_open") == 1;
            toast(response.optString("message", "Status toko belum berubah."));
        }
        renderStoreStatus();
        loadAll();
    }
}
