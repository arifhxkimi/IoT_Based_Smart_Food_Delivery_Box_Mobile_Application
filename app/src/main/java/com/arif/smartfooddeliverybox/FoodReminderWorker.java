package com.arif.smartfooddeliverybox;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.concurrent.TimeUnit;

public class FoodReminderWorker extends Worker {

    // 15 minutes threshold
    private static final long REMINDER_THRESHOLD = TimeUnit.MINUTES.toMillis(15);

    // Do not spam user more often than every 30 minutes
    private static final long REMINDER_COOLDOWN = TimeUnit.MINUTES.toMillis(30);

    private static final String PREFS_NAME = "food_reminder_prefs";
    private static final String KEY_LAST_REMINDER_PREFIX = "last_reminder_"; // + boxId

    public FoodReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            FirebaseHelper firebaseHelper = FirebaseHelper.getInstance();
            NotificationHelper notificationHelper = new NotificationHelper(getApplicationContext());

            DatabaseReference boxesRef = firebaseHelper.getDatabaseReference().child("boxes");

            // ✅ Make it synchronous: block until data is retrieved
            DataSnapshot snapshot = Tasks.await(boxesRef.get());

            long now = System.currentTimeMillis();
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            for (DataSnapshot boxSnap : snapshot.getChildren()) {

                DeliveryBox box = boxSnap.getValue(DeliveryBox.class);
                if (box == null) continue;

                // Only enabled boxes
                if (!box.isEnabled()) continue;

                // Only care about boxes that contain food
                if (!"occupied".equalsIgnoreCase(box.getStatus())) continue;

                long deliveredAt = box.getDeliveredAt();
                if (deliveredAt <= 0) continue;

                long timeInside = now - deliveredAt;

                // Too soon → no reminder
                if (timeInside < REMINDER_THRESHOLD) continue;

                String boxId = boxSnap.getKey();
                if (boxId == null) continue;

                // ✅ Local anti-spam (NO DB write)
                String key = KEY_LAST_REMINDER_PREFIX + boxId;
                long lastReminder = prefs.getLong(key, 0);

                if (lastReminder > 0 && now - lastReminder < REMINDER_COOLDOWN) {
                    continue;
                }

                long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInside);

                notificationHelper.notifyPickupReminder(
                        box.getBoxNumber(),
                        String.valueOf(minutes)
                );

                prefs.edit().putLong(key, now).apply();
            }

            return Result.success();

        } catch (Exception e) {
            // If offline / Firebase fails -> retry later
            return Result.retry();
        }
    }
}
