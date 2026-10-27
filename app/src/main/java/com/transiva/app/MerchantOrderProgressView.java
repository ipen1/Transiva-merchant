package com.transiva.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Visual status order merchant + countdown dapur + perjalanan driver. */
final class MerchantOrderProgressView {
    private MerchantOrderProgressView() {}

    static void attach(MerchantBaseActivity a, LinearLayout parent, JSONObject order) {
        final String global = resolveGlobalStatus(order);
        final String merchant = lower(order.optString("merchant_status", ""));
        if ("pending".equals(global) && merchant.isEmpty()) return;

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 13), dp(a, 12), dp(a, 13), dp(a, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(a, 8), 0, dp(a, 8));
        card.setLayoutParams(lp);
        card.setBackground(a.round(Color.parseColor("#F5F9FF"), dp(a, 14)));
        card.addView(text(a, "Status Pesanan", 14, Color.parseColor("#102A43"), true));

        if (isKitchenActive(global, merchant)) {
            MerchantCountdownView countdown=new MerchantCountdownView(a,MerchantServerClock.readyAt(order),Math.max(1,order.optInt("cook_minutes",0))*60000L,"Sisa persiapan","Waktu habis • periksa makanan");
            card.addView(countdown);
            if(a instanceof MerchantOrdersActivity) countdown.onComplete(()->((MerchantOrdersActivity)a).onKitchenCountdownFinished());
        }

        LinearLayout details=new LinearLayout(a);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(android.view.View.GONE);
        TextView expand=text(a,"Lihat tahapan pesanan ▾",12,a.BLUE,true);expand.setMinHeight(dp(a,48));expand.setGravity(Gravity.CENTER_VERTICAL);
        expand.setOnClickListener(v->{boolean show=details.getVisibility()!=android.view.View.VISIBLE;details.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);expand.setText(show?"Sembunyikan tahapan ▴":"Lihat tahapan pesanan ▾");});
        card.addView(expand);
        details.addView(step(a, "✓", "Pesanan diterima merchant", !"pending".equals(global)));
        details.addView(step(a, "🍳", "Makanan sedang diproses", isKitchenStarted(global, merchant)));
        details.addView(step(a, "🛵", "Driver mengambil pesanan", rank(global) >= rank("driver_accepted")));
        details.addView(step(a, "📍", "Driver tiba di restoran", rank(global) >= rank("arrived_pickup")));
        details.addView(step(a, "🛣", "Makanan dalam perjalanan", rank(global) >= rank("on_delivery")));
        details.addView(step(a, "🏠", "Driver tiba di customer", rank(global) >= rank("arrived_delivery")));
        details.addView(step(a, "✓", "Pesanan selesai", rank(global) >= rank("finished")));
        card.addView(details);
        parent.addView(card);
    }

    static boolean countdownFinished(JSONObject order) {
        long target=MerchantServerClock.readyAt(order);
        return target<=0||MerchantServerClock.now()>=target;
    }

    private static boolean isKitchenActive(String global, String merchant) {
        if("ready".equals(merchant)||"merchant_ready".equals(merchant))return false;
        // Countdown hanya milik fase dapur. Begitu driver membawa makanan (on_delivery)
        // atau status setelahnya, timer harus hilang walau merchant_status masih "preparing".
        if (rank(global) >= rank("on_delivery") || isFinal(global)) return false;
        return "merchant_accepted".equals(global) || "driver_accepted".equals(global)
                || "arrived_pickup".equals(global) || "preparing".equals(merchant)
                || "merchant_preparing".equals(merchant) || "ready".equals(merchant)
                || "merchant_ready".equals(merchant);
    }

    private static String resolveGlobalStatus(JSONObject order) {
        // Beberapa endpoint lama mempertahankan status merchant pada field `status`
        // sementara perjalanan driver ada di driver_status/order_status. Pilih status
        // dengan rank perjalanan tertinggi agar UI selalu mengikuti driver secara realtime.
        String[] keys = {"global_status", "driver_status", "order_status", "trip_status", "status"};
        String best = "pending";
        int bestRank = 0;
        for (String key : keys) {
            String value = lower(order.optString(key, ""));
            if (value.isEmpty()) continue;
            int valueRank = rank(value);
            if (valueRank > bestRank || (bestRank == 0 && !"pending".equals(value))) {
                best = value;
                bestRank = valueRank;
            }
        }
        return best;
    }
    private static boolean isKitchenStarted(String global, String merchant) {
        return !"pending".equals(global) || "preparing".equals(merchant) || "merchant_preparing".equals(merchant) || "ready".equals(merchant);
    }
    private static boolean isFinal(String s) { return "finished".equals(s) || "completed".equals(s) || "canceled".equals(s) || "cancelled".equals(s) || "merchant_rejected".equals(s); }
    private static int rank(String s) {
        switch (lower(s)) {
            case "merchant_accepted": return 1;
            case "driver_accepted": case "taken": return 2;
            case "arrived_pickup": return 3;
            case "on_delivery": return 4;
            case "arrived_delivery": return 5;
            case "finished": case "completed": return 6;
            default: return 0;
        }
    }
    private static LinearLayout step(MerchantBaseActivity a, String icon, String label, boolean active) {
        LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(a,3),0,dp(a,3));
        row.addView(text(a, icon, 13, active ? Color.parseColor("#137333") : Color.parseColor("#98A2B3"), true));
        TextView l = text(a, "  " + label, 12, active ? Color.parseColor("#344054") : Color.parseColor("#98A2B3"), active); row.addView(l);
        return row;
    }
    private static TextView text(MerchantBaseActivity a, String s, int sp, int c, boolean b) { return a.tv(s,sp,c,b); }
    private static int dp(MerchantBaseActivity a,int v){ return Math.round(v*a.getResources().getDisplayMetrics().density); }
    private static String lower(String s){ return s==null?"":s.trim().toLowerCase(Locale.US); }
}
