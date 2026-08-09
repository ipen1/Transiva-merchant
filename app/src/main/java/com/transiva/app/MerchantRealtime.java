package com.transiva.app;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

/** App-internal realtime bridge from Firebase service to visible merchant screens. */
public final class MerchantRealtime {
    public static final String ACTION_REFRESH = "com.transiva.merchant.ACTION_REFRESH";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_ORDER_ID = "order_id";

    private MerchantRealtime() {}

    public static void publish(Context context, String reason, String orderId) {
        if (context == null) return;
        Intent i = new Intent(ACTION_REFRESH);
        i.setPackage(context.getPackageName());
        i.putExtra(EXTRA_REASON, reason == null ? "" : reason);
        i.putExtra(EXTRA_ORDER_ID, orderId == null ? "" : orderId);
        context.sendBroadcast(i);
    }

    public static void register(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) return;
        IntentFilter filter = new IntentFilter(ACTION_REFRESH);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
