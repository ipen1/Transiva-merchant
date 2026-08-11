package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Server-side validation for merchant Bearer sessions. Network failures never force logout. */
public final class SessionValidationClient {
    private static final String URL_VALIDATE = "https://transiva.my.id/server/native_validate_session.php";
    private static final long MIN_VALIDATION_INTERVAL_MS = 90_000L;
    private static final AtomicLong LAST_ATTEMPT_MS = new AtomicLong(0L);
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    private SessionValidationClient() {}

    public static void validate(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SessionManager session = new SessionManager(app);
        final String token = session.getToken() == null ? "" : session.getToken().trim();
        if (!session.isLoggedIn() || token.isEmpty() || !"merchant".equals(session.getRole())) return;

        long now = System.currentTimeMillis();
        long last = LAST_ATTEMPT_MS.get();
        if (last > 0L && now - last < MIN_VALIDATION_INTERVAL_MS) return;
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        LAST_ATTEMPT_MS.set(now);

        if (MerchantNetworkExecutor.executeRead(app, "session-validation", () -> {
            HttpURLConnection conn = null;
            try {
                conn = MerchantApiClient.open(app, URL_VALIDATE);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                int status = conn.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
                String raw = read(stream);
                String code = "";
                try { code = new JSONObject(raw).optString("code", ""); } catch (Exception ignored) {}

                if ((status == 401 || status == 403) && ForceLogoutManager.isForceLogoutCode(code)) {
                    ForceLogoutManager.execute(app, code.isEmpty() ? "SESSION_REVOKED" : code);
                } else if (status >= 200 && status < 300) {
                    session.touchSession();
                }
            } catch (Exception ignored) {
                // Timeout/DNS/TLS/server outage is not proof that the session is invalid.
            } finally {
                if (conn != null) conn.disconnect();
                IN_FLIGHT.set(false);
            }
        }) == null) {
            IN_FLIGHT.set(false);
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }
}
