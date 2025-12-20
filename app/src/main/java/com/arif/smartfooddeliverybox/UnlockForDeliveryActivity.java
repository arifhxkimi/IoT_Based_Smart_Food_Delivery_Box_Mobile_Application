package com.arif.smartfooddeliverybox;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class UnlockForDeliveryActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private CardView cardBoxInfo;
    private TextView tvBoxNumber, tvBoxLocation, tvBoxStatus;
    private TextView tvStatusMessage, tvInstructions;
    private MaterialButton btnUnlock, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private NotificationHelper notificationHelper;
    private String boxId;
    private String boxNumber;
    private String boxLocation;
    private ValueEventListener boxListener;
    private Handler handler;
    private boolean isWaitingForFood = false;
    private boolean hasUnlockedBox = false;
    private boolean isOffline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock_for_delivery);

        firebaseHelper = FirebaseHelper.getInstance();
        notificationHelper = new NotificationHelper(this);
        handler = new Handler();

        initViews();
        setupToolbar();
        setupListeners();

        // Get intent data
        boxId = getIntent().getStringExtra("boxId");
        boxNumber = getIntent().getStringExtra("boxNumber");
        boxLocation = getIntent().getStringExtra("boxLocation");

        if (boxId != null && boxNumber != null && boxLocation != null) {
            updateInitialUI();
            startBoxMonitoring();
        } else {
            findAvailableBox();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        cardBoxInfo = findViewById(R.id.cardBoxInfo);
        tvBoxNumber = findViewById(R.id.tvBoxNumber);
        tvBoxLocation = findViewById(R.id.tvBoxLocation);
        tvBoxStatus = findViewById(R.id.tvBoxStatus);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);
        tvInstructions = findViewById(R.id.tvInstructions);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnCancel = findViewById(R.id.btnCancel);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Unlock for Delivery");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        btnUnlock.setOnClickListener(v -> confirmUnlock());
        btnCancel.setOnClickListener(v -> finish()); // Simple cancel - just go back
    }

    private void findAvailableBox() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        tvStatusMessage.setText("Finding available box...");

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .orderByChild("status")
                .equalTo("available")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);

                        if (snapshot.exists()) {
                            for (DataSnapshot boxSnapshot : snapshot.getChildren()) {
                                DeliveryBox box = boxSnapshot.getValue(DeliveryBox.class);
                                if (box != null) {
                                    boxId = boxSnapshot.getKey();
                                    boxNumber = box.getBoxNumber();
                                    boxLocation = "Box " + box.getBoxNumber();

                                    updateInitialUI();
                                    startBoxMonitoring();
                                    btnUnlock.setEnabled(true);
                                    return;
                                }
                            }
                        }

                        showNoBoxesDialog();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(UnlockForDeliveryActivity.this,
                                "Error finding box: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void showNoBoxesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Boxes Available")
                .setMessage("All boxes are currently occupied. Please try again later.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void updateInitialUI() {
        tvBoxNumber.setText("Box " + boxNumber);
        tvBoxLocation.setText(boxLocation);
        tvBoxStatus.setText("Available");
        tvBoxStatus.setTextColor(getColor(R.color.status_available));

        tvStatusMessage.setText("Ready to unlock for delivery");
        tvInstructions.setText("Tap 'Unlock Box' then tell your rider:\n\n\"Please place the food in Box " +
                boxNumber + " at " + boxLocation + "\"");
    }

    private void startBoxMonitoring() {
        boxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                    if (box != null) {
                        handleBoxStatusChange(box);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UnlockForDeliveryActivity.this,
                        "Error monitoring box: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .addValueEventListener(boxListener);
    }

    private void handleBoxStatusChange(DeliveryBox box) {
        String status = box.getStatus();

        tvBoxStatus.setText(box.getStatusText());

        if ("unlocked".equals(status) && !isWaitingForFood) {
            isWaitingForFood = true;
            hasUnlockedBox = true; // Mark that we've unlocked
            btnUnlock.setEnabled(false);
            btnUnlock.setText("Box Unlocked - Waiting for Food");

            tvStatusMessage.setText("✓ Box is unlocked!");
            tvStatusMessage.setTextColor(getColor(R.color.status_unlocked));

            tvInstructions.setText("Tell your rider to place food in Box " + boxNumber +
                    ". The box will automatically lock when food is detected.");

        } else if ("occupied".equals(status) && isWaitingForFood) {
            showFoodStoredSuccess();
        }
    }

    private void confirmUnlock() {
        new AlertDialog.Builder(this)
                .setTitle("Unlock Box")
                .setMessage("Unlock Box " + boxNumber + " for delivery?\n\nMake sure your rider knows where to place the food.")
                .setPositiveButton("Yes, Unlock", (dialog, which) -> unlockBox())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unlockBox() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        // Check network connectivity
        if (!isNetworkAvailable()) {
            showOfflineDialog();
            progressBar.setVisibility(View.GONE);
            btnUnlock.setEnabled(true);
            return;
        }

        String userId = firebaseHelper.getCurrentUserId();

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("status")
                .setValue("unlocked")
                .addOnSuccessListener(aVoid -> {
                    firebaseHelper.getDatabaseReference()
                            .child("boxes")
                            .child(boxId)
                            .child("unlockedBy")
                            .setValue(userId);

                    firebaseHelper.getDatabaseReference()
                            .child("boxes")
                            .child(boxId)
                            .child("unlockedAt")
                            .setValue(System.currentTimeMillis());

                    logHistory("unlocked");

                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Box unlocked successfully!", Toast.LENGTH_SHORT).show();

                    // Send notification
                    notificationHelper.notifyBoxUnlocked(boxNumber);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUnlock.setEnabled(true);

                    if (!isNetworkAvailable()) {
                        showOfflineDialog();
                    } else {
                        Toast.makeText(this, "Failed to unlock: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showFoodStoredSuccess() {
        logHistory("food_stored");

        // Send notification
        notificationHelper.notifyFoodDelivered(boxNumber);

        new AlertDialog.Builder(this)
                .setTitle("✓ Food Stored!")
                .setMessage("Your food has been delivered and the box is now secured.\n\nYou can retrieve it anytime from the dashboard.")
                .setPositiveButton("Done", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ No Internet Connection")
                .setMessage("You're currently offline. Please check your internet connection and try again.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Retry", (dialog, which) -> unlockBox())
                .show();
    }

    private void logHistory(String action) {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        String historyId = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(userId)
                .push()
                .getKey();

        if (historyId != null) {
            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(historyId)
                    .child("boxNumber")
                    .setValue(boxNumber);

            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(historyId)
                    .child("action")
                    .setValue(action);

            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(historyId)
                    .child("timestamp")
                    .setValue(System.currentTimeMillis());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (boxListener != null && boxId != null) {
            firebaseHelper.getDatabaseReference()
                    .child("boxes")
                    .child(boxId)
                    .removeEventListener(boxListener);
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onBackPressed() {
        // FIX #1 & #3: Only show dialog if we've actually unlocked the box
        if (hasUnlockedBox && isWaitingForFood) {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel Delivery?")
                    .setMessage("The box is unlocked and waiting for food. Cancel and re-lock?")
                    .setPositiveButton("Yes, Cancel", (dialog, which) -> {
                        // Re-lock and return to available
                        firebaseHelper.getDatabaseReference()
                                .child("boxes")
                                .child(boxId)
                                .child("status")
                                .setValue("available")
                                .addOnSuccessListener(unused -> finish());
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else {
            // Haven't unlocked yet, just go back normally
            super.onBackPressed();
        }
    }
}