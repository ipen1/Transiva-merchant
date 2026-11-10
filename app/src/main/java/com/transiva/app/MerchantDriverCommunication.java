package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Single stable entry point for Merchant <-> Driver communication. */
public final class MerchantDriverCommunication {
    private MerchantDriverCommunication() {}

    public static void attach(MerchantOrdersActivity a, LinearLayout box, JSONObject order,
                              String actionId, String displayId, String driverName) {
        TextView caption = a.tv("Komunikasi Driver", 12, a.MUTED, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, a.dp(10), 0, a.dp(6));
        box.addView(caption, cp);

        LinearLayout row = a.row();
        Button chat = a.outlineBtn("💬 Pesan");
        Button call = a.outlineBtn("☎ Panggil");
        row.addView(chat, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        row.addView(call, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        box.addView(row);

        chat.setOnClickListener(v -> {
            Intent i = new Intent(a, MerchantDriverChatActivity.class);
            i.putExtra("order_id", displayId);
            i.putExtra("order_db_id", actionId);
            i.putExtra("driver_name", driverName);
            if (order.optInt("restaurant_id") > 0) i.putExtra(MerchantBaseActivity.EXTRA_BRANCH, order.optString("restaurant_id"));
            a.startActivity(i);
        });
        call.setOnClickListener(v -> chooseCall(a, displayId, actionId, driverName));
    }

    static void chooseCall(Activity activity, String orderId, String orderDbId, String driverName) {
        String id = CallPeerIdentity.first(orderId, orderDbId);
        if (id.isEmpty()) return;
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Panggil Driver")
                .setItems(new String[]{"Panggilan suara", "Panggilan video"}, (dialog, choice) -> {
                    Intent call = new Intent(activity, WebRtcCallActivity.class)
                            .putExtra("order_id", id)
                            .putExtra("order_db_id", orderDbId != null && orderDbId.matches("[0-9]+") ? orderDbId : "")
                            .putExtra("source", "orders")
                            .putExtra("peer_name", driverName)
                            .putExtra("call_type", choice == 0 ? "audio" : "video");
                    activity.startActivity(call);
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
