package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Serial, session-bound registration; resume repairs failed/rotated tokens without login. */
final class MerchantPushRegistration {
    private static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor();
    private static long lastRequest;
    private MerchantPushRegistration() { }

    static synchronized void sync(Context context, boolean force) {
        Context app = context.getApplicationContext();
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && lastRequest != 0 && now - lastRequest < 30000L) return;
        lastRequest = now;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> onToken(app, token))
                .addOnFailureListener(error -> app.getSharedPreferences("merchant_push_health", 0)
                        .edit().putString("registration", "Token Firebase belum tersedia").apply());
    }

    static void onToken(Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        Context app = context.getApplicationContext();
        String clean = token.trim();
        new SessionManager(app).saveFcmToken(clean);
        app.getSharedPreferences("transiva_fcm", 0).edit().putString("fcm_token", clean).apply();
        IO.execute(() -> prepare(app, clean));
    }

    private static void prepare(Context app, String token) {
        try {
            SessionManager session = new SessionManager(app);
            if (!session.isLoggedIn() || !"merchant".equalsIgnoreCase(session.getRole())) return;
            String auth = session.getToken();
            if (auth.isEmpty() || !token.equals(session.getFcmToken())) return;
            SharedPreferences health = app.getSharedPreferences("merchant_push_health", 0);
            String key = hash(auth + "|" + token);
            if (key.equals(health.getString("confirmed_key", ""))
                    && System.currentTimeMillis() - health.getLong("confirmed_at", 0) < 21600000L) return;
            long sequence = Math.max(1, health.getLong("sequence", 0) + 1);
            health.edit().putLong("sequence", sequence).commit();
            JSONObject payload = new JSONObject();
            payload.put("fcm_token", token);
            payload.put("registration_protocol", 2);
            payload.put("client_sequence", sequence);
            payload.put("registration_id", UUID.randomUUID().toString());
            payload.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(app));
            payload.put("app_version", app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName);
            payload.put("platform", "android_native");
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("model", Build.MODEL);
            payload.put("android_version", Build.VERSION.RELEASE);
            upload(app, auth, token, key, payload, 0);
        } catch (Exception error) { Log.w("MerchantPush", "Tidak dapat menyiapkan registrasi"); }
    }

    private static void upload(Context app, String auth, String token, String key, JSONObject payload, int attempt) {
        SessionManager session = new SessionManager(app);
        if (!session.isLoggedIn() || !"merchant".equalsIgnoreCase(session.getRole())
                || !auth.equals(session.getToken()) || !token.equals(session.getFcmToken())) return;
        SharedPreferences health = app.getSharedPreferences("merchant_push_health", 0);
        if (key.equals(health.getString("confirmed_key", ""))
                && System.currentTimeMillis() - health.getLong("confirmed_at", 0) < 21600000L) return;
        HttpURLConnection connection = null;
        boolean retry = true;
        try {
            connection = MerchantApiClient.open(app, "https://transiva.my.id/server/save_fcm_token.php");
            // Pin credentials to this job; a late request cannot claim a later account's session.
            connection.setRequestProperty("Authorization", "Bearer " + auth);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) body.append(line);
            }
            JSONObject response = new JSONObject(body.toString());
            if (!auth.equals(new SessionManager(app).getToken())) return;
            if (code >= 200 && code < 300 && response.optBoolean("success", false)) {
                health.edit().putString("confirmed_key", key).putLong("confirmed_at", System.currentTimeMillis())
                        .putString("registration", "Terdaftar di server").apply();
                return;
            }
            health.edit().putString("registration", "Belum terkonfirmasi: HTTP " + code + " " + response.optString("code", "")).apply();
            retry = code == 408 || code == 429 || code >= 500;
            if (code == 409 && "FCM_REGISTRATION_STALE".equals(response.optString("code"))) {
                JSONObject data = response.optJSONObject("data");
                long serverSequence = response.optLong("server_sequence", data == null ? 0 : data.optLong("server_sequence", 0));
                long sequence = Math.max(health.getLong("sequence", 0), serverSequence) + 1;
                health.edit().putLong("sequence", sequence).commit();
                payload.put("client_sequence", sequence);
                payload.put("registration_id", UUID.randomUUID().toString());
                retry = true;
            }
        } catch (Exception error) {
            health.edit().putString("registration", "Registrasi tertunda, menunggu koneksi/server").apply();
        } finally { if (connection != null) connection.disconnect(); }
        if (retry && attempt < 3) {
            long delay = new long[]{5, 30, 120}[attempt];
            IO.schedule(() -> upload(app, auth, token, key, payload, attempt + 1), delay, TimeUnit.SECONDS);
        }
    }

    static String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return hex.toString();
    }
}
