package com.arif.smartfooddeliverybox;

import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.*;

import java.util.concurrent.Executor;

public class UnlockForDeliveryActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvBoxNumber, tvBoxLocation, tvBoxStatus, tvStatusMessage, tvInstructions;
    private MaterialButton btnUnlock, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private NotificationHelper notificationHelper;

    private String boxId, boxNumber, boxLocation, currentUserId;
    private boolean unlockCommitted = false; // Tracks if we successfully unlocked the box

    private ValueEventListener boxListener;

    // Biometrics
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock_for_delivery);

        firebaseHelper = FirebaseHelper.getInstance();
        notificationHelper = new NotificationHelper(this);
        currentUserId = firebaseHelper.getCurrentUserId();

        boxId = getIntent().getStringExtra("boxId");
        boxNumber = getIntent().getStringExtra("boxNumber");
        boxLocation = getIntent().getStringExtra("boxLocation");

        initViews();
        setupToolbar();
        setupBiometrics();
        setupListeners();
        updateInitialUI();
        startMonitoring();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
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
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        btnUnlock.setOnClickListener(v -> confirmUnlock());
        // POINT 2: Confirmation on Cancel Button
        btnCancel.setOnClickListener(v -> showCancelConfirmation());
    }

    private void showCancelConfirmation() {
        if (unlockCommitted) {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel Delivery?")
                    .setMessage("The box is currently UNLOCKED. Canceling will re-lock the box and mark it as Available.\n\nAre you sure?")
                    .setPositiveButton("Yes, Cancel", (dialog, which) -> cancelUnlock())
                    .setNegativeButton("No", null)
                    .show();
        } else {
            finish(); // Just close if we haven't done anything yet
        }
    }

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        unlockBoxSafely();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Unlock Box " + boxNumber)
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void confirmUnlock() {
        if (!isOnline()) {
            showError("No internet connection");
            return;
        }

        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            unlockBoxSafely();
        }
    }

    private void unlockBoxSafely() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        DatabaseReference boxRef = firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId);

        boxRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                DeliveryBox box = currentData.getValue(DeliveryBox.class);
                if (box == null) return Transaction.abort();

                // If already unlocked by ME, allow re-entry (idempotency)
                if ("unlocked_delivery".equals(box.getStatus()) && currentUserId.equals(box.getUnlockedBy())) {
                    return Transaction.success(currentData);
                }

                if (!"available".equals(box.getStatus())) return Transaction.abort();

                box.setStatus("unlocked_delivery");
                box.setUnlockedBy(currentUserId);
                box.setUnlockedAt(System.currentTimeMillis());

                currentData.setValue(box);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                if (!committed) {
                    // Check if it failed because *I* already own it (re-entry)
                    DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                    if (box != null && "unlocked_delivery".equals(box.getStatus()) && currentUserId.equals(box.getUnlockedBy())) {
                        // All good, I'm just re-opening the screen
                        unlockCommitted = true;
                        updateUiForUnlockedState();
                        return;
                    }

                    showError("Box already in use or unavailable");
                    btnUnlock.setEnabled(true);
                    return;
                }

                unlockCommitted = true;
                updateUiForUnlockedState();
                logHistory("unlocked");
                notificationHelper.notifyBoxUnlocked(boxNumber);
            }
        });
    }

    private void updateUiForUnlockedState() {
        tvBoxStatus.setText("Waiting for Rider");
        tvBoxStatus.setTextColor(getColor(R.color.status_warning));
        tvStatusMessage.setText("Box Unlocked. Monitoring sensors...");
        tvInstructions.setText("The box is unlocked. Tell rider to place food inside.");
        btnUnlock.setEnabled(false);
        btnUnlock.setText("Unlocked");
    }

    private void startMonitoring() {
        boxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                if (box == null) return;

                // Re-sync UI state if I come back to screen
                if ("unlocked_delivery".equals(box.getStatus()) && currentUserId.equals(box.getUnlockedBy())) {
                    unlockCommitted = true;
                    updateUiForUnlockedState();
                }

                if ("unlocked_delivery".equals(box.getStatus())
                        && !currentUserId.equals(box.getUnlockedBy())) {
                    showError("This box was taken by another user");
                    finish();
                }

                if ("occupied".equals(box.getStatus()) && unlockCommitted) {
                    logHistory("food_stored");
                    notificationHelper.notifyFoodDelivered(boxNumber);
                    showDoneDialog();
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .addValueEventListener(boxListener);
    }

    private void cancelUnlock() {
        // Safe cancel: Transaction ensures we don't accidentally unlock someone else's box
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData data) {
                        DeliveryBox box = data.getValue(DeliveryBox.class);
                        if (box == null) return Transaction.abort();

                        if (!currentUserId.equals(box.getUnlockedBy()))
                            return Transaction.abort();

                        box.setStatus("available");
                        box.setUnlockedBy(null);
                        box.setUnlockedAt(0);
                        data.setValue(box);
                        return Transaction.success(data);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                        finish();
                    }
                });
    }

    private void updateInitialUI() {
        tvBoxNumber.setText("Box " + boxNumber);
        tvBoxLocation.setText(boxLocation);
        tvBoxStatus.setText("Available");
        tvStatusMessage.setText("Ready for delivery");
        tvInstructions.setText("Ask rider to place food inside Box " + boxNumber);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }

    private void logHistory(String action) {
        String uid = firebaseHelper.getCurrentUserId();
        if (uid == null) return;

        DatabaseReference ref = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(uid)
                .push();

        ref.child("boxNumber").setValue(boxNumber);
        ref.child("action").setValue(action);
        ref.child("timestamp").setValue(System.currentTimeMillis());
    }

    private void showDoneDialog() {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("Delivery Completed")
                    .setMessage("Food stored successfully. Box is now locked.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }

    private void showError(String msg) {
        if (!isFinishing()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (boxListener != null) {
            firebaseHelper.getDatabaseReference()
                    .child("boxes")
                    .child(boxId)
                    .removeEventListener(boxListener);
        }
    }

    // POINT 2: Handle Back Button
    @Override
    public void onBackPressed() {
        if (unlockCommitted) {
            showCancelConfirmation();
        } else {
            super.onBackPressed();
        }
    }
}