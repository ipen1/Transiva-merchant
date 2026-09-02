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
        final String global = lower(order.optString("global_status", order.optString("status", "pending")));
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
            TextView countdown = text(a, "Sedang diproses...", 16, Color.parseColor("#B54708"), true);
            countdown.setGravity(Gravity.CENTER);
            countdown.setPadding(0, dp(a, 8), 0, dp(a, 8));
            card.addView(countdown);
            bindCountdown(countdown, order.optString("estimated_ready_at", ""), order.optInt("cook_minutes", 0));
        }

        card.addView(step(a, "✓", "Pesanan diterima merchant", !"pending".equals(global)));
        card.addView(step(a, "🍳", "Makanan sedang diproses", isKitchenStarted(global, merchant)));
        card.addView(step(a, "🛵", "Driver mengambil pesanan", rank(global) >= rank("driver_accepted")));
        card.addView(step(a, "📍", "Driver tiba di restoran", rank(global) >= rank("arrived_pickup")));
        card.addView(step(a, "🛣", "Makanan dalam perjalanan", rank(global) >= rank("on_delivery")));
        card.addView(step(a, "🏠", "Driver tiba di customer", rank(global) >= rank("arrived_delivery")));
        card.addView(step(a, "✓", "Pesanan selesai", rank(global) >= rank("finished")));
        parent.addView(card);
    }

    static boolean countdownFinished(JSONObject order) {
        String readyAt = order.optString("estimated_ready_at", "");
        if (readyAt.trim().isEmpty()) return true;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(readyAt);
            return d == null || System.currentTimeMillis() >= d.getTime();
        } catch (Exception e) { return true; }
    }

    private static void bindCountdown(TextView t, String readyAt, int fallbackMinutes) {
        final long target;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(readyAt);
            target = d == null ? System.currentTimeMillis() + Math.max(1, fallbackMinutes) * 60000L : d.getTime();
        } catch (Exception e) {
            target = System.currentTimeMillis() + Math.max(1, fallbackMinutes) * 60000L;
        }
        Handler h = new Handler(Looper.getMainLooper());
        Runnable r = new Runnable() {
            @Override public void run() {
                if (!t.isAttachedToWindow()) return;
                long left = Math.max(0L, target - System.currentTimeMillis());
                long min = left / 60000L;
                long sec = (left / 1000L) % 60L;
                if (left > 0) {
                    t.setText(String.format(Locale.US, "🍳 Sedang diproses • %02d:%02d", min, sec));
                    h.postDelayed(this, 1000L);
                } else {
                    t.setText("✓ Waktu persiapan selesai • cek pesanan lalu tandai siap");
                    t.setTextColor(Color.parseColor("#137333"));
                }
            }
        };
        t.post(r);
    }

    private static boolean isKitchenActive(String global, String merchant) {
        return !isFinal(global) && ("merchant_accepted".equals(global) || "driver_accepted".equals(global)
                || "arrived_pickup".equals(global) || "preparing".equals(merchant) || "merchant_preparing".equals(merchant));
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
    private static TextView text(MerchantBaseActivity a, String s, int sp, int c, boolean b) { TextView t=new TextView(a); t.setText(s); t.setTextSize(sp); t.setTextColor(c); if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private static int dp(MerchantBaseActivity a,int v){ return Math.round(v*a.getResources().getDisplayMetrics().density); }
    private static String lower(String s){ return s==null?"":s.trim().toLowerCase(Locale.US); }
}
