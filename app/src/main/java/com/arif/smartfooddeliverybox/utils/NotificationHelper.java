package com.arif.smartfooddeliverybox.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.arif.smartfooddeliverybox.MainActivity;
import com.arif.smartfooddeliverybox.R;

public class NotificationHelper {

    private static final String CHANNEL_ID = "smart_delivery_box";
    private static final String CHANNEL_NAME = "Smart Delivery Box";
    private static final String CHANNEL_DESC = "Notifications for delivery box events";

    private Context context;
    private NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public void showDeliveryNotification(String boxNumber, String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        int notificationId = boxNumber.hashCode();
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    // Specific notification methods
    public void notifyBoxUnlocked(String boxNumber) {
        showDeliveryNotification(
                boxNumber,
                "Box " + boxNumber + " Unlocked",
                "Your delivery box is now unlocked. Waiting for rider to place food."
        );
    }

    public void notifyFoodDelivered(String boxNumber) {
        showDeliveryNotification(
                boxNumber,
                "🎉 Food Delivered!",
                "Your food has been delivered to Box " + boxNumber + " and is now secured."
        );
    }

    public void notifyFoodRetrieved(String boxNumber) {
        showDeliveryNotification(
                boxNumber,
                "✅ Food Retrieved",
                "Box " + boxNumber + " is now available for your next delivery."
        );
    }

    public void notifyBoxOffline(String boxNumber) {
        showDeliveryNotification(
                boxNumber,
                "⚠️ Box Offline",
                "Box " + boxNumber + " is currently offline. Please check the connection."
        );
    }
}