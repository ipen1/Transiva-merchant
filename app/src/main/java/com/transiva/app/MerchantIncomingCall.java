package com.transiva.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Session-bound notification for calls from the driver assigned to a TransFood order. */
final class MerchantIncomingCall {
    private static final String CHANNEL = "transiva_merchant_incoming_call";
    private static final int NOTIFICATION_ID = 471;
    private static final Map<String, Long> closed = new LinkedHashMap<>();

    private MerchantIncomingCall() {}

    static synchronized void show(Context context, Map<String, String> data, String sessionHash) {
        String callId = data.get("call_id");
        closed.values().removeIf(expiry -> expiry <= SystemClock.elapsedRealtime());
        if (callId == null || !callId.matches("[a-f0-9]{48}") || closed.containsKey(callId)) return;
        long expiry;
        try { expiry = Long.parseLong(data.get("call_expires_epoch")); }
        catch (NumberFormatException invalid) { return; }
        long remaining = Math.min(45L, expiry - System.currentTimeMillis() / 1000L) * 1000L;
        if (remaining <= 0) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        String name = CallPeerIdentity.first(data.get("caller_name"), "Driver Transiva");
        boolean video = "video".equals(data.get("call_type"));
        Intent open = new Intent(context, WebRtcCallActivity.class)
                .setData(android.net.Uri.parse("transiva-merchant-call:" + callId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("incoming", true)
                .putExtra("call_id", callId)
                .putExtra("order_id", data.get("order_id"))
                .putExtra("source", data.get("source"))
                .putExtra("call_type", video ? "video" : "audio")
                .putExtra("peer_name", name)
                .putExtra("incoming_session_hash", sessionHash)
                .putExtra("incoming_deadline", SystemClock.elapsedRealtime() + remaining);
        if (!valid(context, open)) return;
        PendingIntent view = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "Panggilan Driver",
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setSound(Settings.System.DEFAULT_RINGTONE_URI, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
                channel.enableVibration(MerchantAppSettings.isVibrationEnabled(context));
                manager.createNotificationChannel(channel);
            }
            NotificationCompat.Builder alert = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(context.getApplicationInfo().icon)
                    .setContentTitle(name)
                    .setContentText(video ? "Panggilan video masuk" : "Panggilan suara masuk")
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(view).setOnlyAlertOnce(true)
                    .setTimeoutAfter(remaining)
                    .addAction(context.getApplicationInfo().icon, "Buka panggilan", view);
            if (Build.VERSION.SDK_INT < 26) alert.setSound(Settings.System.DEFAULT_RINGTONE_URI);
            manager.notify(callId, NOTIFICATION_ID, alert.build());
        } catch (RuntimeException failure) {
            android.util.Log.w("MerchantIncomingCall", "Panggilan tidak dapat ditampilkan", failure);
        }
    }

    static long remaining(Intent intent) {
        return intent.getLongExtra("incoming_deadline", 0) - SystemClock.elapsedRealtime();
    }

    static boolean valid(Context context, Intent intent) {
        SessionManager session = new SessionManager(context);
        String hash = intent.getStringExtra("incoming_session_hash");
        if (remaining(intent) <= 0 || hash == null || !session.isLoggedIn()
                || !"merchant".equalsIgnoreCase(session.getRole())) return false;
        try { return hash.equals(MerchantPushRegistration.hash(session.getToken())); }
        catch (Exception unavailable) { return false; }
    }

    static synchronized void cancel(Context context, String callId) {
        if (callId == null || callId.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        closed.values().removeIf(expiry -> expiry <= now);
        closed.put(callId, now + 90000L);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(callId, NOTIFICATION_ID);
    }
}
