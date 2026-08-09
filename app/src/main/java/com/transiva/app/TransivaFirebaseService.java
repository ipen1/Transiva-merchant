package com.transiva.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Locale;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    /*
     * Gunakan ID channel BARU. Setting importance/sound channel Android tidak dapat
     * diubah setelah channel lama dibuat di perangkat.
     */
    private static final String CHANNEL_URGENT = "transiva_merchant_urgent_v2";
    private static final String CHANNEL_NORMAL = "transiva_merchant_general_v2";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String,String> data = message.getData();

        String title = value(data, "title", "Transiva Merchant");
        String body = value(data, "body", "Ada pembaruan untuk merchant Anda.");
        String type = value(data, "type", value(data, "event", "order"));
        String status = value(data, "status",
                value(data, "order_status",
                        value(data, "driver_status", "")));
        String orderId = value(data, "order_id", value(data, "id", ""));

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null &&
                    !message.getNotification().getTitle().trim().isEmpty()) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null &&
                    !message.getNotification().getBody().trim().isEmpty()) {
                body = message.getNotification().getBody();
            }
        }

        String signal = (type + " " + status + " " + title + " " + body)
                .toLowerCase(Locale.US);

        boolean incomingOrder = isIncomingOrder(signal);
        boolean driverArrived = isDriverArrived(signal);
        boolean urgent = incomingOrder || driverArrived;

        /*
         * Beri judul/pesan yang jelas walaupun backend hanya mengirim status/event.
         * Konten eksplisit dari server tetap dipakai jika tersedia.
         */
        if (incomingOrder && (title.equals("Transiva Merchant") || title.trim().isEmpty())) {
            title = "🔔 Pesanan Baru Masuk";
        }
        if (incomingOrder && (body.equals("Ada pembaruan untuk merchant Anda.") || body.trim().isEmpty())) {
            body = orderId.isEmpty()
                    ? "Ada pesanan baru. Buka Transiva Merchant untuk memprosesnya."
                    : "Pesanan #" + orderId + " menunggu konfirmasi merchant.";
        }

        if (driverArrived) {
            if (title.equals("Transiva Merchant") || title.trim().isEmpty()) {
                title = "🛵 Driver Sudah Tiba";
            }
            if (body.equals("Ada pembaruan untuk merchant Anda.") || body.trim().isEmpty()) {
                body = orderId.isEmpty()
                        ? "Driver sudah tiba di titik pickup."
                        : "Driver untuk pesanan #" + orderId + " sudah tiba di titik pickup.";
            }
        }

        createChannels();
        if (urgent) wakeScreenBriefly();

        Intent target = targetIntent(type, status);
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);

        target.putExtra("notification_type", type);
        target.putExtra("notification_status", status);
        target.putExtra("wake_screen", urgent);
        target.putExtra("urgent_notification", urgent);
        if (!orderId.isEmpty()) target.putExtra("order_id", orderId);

        if (data != null) {
            for (Map.Entry<String,String> e : data.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    target.putExtra(e.getKey(), e.getValue());
                }
            }
        }

        int requestCode = !orderId.isEmpty()
                ? (orderId + "|" + status + "|" + type).hashCode()
                : (int)(System.currentTimeMillis() & 0x7fffffff);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                requestCode,
                target,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String channelId = urgent ? CHANNEL_URGENT : CHANNEL_NORMAL;

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(urgent ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(urgent ? NotificationCompat.DEFAULT_ALL : NotificationCompat.DEFAULT_SOUND)
                .setCategory(urgent ? NotificationCompat.CATEGORY_CALL : NotificationCompat.CATEGORY_STATUS);

        /*
         * Full-screen intent adalah mekanisme Android resmi untuk notifikasi sangat
         * penting. Activity tujuan akan showWhenLocked + turnScreenOn.
         *
         * Di Android versi baru pengguna/OEM masih dapat membatasi full-screen intent.
         * Jika dibatasi, notifikasi HIGH/MAX tetap berbunyi dan tampil heads-up.
         */
        if (urgent) {
            b.setFullScreenIntent(contentIntent, true);
        }

        NotificationManager nm =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(requestCode, b.build());
    }


    @SuppressWarnings("deprecation")
    private void wakeScreenBriefly() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.ON_AFTER_RELEASE,
                    "transiva:merchantUrgentWake"
            );
            wl.acquire(8000L);
        } catch (Exception ignored) {
            // Full-screen/heads-up notification tetap menjadi fallback.
        }
    }

    private Intent targetIntent(String type, String status) {
        String low = ((type == null ? "" : type) + " " +
                (status == null ? "" : status)).toLowerCase(Locale.US);

        if (low.contains("review")) {
            return new Intent(this, MerchantReviewsActivity.class);
        }
        if (low.contains("menu") && !low.contains("order")) {
            return new Intent(this, MerchantMenuListActivity.class);
        }
        return new Intent(this, MerchantOrdersActivity.class);
    }

    private boolean isIncomingOrder(String signal) {
        if (signal == null) return false;

        // Event yang umum dipakai backend Transiva / payload FCM.
        return signal.contains("new_order")
                || signal.contains("order_new")
                || signal.contains("order_created")
                || signal.contains("incoming_order")
                || signal.contains("pesanan baru")
                || signal.contains("order baru")
                || signal.contains("menunggu merchant")
                || signal.contains("merchant_pending")
                || (signal.contains("order") && signal.contains("pending"));
    }

    private boolean isDriverArrived(String signal) {
        if (signal == null) return false;

        return signal.contains("arrived_pickup")
                || signal.contains("driver_arrived_pickup")
                || signal.contains("driver_arrived")
                || signal.contains("driver tiba")
                || signal.contains("tiba di resto")
                || signal.contains("tiba di restoran")
                || signal.contains("titik pickup");
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel urgent = new NotificationChannel(
                CHANNEL_URGENT,
                "Pesanan & Driver Tiba",
                NotificationManager.IMPORTANCE_HIGH
        );
        urgent.setDescription("Pesanan baru dan pemberitahuan driver tiba di titik pickup");
        urgent.enableVibration(true);
        urgent.setVibrationPattern(new long[]{0, 450, 180, 450, 180, 700});
        urgent.enableLights(true);
        urgent.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        urgent.setSound(sound, attrs);
        urgent.setBypassDnd(false);
        nm.createNotificationChannel(urgent);

        NotificationChannel normal = new NotificationChannel(
                CHANNEL_NORMAL,
                "Notifikasi Merchant",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        normal.setDescription("Pembaruan umum Transiva Merchant");
        normal.enableVibration(true);
        normal.setSound(sound, attrs);
        nm.createNotificationChannel(normal);
    }

    private String value(Map<String,String> data, String key, String fallback) {
        if (data == null) return fallback;
        String v = data.get(key);
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }
}
