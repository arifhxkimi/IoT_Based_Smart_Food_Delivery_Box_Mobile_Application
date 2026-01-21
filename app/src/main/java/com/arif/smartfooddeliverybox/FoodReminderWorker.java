package com.arif.smartfooddeliverybox;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.TimeUnit;

public class FoodReminderWorker extends Worker {

    // 15 minutes threshold
    private static final long REMINDER_THRESHOLD =
            TimeUnit.MINUTES.toMillis(15);

    // Do not spam user more often than every 30 minutes
    private static final long REMINDER_COOLDOWN =
            TimeUnit.MINUTES.toMillis(30);

    public FoodReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        FirebaseHelper firebaseHelper = FirebaseHelper.getInstance();
        NotificationHelper notificationHelper =
                new NotificationHelper(getApplicationContext());

        DatabaseReference boxesRef =
                firebaseHelper.getDatabaseReference().child("boxes");

        boxesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                long now = System.currentTimeMillis();

                for (DataSnapshot boxSnap : snapshot.getChildren()) {

                    DeliveryBox box = boxSnap.getValue(DeliveryBox.class);
                    if (box == null || !box.isEnabled()) continue;

                    // Only care about boxes that contain food
                    if (!"occupied".equalsIgnoreCase(box.getStatus())) continue;

                    long deliveredAt = box.getDeliveredAt();
                    if (deliveredAt <= 0) continue;

                    long timeInside = now - deliveredAt;

                    // Too soon → no reminder
                    if (timeInside < REMINDER_THRESHOLD) continue;

                    // Anti-spam check
                    Long lastReminder =
                            boxSnap.child("lastReminderAt").getValue(Long.class);

                    if (lastReminder != null &&
                            now - lastReminder < REMINDER_COOLDOWN) {
                        continue;
                    }

                    // Send notification
                    long minutes =
                            TimeUnit.MILLISECONDS.toMinutes(timeInside);

                    notificationHelper.notifyPickupReminder(
                            box.getBoxNumber(),
                            String.valueOf(minutes)
                    );

                    // Save reminder timestamp
                    boxSnap.getRef()
                            .child("lastReminderAt")
                            .setValue(now);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silent fail — worker will retry later
            }
        });

        return Result.success();
    }
}
