package com.arif.smartfooddeliverybox;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

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

public class RetrieveFoodActivity extends BaseInsetActivity {

    private MaterialToolbar toolbar;
    private TextView tvBoxNumber, tvBoxLocation, tvBoxStatus;
    private TextView tvStatusMessage, tvInstructions;
    private MaterialButton btnUnlock, btnCancel;
    private ProgressBar progressBar;

    private FirebaseHelper firebaseHelper;
    private NotificationHelper notificationHelper;

    private String boxId;
    private String boxNumber;
    private String boxLocation;

    private String currentUserId;

    private ValueEventListener boxListener;

    private boolean unlockCommitted = false;
    private boolean hasRetrieved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrieve_food);

        applyStatusBarInset();

        boxId = getIntent().getStringExtra("boxId");
        boxNumber = getIntent().getStringExtra("boxNumber");
        boxLocation = getIntent().getStringExtra("boxLocation");

        if (boxId == null) {
            Toast.makeText(this, "Box data missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        firebaseHelper = FirebaseHelper.getInstance();
        notificationHelper = new NotificationHelper(this);
        currentUserId = firebaseHelper.getCurrentUserId();

        if (currentUserId == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupListeners();
        updateInitialUI();
        startBoxMonitoring();
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
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Retrieve Food");
        }
    }

    private void setupListeners() {
        btnUnlock.setOnClickListener(v -> confirmUnlock());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void updateInitialUI() {
        tvBoxNumber.setText("Box " + (boxNumber == null ? "" : boxNumber));
        tvBoxLocation.setText(boxLocation == null ? "" : boxLocation);
        tvBoxStatus.setText("Checking...");
        tvStatusMessage.setText("Checking box status...");
        tvInstructions.setText("Please wait");
    }

    // ---------------- MONITOR ----------------

    private void startBoxMonitoring() {
        boxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                if (box == null) return;

                // ✅ HARD SECURITY: block if not owner when box is in-use/occupied
                if (!isOwnerAllowed(box)) {
                    Toast.makeText(RetrieveFoodActivity.this,
                            "This box belongs to another user.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                handleBoxStatusChange(box);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RetrieveFoodActivity.this,
                        "Failed to monitor box", Toast.LENGTH_SHORT).show();
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .addValueEventListener(boxListener);
    }

    /**
     * ✅ Owner rule:
     * - If status is occupied/unlocked_for_retrieval/retrieval_in_progress -> must have unlockedBy == currentUserId
     * - If unlockedBy is null while occupied -> treat as NOT allowed (better safe)
     */
    private boolean isOwnerAllowed(DeliveryBox box) {
        String st = box.getStatus() == null ? "" : box.getStatus().toLowerCase();
        String owner = box.getUnlockedBy();

        boolean inUse = st.equals("occupied")
                || st.equals("unlocked_for_retrieval")
                || st.equals("unlocked_retrieval")
                || st.equals("retrieval_in_progress");

        if (!inUse) return true;

        return owner != null && owner.equals(currentUserId);
    }

    private void handleBoxStatusChange(DeliveryBox box) {
        tvBoxStatus.setText(box.getStatusText());

        String st = box.getStatus() == null ? "" : box.getStatus().toLowerCase();

        if (st.equals("occupied")) {
            tvStatusMessage.setText("Your food is ready!");
            tvInstructions.setText("Tap Unlock to retrieve your food.");
            btnUnlock.setEnabled(true);
            btnUnlock.setText("Unlock");
            return;
        }

        if (st.equals("unlocked_for_retrieval") || st.equals("unlocked_retrieval")) {
            unlockCommitted = true;
            btnUnlock.setEnabled(false);
            btnUnlock.setText("Unlocked");
            tvStatusMessage.setText("Box unlocked");
            tvInstructions.setText("Take your food, then tap Done.");
            return;
        }

        if (st.equals("available") || st.equals("idle")) {
            // If we already unlocked before, it means retrieval finished
            if (unlockCommitted && !hasRetrieved) {
                hasRetrieved = true;
                Toast.makeText(this, "Box secured.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // ---------------- UNLOCK FOR RETRIEVAL ----------------

    private void confirmUnlock() {
        new AlertDialog.Builder(this)
                .setTitle("Unlock Box")
                .setMessage("Unlock Box " + boxNumber + " to get your food?")
                .setPositiveButton("Unlock", (d, w) -> unlockForRetrievalTransaction())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * ✅ CRITICAL FIX:
     * - ONLY owner (unlockedBy) can unlock for retrieval.
     * - DO NOT overwrite unlockedBy here.
     */
    private void unlockForRetrievalTransaction() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        DatabaseReference boxRef = firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId);

        boxRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                DeliveryBox box = data.getValue(DeliveryBox.class);
                if (box == null) return Transaction.abort();

                String st = box.getStatus() == null ? "" : box.getStatus().toLowerCase();

                // Must be occupied to start retrieval
                if (!st.equals("occupied")
                        && !st.equals("unlocked_for_retrieval")
                        && !st.equals("unlocked_retrieval")) {
                    return Transaction.abort();
                }

                // ✅ OWNER CHECK
                if (box.getUnlockedBy() == null || !currentUserId.equals(box.getUnlockedBy())) {
                    return Transaction.abort();
                }

                // Re-entry: already unlocked for retrieval, keep it
                if (st.equals("unlocked_for_retrieval") || st.equals("unlocked_retrieval")) {
                    return Transaction.success(data);
                }

                // Set retrieval status (keep your naming consistent)
                box.setStatus("unlocked_for_retrieval");

                // ✅ DO NOT overwrite unlockedBy (it’s the owner binding)
                // box.setUnlockedBy(currentUserId);  // ❌ removed

                box.setUnlockedAt(System.currentTimeMillis());

                data.setValue(box);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                if (!committed) {
                    Toast.makeText(RetrieveFoodActivity.this,
                            "Not allowed / box not yours / unavailable",
                            Toast.LENGTH_SHORT).show();
                    btnUnlock.setEnabled(true);
                    return;
                }

                unlockCommitted = true;
                updateBoxLastUsed();

                logHistory("unlocked_for_retrieval");
                showRetrieveDialog();
            }
        });
    }

    private void showRetrieveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Box Unlocked")
                .setMessage("Please take your food. The box will be secured after you tap Done.")
                .setPositiveButton("Done", (d, w) -> completeRetrievalTransaction())
                .setCancelable(false)
                .show();
    }

    /**
     * ✅ Must be transaction:
     * - only owner can reset status
     * - clear owner fields so box becomes free again
     */
    private void completeRetrievalTransaction() {
        if (hasRetrieved) return;
        progressBar.setVisibility(View.VISIBLE);

        DatabaseReference boxRef = firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId);

        boxRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                DeliveryBox box = data.getValue(DeliveryBox.class);
                if (box == null) return Transaction.abort();

                // ✅ OWNER CHECK
                if (box.getUnlockedBy() == null || !currentUserId.equals(box.getUnlockedBy())) {
                    return Transaction.abort();
                }

                String st = box.getStatus() == null ? "" : box.getStatus().toLowerCase();

                // Only allow completing if it was occupied/unlocked_for_retrieval
                boolean ok = st.equals("occupied")
                        || st.equals("unlocked_for_retrieval")
                        || st.equals("unlocked_retrieval")
                        || st.equals("retrieval_in_progress");

                if (!ok) return Transaction.abort();

                box.setStatus("available");
                box.setUnlockedBy(null);
                box.setUnlockedAt(0);
                box.setDeliveredAt(0); // optional

                // statistics lastUsed
                if (box.getStatistics() == null) {
                    DeliveryBox.BoxStatistics stats = new DeliveryBox.BoxStatistics();
                    stats.setLastUsed(0);
                    box.setStatistics(stats);
                }

                // We will set lastUsed via ServerValue in onComplete to avoid mapping issues
                data.setValue(box);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                if (!committed) {
                    Toast.makeText(RetrieveFoodActivity.this,
                            "Failed: not allowed / state changed",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                hasRetrieved = true;

                // lastUsed server timestamp (separate write is fine here)
                firebaseHelper.getDatabaseReference()
                        .child("boxes")
                        .child(boxId)
                        .child("statistics")
                        .child("lastUsed")
                        .setValue(ServerValue.TIMESTAMP);

                logHistory("retrieved");
                notificationHelper.notifyFoodRetrieved(boxNumber);

                new AlertDialog.Builder(RetrieveFoodActivity.this)
                        .setTitle("Success")
                        .setMessage("Food retrieved successfully!")
                        .setPositiveButton("OK", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private void updateBoxLastUsed() {
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("statistics")
                .child("lastUsed")
                .setValue(ServerValue.TIMESTAMP);
    }

    private void logHistory(String action) {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        DatabaseReference ref = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(userId)
                .push();

        ref.child("boxNumber").setValue(boxNumber);
        ref.child("action").setValue(action);

        // Use server time (ok)
        ref.child("timestamp").setValue(ServerValue.TIMESTAMP);
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
}
