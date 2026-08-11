package com.transiva.app;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.SystemClock;
import java.util.concurrent.ConcurrentHashMap;

/** App-internal realtime bridge from Firebase service to visible merchant screens. */
public final class MerchantRealtime {
    public static final String ACTION_REFRESH = "com.transiva.merchant.ACTION_REFRESH";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_ORDER_ID = "order_id";
    private static final long DUPLICATE_WINDOW_MS = 1200L;
    private static final ConcurrentHashMap<String, Long> LAST_PUBLISH = new ConcurrentHashMap<>();

    private MerchantRealtime() {}

    public static void publish(Context context, String reason, String orderId) {
        if (context == null) return;
        String safeReason = reason == null ? "" : reason.trim();
        String safeOrderId = orderId == null ? "" : orderId.trim();

        // P3: coalesce duplicate FCM/internal events arriving almost simultaneously.
        // Different orders/reasons are never merged.
        String key = safeReason + "|" + safeOrderId;
        long now = SystemClock.elapsedRealtime();
        Long previous = LAST_PUBLISH.put(key, now);
        if (previous != null && now - previous < DUPLICATE_WINDOW_MS) return;

        // Keep the tiny map bounded over long-running merchant sessions.
        if (LAST_PUBLISH.size() > 64) {
            long cutoff = now - 60_000L;
            for (java.util.Map.Entry<String, Long> e : LAST_PUBLISH.entrySet()) {
                Long at = e.getValue();
                if (at == null || at < cutoff) LAST_PUBLISH.remove(e.getKey(), at);
            }
        }

        Intent i = new Intent(ACTION_REFRESH);
        i.setPackage(context.getPackageName());
        i.putExtra(EXTRA_REASON, safeReason);
        i.putExtra(EXTRA_ORDER_ID, safeOrderId);
        context.sendBroadcast(i);
    }

    public static void register(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) return;
        IntentFilter filter = new IntentFilter(ACTION_REFRESH);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
