package com.arif.smartfooddeliverybox;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.arif.smartfooddeliverybox.utils.NotificationHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class UnlockForDeliveryActivity extends BaseInsetActivity {

    private MaterialToolbar toolbar;
    private TextView tvBoxNumber, tvBoxLocation, tvBoxStatus, tvStatusMessage, tvInstructions;
    private MaterialButton btnUnlock, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private NotificationHelper notificationHelper;

    private String boxId, boxNumber, boxLocation, currentUserId;
    private boolean unlockCommitted = false;

    private ValueEventListener boxListener;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    // ✅ guards (avoid repeated calls while transaction is running)
    private boolean deliveryHandled = false;
    private boolean doneDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock_for_delivery);

        applyStatusBarInset();

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
            finish();
        }
    }

    private void clearReminderCooldownForThisBox() {
        if (boxId == null) return;
        getSharedPreferences("food_reminder_prefs", MODE_PRIVATE)
                .edit()
                .remove("last_reminder_" + boxId)
                .apply();
    }

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        unlockBoxSafely();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        Toast.makeText(UnlockForDeliveryActivity.this, errString, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Toast.makeText(UnlockForDeliveryActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Unlock Box " + (boxNumber == null ? "" : boxNumber))
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void confirmUnlock() {
        if (!isOnlineInternet()) {
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
        if (boxId == null || boxId.trim().isEmpty()) {
            showError("Invalid box");
            return;
        }

        progressBar.setVisibility(android.view.View.VISIBLE);
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

                if (box.isPhysical() && !box.isOnline()) {
                    return Transaction.abort();
                }

                String uid = currentUserId;

                String status = box.getStatus();
                if (status != null && status.equalsIgnoreCase("unlocked_delivery")
                        && uid != null && uid.equals(box.getUnlockedBy())) {
                    return Transaction.success(currentData);
                }

                if (status == null) return Transaction.abort();
                if (!status.equalsIgnoreCase("available") && !status.equalsIgnoreCase("idle")) {
                    return Transaction.abort();
                }

                box.setStatus("unlocked_delivery");
                box.setUnlockedBy(uid);
                box.setUnlockedAt(System.currentTimeMillis());
                box.setDeliveredAt(0);

                currentData.setValue(box);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                progressBar.setVisibility(android.view.View.GONE);

                if (!committed) {
                    DeliveryBox box = snapshot.getValue(DeliveryBox.class);

                    if (box != null && box.isPhysical() && !box.isOnline()) {
                        showError("Box is offline. Cannot unlock.");
                    } else {
                        showError("Box already in use or unavailable");
                    }

                    btnUnlock.setEnabled(true);
                    return;
                }

                unlockCommitted = true;
                // reset delivery handled flags for this session
                deliveryHandled = false;
                doneDialogShown = false;

                updateUiForUnlockedState();
                logHistory("unlocked_for_delivery");
                notificationHelper.notifyBoxUnlocked(boxNumber);
            }
        });
    }

    private void updateInitialUI() {
        tvBoxNumber.setText("Box " + (boxNumber == null ? "" : boxNumber));
        tvBoxLocation.setText(boxLocation == null ? "" : boxLocation);

        tvBoxStatus.setText("Available");
        tvStatusMessage.setText("Ready for delivery");
        tvInstructions.setText("Ask rider to place food inside Box " + (boxNumber == null ? "" : boxNumber));

        btnUnlock.setEnabled(true);
        btnUnlock.setText("Unlock Box");
    }

    private void updateUiForUnlockedState() {
        tvBoxStatus.setText("Waiting for Rider");
        tvStatusMessage.setText("Box Unlocked. Monitoring sensors...");
        tvInstructions.setText("The box is unlocked. Tell rider to place food inside.");
        btnUnlock.setEnabled(false);
        btnUnlock.setText("Unlocked");
    }

    private void startMonitoring() {
        if (boxId == null) return;

        boxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                if (box == null) return;

                String status = box.getStatus() == null ? "" : box.getStatus().toLowerCase();
                String owner = box.getUnlockedBy();

                if (status.equals("unlocked_delivery")
                        && currentUserId != null
                        && currentUserId.equals(owner)) {
                    unlockCommitted = true;
                    updateUiForUnlockedState();
                }

                if (status.equals("unlocked_delivery")
                        && currentUserId != null
                        && owner != null
                        && !currentUserId.equals(owner)) {
                    showError("This box was taken by another user");
                    finish();
                    return;
                }

                // ✅ Delivery completed: status becomes occupied
                if (status.equals("occupied") && unlockCommitted) {
                    handleFoodStoredOnce();
                }

                // If box becomes available again, reset state
                if ((status.equals("available") || status.equals("idle")) && unlockCommitted) {
                    unlockCommitted = false;
                    deliveryHandled = false;
                    doneDialogShown = false;
                    updateInitialUI();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // optional
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .addValueEventListener(boxListener);
    }

    /**
     * ✅ The bulletproof "only once" method:
     * Use a transaction on deliveredAt so only the first caller wins.
     */
    private void handleFoodStoredOnce() {
        if (deliveryHandled) {
            // If already handled, just ensure dialog shown once
            if (!doneDialogShown) {
                doneDialogShown = true;
                showDoneDialog();
            }
            return;
        }

        deliveryHandled = true;

        DatabaseReference deliveredAtRef = firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("deliveredAt");

        deliveredAtRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long existing = currentData.getValue(Long.class);

                // if already set, abort (means already logged before)
                if (existing != null && existing != 0) {
                    return Transaction.abort();
                }

                currentData.setValue(System.currentTimeMillis());
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                // If we lost the race (already marked), do not log again
                if (!committed) {
                    if (!doneDialogShown) {
                        doneDialogShown = true;
                        showDoneDialog();
                    }
                    return;
                }

                // ✅ First time only
                logHistory("food_stored");
                notificationHelper.notifyFoodDelivered(boxNumber);

                if (!doneDialogShown) {
                    doneDialogShown = true;
                    showDoneDialog();
                }
            }
        });
    }

    private void cancelUnlock() {
        if (boxId == null) return;

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData data) {
                        DeliveryBox box = data.getValue(DeliveryBox.class);
                        if (box == null) return Transaction.abort();

                        String uid = currentUserId;
                        if (uid == null) return Transaction.abort();
                        if (box.getUnlockedBy() == null) return Transaction.abort();
                        if (!uid.equals(box.getUnlockedBy())) return Transaction.abort();

                        String status = box.getStatus();
                        if (status == null || !status.equalsIgnoreCase("unlocked_delivery")) {
                            return Transaction.abort();
                        }

                        box.setStatus("available");
                        box.setUnlockedBy(null);
                        box.setUnlockedAt(0);

                        data.setValue(box);
                        return Transaction.success(data);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                        if (committed) {
                            // ✅ log cancel so History session can close
                            logHistory("cancelled");
                            clearReminderCooldownForThisBox();
                        }
                        finish();
                    }
                });
    }


    private void logHistory(String action) {
        String uid = firebaseHelper.getCurrentUserId();
        if (uid == null) return;

        DatabaseReference ref = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(uid)
                .push();

        Map<String, Object> data = new HashMap<>();
        data.put("boxNumber", boxNumber);
        data.put("action", action);
        data.put("timestamp", ServerValue.TIMESTAMP); // ✅ consistent server time

        ref.updateChildren(data);
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

    private boolean isOnlineInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo net = cm != null ? cm.getActiveNetworkInfo() : null;
        return net != null && net.isConnected();
    }

    private void showError(String msg) {
        if (!isFinishing()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
    }

    @Override
    public void onBackPressed() {
        if (unlockCommitted) {
            showCancelConfirmation();
        } else {
            super.onBackPressed();
        }
    }
}
