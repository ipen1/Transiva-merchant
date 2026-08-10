package com.transiva.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Locale;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    // Channel IDs are intentionally versioned because Android channel sound is immutable.
    private static final String CHANNEL_ORDER = "transiva_merchant_order_v3";
    private static final String CHANNEL_ARRIVAL = "transiva_merchant_arrival_v3";
    private static final String CHANNEL_NORMAL = "transiva_merchant_general_v3";
    private static final String CHANNEL_CHAT = "transiva_merchant_driver_chat_v1";
    private static final long DEDUPE_WINDOW_MS = 8000L;

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();

        String title = value(data, "title", "Transiva Merchant");
        String body = value(data, "body", "Ada pembaruan untuk merchant Anda.");
        String type = value(data, "type", value(data, "event", "order"));
        String status = value(data, "status",
                value(data, "order_status", value(data, "driver_status", "")));
        String orderId = value(data, "order_id", value(data, "id", ""));

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null
                    && !message.getNotification().getTitle().trim().isEmpty()) {
                title = message.getNotification().getTitle().trim();
            }
            if (message.getNotification().getBody() != null
                    && !message.getNotification().getBody().trim().isEmpty()) {
                body = message.getNotification().getBody().trim();
            }
        }

        String signal = (type + " " + status + " " + title + " " + body)
                .toLowerCase(Locale.US);
        boolean incomingOrder = isIncomingOrder(signal);
        boolean driverArrived = isDriverArrived(signal);
        boolean merchantDriverChat = isMerchantDriverChat(signal);
        boolean urgent = incomingOrder || driverArrived || merchantDriverChat;

        if (incomingOrder && (title.equals("Transiva Merchant") || title.isEmpty())) {
            title = "🔔 Pesanan Baru Masuk";
        }
        if (incomingOrder && (body.equals("Ada pembaruan untuk merchant Anda.") || body.isEmpty())) {
            body = orderId.isEmpty()
                    ? "Ada pesanan baru. Buka Transiva Merchant untuk memprosesnya."
                    : "Pesanan #" + orderId + " menunggu konfirmasi merchant.";
        }
        if (driverArrived) {
            if (title.equals("Transiva Merchant") || title.isEmpty()) title = "🛵 Driver Sudah Tiba";
            if (body.equals("Ada pembaruan untuk merchant Anda.") || body.isEmpty()) {
                body = orderId.isEmpty()
                        ? "Driver sudah tiba di titik pickup."
                        : "Driver untuk pesanan #" + orderId + " sudah tiba di titik pickup.";
            }
        }

        // Refresh visible screens immediately. Polling remains only as a fallback.
        MerchantRealtime.publish(this, incomingOrder ? "new_order" : (driverArrived ? "driver_arrived" : (merchantDriverChat ? "merchant_driver_chat" : "update")), orderId);

        String dedupeKey = (orderId + "|" + type + "|" + status + "|" + title).toLowerCase(Locale.US);
        if (isDuplicate(dedupeKey)) return;

        createChannels();
        if (urgent) wakeScreenBriefly();

        Intent target = targetIntent(type, status, signal);
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        target.putExtra("notification_type", type);
        target.putExtra("notification_status", status);
        target.putExtra("wake_screen", urgent);
        target.putExtra("urgent_notification", urgent);
        if (!orderId.isEmpty()) target.putExtra("order_id", orderId);
        if (data != null) {
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) target.putExtra(e.getKey(), e.getValue());
            }
        }

        int requestCode = !orderId.isEmpty()
                ? (orderId + "|" + status + "|" + type).hashCode()
                : (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, requestCode, target,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        String channelId = incomingOrder ? CHANNEL_ORDER : (driverArrived ? CHANNEL_ARRIVAL : (merchantDriverChat ? CHANNEL_CHAT : CHANNEL_NORMAL));
        Uri sound = incomingOrder ? rawUri(R.raw.order_new)
                : (driverArrived ? rawUri(R.raw.order_taken) : rawUri(R.raw.order_notif));

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(urgent ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(merchantDriverChat ? NotificationCompat.CATEGORY_MESSAGE : (urgent ? NotificationCompat.CATEGORY_CALL : NotificationCompat.CATEGORY_STATUS))
                .setSound(sound)
                .setVibrate(urgent ? new long[]{0, 450, 180, 450, 180, 700} : new long[]{0, 250});
        if (urgent) b.setFullScreenIntent(contentIntent, true);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(requestCode, b.build());
    }

    private boolean isDuplicate(String key) {
        if (key == null || key.trim().isEmpty()) return false;
        SharedPreferences sp = getSharedPreferences("merchant_notification_dedupe", MODE_PRIVATE);
        String oldKey = sp.getString("key", "");
        long oldAt = sp.getLong("at", 0L);
        long now = System.currentTimeMillis();
        if (key.equals(oldKey) && now - oldAt < DEDUPE_WINDOW_MS) return true;
        sp.edit().putString("key", key).putLong("at", now).apply();
        return false;
    }

    @SuppressWarnings("deprecation")
    private void wakeScreenBriefly() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "transiva:merchantUrgentWake");
            wl.acquire(8000L);
        } catch (Exception ignored) { }
    }

    private Intent targetIntent(String type, String status, String signal) {
        String low = ((type == null ? "" : type) + " " + (status == null ? "" : status) + " " + (signal == null ? "" : signal)).toLowerCase(Locale.US);
        if (isMerchantDriverChat(low)) return new Intent(this, MerchantDriverChatActivity.class);
        if (low.contains("review")) return new Intent(this, MerchantReviewsActivity.class);
        if (low.contains("menu") && !low.contains("order")) return new Intent(this, MerchantMenuListActivity.class);
        return new Intent(this, MerchantOrdersActivity.class);
    }

    private boolean isIncomingOrder(String signal) {
        return signal != null && (signal.contains("new_order")
                || signal.contains("order_new")
                || signal.contains("order_created")
                || signal.contains("incoming_order")
                || signal.contains("pesanan baru")
                || signal.contains("order baru")
                || signal.contains("menunggu merchant")
                || signal.contains("merchant_pending")
                || (signal.contains("order") && signal.contains("pending")));
    }

    private boolean isMerchantDriverChat(String signal) {
        if (signal == null) return false;
        String low = signal.toLowerCase(Locale.US);
        return low.contains("merchant_driver_chat") || low.contains("driver_merchant_chat")
                || (low.contains("chat") && low.contains("merchant") && low.contains("driver"));
    }

    private boolean isDriverArrived(String signal) {
        return signal != null && (signal.contains("arrived_pickup")
                || signal.contains("driver_arrived_pickup")
                || signal.contains("driver_arrived")
                || signal.contains("driver tiba")
                || signal.contains("tiba di resto")
                || signal.contains("tiba di restoran")
                || signal.contains("titik pickup"));
    }

    private Uri rawUri(int resId) {
        return Uri.parse("android.resource://" + getPackageName() + "/" + resId);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel order = new NotificationChannel(CHANNEL_ORDER, "Pesanan Baru", NotificationManager.IMPORTANCE_HIGH);
        order.setDescription("Pesanan baru Transiva Merchant");
        order.enableVibration(true);
        order.setVibrationPattern(new long[]{0, 450, 180, 450, 180, 700});
        order.enableLights(true);
        order.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        order.setSound(rawUri(R.raw.order_new), attrs);
        nm.createNotificationChannel(order);

        NotificationChannel arrival = new NotificationChannel(CHANNEL_ARRIVAL, "Driver Tiba", NotificationManager.IMPORTANCE_HIGH);
        arrival.setDescription("Driver tiba di titik pickup merchant");
        arrival.enableVibration(true);
        arrival.setVibrationPattern(new long[]{0, 350, 150, 350});
        arrival.enableLights(true);
        arrival.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        arrival.setSound(rawUri(R.raw.order_taken), attrs);
        nm.createNotificationChannel(arrival);

        NotificationChannel chat = new NotificationChannel(CHANNEL_CHAT, "Chat Driver", NotificationManager.IMPORTANCE_HIGH);
        chat.setDescription("Pesan penting antara merchant dan driver");
        chat.enableVibration(true);
        chat.setVibrationPattern(new long[]{0, 350, 120, 350, 120, 500});
        chat.enableLights(true);
        chat.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        chat.setSound(rawUri(R.raw.order_notif), attrs);
        nm.createNotificationChannel(chat);

        NotificationChannel normal = new NotificationChannel(CHANNEL_NORMAL, "Notifikasi Merchant", NotificationManager.IMPORTANCE_DEFAULT);
        normal.setDescription("Pembaruan umum Transiva Merchant");
        normal.enableVibration(true);
        normal.setSound(rawUri(R.raw.order_notif), attrs);
        nm.createNotificationChannel(normal);
    }

    private String value(Map<String, String> data, String key, String fallback) {
        if (data == null) return fallback;
        String v = data.get(key);
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }
}
