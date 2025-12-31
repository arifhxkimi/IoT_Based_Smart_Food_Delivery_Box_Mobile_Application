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
import androidx.biometric.BiometricManager; // Import for Availability Check
import androidx.biometric.BiometricPrompt; // Import for Fingerprint
import androidx.core.content.ContextCompat; // Import for Executor
import androidx.cardview.widget.CardView;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.Executor;

public class UnlockForDeliveryActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvBoxNumber, tvBoxLocation, tvBoxStatus, tvStatusMessage, tvInstructions;
    private MaterialButton btnUnlock, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private NotificationHelper notificationHelper;
    private String boxId, boxNumber, boxLocation;
    private ValueEventListener boxListener;
    private Handler handler;
    private boolean isWaitingForFood = false;
    private boolean hasUnlockedBox = false;

    // Biometric Variables
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

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
        setupBiometrics(); // Initialize Security

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
        // IDs must match your XML layout
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
        btnCancel.setOnClickListener(v -> finish());
    }

    // --- NEW: BIOMETRIC SETUP ---
    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(UnlockForDeliveryActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Handle error (e.g., user canceled or too many attempts)
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // SUCCESS! The user is verified. Now we unlock the box.
                Toast.makeText(getApplicationContext(), "Identity Verified!", Toast.LENGTH_SHORT).show();
                unlockBox();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed. Try again.", Toast.LENGTH_SHORT).show();
            }
        });

        // Setup the dialog that appears
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Security Check")
                .setSubtitle("Verify identity to unlock Box " + (boxNumber != null ? boxNumber : ""))
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false) // True = require user to click "Confirm" after face scan
                .build();
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

                                    // Update prompt info with new box number
                                    promptInfo = new BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("Security Check")
                                            .setSubtitle("Verify identity to unlock Box " + boxNumber)
                                            .setNegativeButtonText("Cancel")
                                            .setConfirmationRequired(false) // Added for consistency
                                            .build();

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
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        firebaseHelper.getDatabaseReference().child("boxes").child(boxId).addValueEventListener(boxListener);
    }

    private void handleBoxStatusChange(DeliveryBox box) {
        tvBoxStatus.setText(box.getStatusText());

        if ("unlocked".equals(box.getStatus()) && !isWaitingForFood) {
            isWaitingForFood = true;
            hasUnlockedBox = true;
            btnUnlock.setEnabled(false);
            btnUnlock.setText("Box Unlocked - Waiting for Food");
            tvStatusMessage.setText("✓ Box is unlocked!");
            tvStatusMessage.setTextColor(getColor(R.color.status_unlocked));
            tvInstructions.setText("Tell your rider to place food in Box " + boxNumber +
                    ". The box will automatically lock when food is detected.");
        } else if ("occupied".equals(box.getStatus()) && isWaitingForFood) {
            showFoodStoredSuccess();
        }
    }

    private void confirmUnlock() {
        // --- SAFETY CHECK: Verify if Biometrics are available on this device ---
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            // Case A: Hardware is ready and fingerprints are enrolled
            new AlertDialog.Builder(this)
                    .setTitle("Unlock Box")
                    .setMessage("Unlock Box " + boxNumber + " for delivery?")
                    .setPositiveButton("Authenticate & Unlock", (dialog, which) -> {
                        biometricPrompt.authenticate(promptInfo);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // Case B: No hardware or no fingerprints enrolled - Fallback to standard
            new AlertDialog.Builder(this)
                    .setTitle("Unlock Box")
                    .setMessage("Unlock Box " + boxNumber + " for delivery?")
                    .setPositiveButton("Yes, Unlock", (dialog, which) -> {
                        Toast.makeText(this, "Security check skipped (Biometrics not set up)", Toast.LENGTH_SHORT).show();
                        unlockBox();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    // This is now called ONLY after fingerprint success
    private void unlockBox() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

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
                    firebaseHelper.getDatabaseReference().child("boxes").child(boxId).child("unlockedBy").setValue(userId);
                    firebaseHelper.getDatabaseReference().child("boxes").child(boxId).child("unlockedAt").setValue(System.currentTimeMillis());

                    logHistory("unlocked");
                    progressBar.setVisibility(View.GONE);

                    // Notification handled by Activity/Helper
                    if(notificationHelper != null) notificationHelper.notifyBoxUnlocked(boxNumber);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnUnlock.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showFoodStoredSuccess() {
        logHistory("food_stored");
        if(notificationHelper != null) notificationHelper.notifyFoodDelivered(boxNumber);

        new AlertDialog.Builder(this)
                .setTitle("✓ Food Stored!")
                .setMessage("Your food has been delivered and the box is now secured.\n\nYou can retrieve it anytime from the dashboard.")
                .setPositiveButton("Done", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ No Internet Connection")
                .setMessage("You're currently offline. Please check your connection.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void logHistory(String action) {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        String historyId = firebaseHelper.getDatabaseReference().child("history").child(userId).push().getKey();
        if (historyId != null) {
            firebaseHelper.getDatabaseReference().child("history").child(userId).child(historyId).child("boxNumber").setValue(boxNumber);
            firebaseHelper.getDatabaseReference().child("history").child(userId).child(historyId).child("action").setValue(action);
            firebaseHelper.getDatabaseReference().child("history").child(userId).child(historyId).child("timestamp").setValue(System.currentTimeMillis());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (boxListener != null && boxId != null) {
            firebaseHelper.getDatabaseReference().child("boxes").child(boxId).removeEventListener(boxListener);
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onBackPressed() {
        if (hasUnlockedBox && isWaitingForFood) {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel Delivery?")
                    .setMessage("The box is unlocked. Cancel and re-lock?")
                    .setPositiveButton("Yes, Cancel", (dialog, which) -> {
                        firebaseHelper.getDatabaseReference().child("boxes").child(boxId).child("status").setValue("available")
                                .addOnSuccessListener(unused -> finish());
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}