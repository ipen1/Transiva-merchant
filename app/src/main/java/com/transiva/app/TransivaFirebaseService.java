package com.transiva.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {
    private static final String CHANNEL = "transiva_merchant";

    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String,String> data = message.getData();
        String title = value(data, "title", "Transiva Merchant");
        String body = value(data, "body", "Ada pembaruan untuk merchant Anda.");
        String type = value(data, "type", value(data, "event", "order"));
        String orderId = value(data, "order_id", value(data, "id", ""));

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null && !message.getNotification().getTitle().trim().isEmpty()) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null && !message.getNotification().getBody().trim().isEmpty()) body = message.getNotification().getBody();
        }

        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,"Merchant Transiva",NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Pesanan dan pembaruan penting merchant");
            nm.createNotificationChannel(channel);
        }

        Intent i;
        String low = type == null ? "" : type.toLowerCase();
        if(low.contains("review")) i = new Intent(this, MerchantReviewsActivity.class);
        else if(low.contains("menu")) i = new Intent(this, MerchantMenuListActivity.class);
        else i = new Intent(this, MerchantOrdersActivity.class);

        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("notification_type", type);
        if(!orderId.isEmpty()) i.putExtra("order_id", orderId);
        for(Map.Entry<String,String> e : data.entrySet()) if(e.getKey()!=null && e.getValue()!=null) i.putExtra(e.getKey(), e.getValue());

        int requestCode = !orderId.isEmpty() ? orderId.hashCode() : (int)(System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pi=PendingIntent.getActivity(this,requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(low.contains("order") ? NotificationCompat.CATEGORY_MESSAGE : NotificationCompat.CATEGORY_STATUS);
        nm.notify(requestCode, b.build());
    }

    private String value(Map<String,String> data, String key, String fallback){
        if(data == null) return fallback;
        String v = data.get(key);
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }
}
