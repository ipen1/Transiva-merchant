package com.transiva.app;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

/** Central HTTP/security gateway for Transiva Merchant-owned requests. */
public final class MerchantApiClient {
    private static final String HOST = "transiva.my.id";
    private MerchantApiClient() {}

    public static HttpURLConnection open(Context context, String urlText) throws IOException {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !HOST.equalsIgnoreCase(url.getHost())) {
            throw new IOException("Merchant API hanya boleh mengakses HTTPS Transiva");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestProperty("Connection", "close");
        applySecurity(context, connection);
        return connection;
    }

    public static void applySecurity(Context context, HttpURLConnection connection) {
        if (context == null || connection == null) return;
        Context app = context.getApplicationContext();
        SessionManager session = new SessionManager(app);
        String token = session.getToken();
        if (token != null && !token.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        String uuid = DeviceIdentityManager.getInstallationUuid(app);
        if (uuid != null && !uuid.trim().isEmpty()) {
            connection.setRequestProperty("X-Device-UUID", uuid.trim());
        }
        connection.setRequestProperty("X-App-Scope", "merchant");
        connection.setRequestProperty("X-Transiva-App", "Android-Merchant");
        connection.setRequestProperty("Accept", "application/json");
    }

    public static String idempotencyKey(String action) {
        String prefix = action == null ? "action" : action.trim().toLowerCase(Locale.US);
        if (prefix.isEmpty()) prefix = "action";
        return prefix + "-" + UUID.randomUUID();
    }
}
