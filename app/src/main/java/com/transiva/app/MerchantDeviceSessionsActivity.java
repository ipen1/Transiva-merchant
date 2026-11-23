package com.transiva.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;

public class MerchantDeviceSessionsActivity extends MerchantBaseActivity {
    private LinearLayout list;
    private final String endpoint = BASE + "merchant_device_sessions.php";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        root.addView(title("Perangkat & Sesi"));
        root.addView(sub(isStaff() ? "Riwayat perangkat akun staf ini" : "Riwayat perangkat Pemilik dan Staf"));
        Button refresh = outlineBtn("Muat ulang");
        refresh.setOnClickListener(v -> load());
        root.addView(refresh);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        load();
    }

    private void load() {
        list.removeAllViews();
        list.addView(card("Memuat sesi…"));
        MerchantNetworkExecutor.executeRead(this, "merchant-sessions", () -> {
            try {
                JSONObject result = new JSONObject(get(endpoint));
                if (!result.optBoolean("success")) throw new IllegalStateException(result.optString("message"));
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) render(result.optJSONArray("sessions"));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    list.removeAllViews();
                    list.addView(card("Gagal memuat sesi perangkat."));
                });
            }
        });
    }

    private void render(JSONArray sessions) {
        list.removeAllViews();
        if (sessions == null || sessions.length() == 0) {
            list.addView(card("Belum ada riwayat sesi."));
            return;
        }
        for (int index = 0; index < sessions.length(); index++) {
            JSONObject session = sessions.optJSONObject(index);
            if (session == null) continue;
            long id = session.optLong("id");
            String account = session.optString("account_type", "owner");
            boolean current = session.optInt("is_current") == 1;
            boolean revoked = !session.isNull("revoked_at") && !session.optString("revoked_at").isEmpty();
            boolean expired = session.optInt("is_expired") == 1;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(9), dp(12), dp(9));
            String person = "staff".equals(account)
                    ? "Staf · " + session.optString("staff_username") + " · " + session.optString("outlet_name", "")
                    : "Pemilik";
            item.addView(tv(session.optString("device_name", "Android"), 15, NAVY, true));
            item.addView(sub(person + (current ? " · Perangkat ini" : revoked ? " · Dicabut" : expired ? " · Kedaluwarsa" : " · Aktif")));
            item.addView(sub("Login " + session.optString("created_at", "-")));
            if (!session.isNull("last_used_at")) item.addView(sub("Terakhir aktif " + session.optString("last_used_at")));
            if (!current && !revoked && !expired) {
                item.addView(sub("Kedaluwarsa " + session.optString("expires_at", "-")));
                Button revoke = outlineBtn("Keluarkan perangkat");
                revoke.setOnClickListener(v -> PremiumDialogs.builder(this)
                        .setTitle("Keluarkan perangkat?")
                        .setMessage("Sesi pada perangkat tersebut akan dicabut.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Keluarkan", (dialog, which) -> revoke(id, account, v))
                        .show());
                item.addView(revoke);
            }
            list.addView(item);
        }
    }

    private void revoke(long id, String account, View button) {
        button.setEnabled(false);
        boolean queued = MerchantNetworkExecutor.executeWrite("revoke-session:" + account + ":" + id, () -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token_id", id);
                body.put("account_type", account);
                JSONObject result = new JSONObject(postJson(BASE + "merchant_revoke_device_session.php", body));
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    toast(result.optString("message", "Sesi diperbarui."));
                    load();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    button.setEnabled(true);
                    toast("Gagal mengeluarkan perangkat.");
                });
            }
        });
        if (!queued) button.setEnabled(true);
    }
}
