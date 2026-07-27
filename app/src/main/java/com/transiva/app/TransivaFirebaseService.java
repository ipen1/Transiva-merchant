package com.transiva.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class TransivaFirebaseService extends FirebaseMessagingService {
    private static final String CHANNEL = "transiva_merchant";
    @Override public void onMessageReceived(RemoteMessage message) {
        String title = "Transiva Merchant";
        String body = "Ada pembaruan untuk merchant Anda.";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        } else if (!message.getData().isEmpty()) {
            if (message.getData().get("title") != null) title = message.getData().get("title");
            if (message.getData().get("body") != null) body = message.getData().get("body");
        }
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Merchant Transiva",NotificationManager.IMPORTANCE_HIGH));
        Intent i = new Intent(this, MerchantOrdersActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(getApplicationInfo().icon).setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH);
        nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), b.build());
    }
}
