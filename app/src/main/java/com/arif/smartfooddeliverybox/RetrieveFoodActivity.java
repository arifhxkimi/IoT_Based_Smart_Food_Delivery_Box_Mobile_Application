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

public class RetrieveFoodActivity extends AppCompatActivity {

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
    private boolean isUnlocked = false;
    private boolean hasRetrieved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrieve_food);

        // Get intent data
        boxId = getIntent().getStringExtra("boxId");
        boxNumber = getIntent().getStringExtra("boxNumber");
        boxLocation = getIntent().getStringExtra("boxLocation");

        if (boxId == null) {
            Toast.makeText(this, "Error: Box information missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        firebaseHelper = FirebaseHelper.getInstance();
        notificationHelper = new NotificationHelper(this);
        handler = new Handler();

        initViews();
        setupToolbar();
        setupListeners();
        updateInitialUI();
        startBoxMonitoring();
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
            getSupportActionBar().setTitle("Retrieve Food");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        btnUnlock.setOnClickListener(v -> confirmUnlock());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void updateInitialUI() {
        tvBoxNumber.setText("Box " + boxNumber);
        tvBoxLocation.setText(boxLocation);
        tvBoxStatus.setText("Occupied - Food Inside");
        tvBoxStatus.setTextColor(getColor(R.color.primary));

        tvStatusMessage.setText("Your food is ready!");
        tvInstructions.setText("Tap 'Unlock & Retrieve' to open the box and collect your food.");
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
                Toast.makeText(RetrieveFoodActivity.this,
                        "Error monitoring box", Toast.LENGTH_SHORT).show();
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

        if ("unlocked".equals(status) && isUnlocked && !hasRetrieved) {
            // Box is unlocked - waiting for user to retrieve
            btnUnlock.setEnabled(false);
            btnUnlock.setText("Box Unlocked - Take Your Food");

            tvStatusMessage.setText("✓ Box is unlocked!");
            tvStatusMessage.setTextColor(getColor(R.color.status_unlocked));

            tvInstructions.setText("Please open the box and take your food. The box will automatically lock when closed.");

        } else if ("available".equals(status) && isUnlocked && !hasRetrieved) {
            // Sensor detected empty - food retrieved!
            hasRetrieved = true;
            showRetrievalSuccess();
        }
    }

    private void confirmUnlock() {
        new AlertDialog.Builder(this)
                .setTitle("Unlock Box")
                .setMessage("Unlock Box " + boxNumber + " to retrieve your food?")
                .setPositiveButton("Yes, Unlock", (dialog, which) -> unlockBox())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unlockBox() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        // Update box status to "unlocked"
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("status")
                .setValue("unlocked")
                .addOnSuccessListener(aVoid -> {
                    isUnlocked = true;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Box unlocked!", Toast.LENGTH_SHORT).show();

                    // Log history
                    logHistory("retrieved");

                    // FIX #2: Show immediate success message and let user close
                    // When user dismisses dialog, mark as retrieved
                    handler.postDelayed(() -> {
                        if (!hasRetrieved) {
                            showRetrievalReadyDialog();
                        }
                    }, 1000); // 1 second delay for smooth transition
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUnlock.setEnabled(true);
                    Toast.makeText(this, "Failed to unlock: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showRetrievalReadyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Box Unlocked!")
                .setMessage("Please take your food from the box.\n\nTap 'Done' after you've collected your food.")
                .setPositiveButton("Done", (dialog, which) -> {
                    // User confirms they've taken food - set to available
                    firebaseHelper.getDatabaseReference()
                            .child("boxes")
                            .child(boxId)
                            .child("status")
                            .setValue("available")
                            .addOnSuccessListener(unused -> {
                                hasRetrieved = true;
                                showRetrievalSuccess();
                            });
                })
                .setCancelable(false)
                .show();
    }

    private void showRetrievalSuccess() {
        // Send notification
        notificationHelper.notifyFoodRetrieved(boxNumber);

        new AlertDialog.Builder(this)
                .setTitle("✓ Food Retrieved!")
                .setMessage("Enjoy your meal! 😊\n\nThe box is now available for your next delivery.")
                .setPositiveButton("Done", (dialog, which) -> finish())
                .setCancelable(false)
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
        if (isUnlocked && !hasRetrieved) {
            new AlertDialog.Builder(this)
                    .setTitle("Leave?")
                    .setMessage("The box is unlocked. Are you sure you want to leave?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Re-lock the box if user leaves without retrieving
                        firebaseHelper.getDatabaseReference()
                                .child("boxes")
                                .child(boxId)
                                .child("status")
                                .setValue("occupied"); // Return to occupied state
                        super.onBackPressed();
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}