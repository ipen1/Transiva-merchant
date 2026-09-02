package com.transiva.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Single stable entry point for Merchant <-> Driver communication. */
public final class MerchantDriverCommunication {
    private MerchantDriverCommunication() {}

    public static void attach(MerchantOrdersActivity a, LinearLayout box, JSONObject order,
                              String actionId, String displayId, String driverName) {
        String driverPhone = first(order, "driver_phone", "driver_mobile", "driver_tel", "driver_phone_number");
        TextView caption = a.tv("Komunikasi Driver", 12, a.MUTED, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, a.dp(10), 0, a.dp(6));
        box.addView(caption, cp);

        LinearLayout row = a.row();
        Button chat = a.outlineBtn("💬 Pesan");
        Button call = a.outlineBtn("☎ Telepon");
        row.addView(chat, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        row.addView(call, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        box.addView(row);

        chat.setOnClickListener(v -> {
            Intent i = new Intent(a, MerchantDriverChatActivity.class);
            i.putExtra("order_id", displayId);
            i.putExtra("order_db_id", actionId);
            i.putExtra("driver_name", driverName);
            a.startActivity(i);
        });
        call.setEnabled(!normalize(driverPhone).isEmpty());
        call.setAlpha(call.isEnabled() ? 1f : .45f);
        call.setOnClickListener(v -> openDialer(a, driverPhone));
    }

    private static void openDialer(Activity a, String raw) {
        String number = normalize(raw);
        if (number.isEmpty()) return;
        try { a.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))); }
        catch (ActivityNotFoundException ignored) { }
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        String n = raw.replaceAll("[^0-9+]", "");
        if (n.startsWith("08")) n = "+62" + n.substring(1);
        else if (n.startsWith("8")) n = "+62" + n;
        else if (n.startsWith("62")) n = "+" + n;
        return n.matches("\\+?[0-9]{8,15}") ? n : "";
    }
    private static String first(JSONObject o, String... keys) {
        if (o == null) return ""; for (String k: keys) { String v=o.optString(k,"").trim(); if(!v.isEmpty()&&!"null".equalsIgnoreCase(v)) return v; } return "";
    }
}
