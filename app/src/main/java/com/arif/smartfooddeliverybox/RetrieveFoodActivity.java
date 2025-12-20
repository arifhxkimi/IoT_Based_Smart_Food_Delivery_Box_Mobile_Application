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
            getSupportActionBar().setTitle("Retrieve Food");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
        tvBoxStatus.setText("Occupied");
        tvStatusMessage.setText("Your food is ready!");
        tvInstructions.setText("Tap Unlock to retrieve your food.");
    }

    private void startBoxMonitoring() {
        boxListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DeliveryBox box = snapshot.getValue(DeliveryBox.class);
                if (box != null) {
                    handleBoxStatusChange(box);
                }
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

    private void handleBoxStatusChange(DeliveryBox box) {
        tvBoxStatus.setText(box.getStatusText());

        if ("unlocked".equals(box.getStatus()) && isUnlocked && !hasRetrieved) {
            btnUnlock.setEnabled(false);
            tvStatusMessage.setText("Box unlocked");
            tvInstructions.setText("Take your food and tap Done.");
        }
    }

    private void confirmUnlock() {
        new AlertDialog.Builder(this)
                .setTitle("Unlock Box")
                .setMessage("Unlock Box " + boxNumber + "?")
                .setPositiveButton("Unlock", (d, w) -> unlockBox())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unlockBox() {
        progressBar.setVisibility(View.VISIBLE);
        btnUnlock.setEnabled(false);

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("status")
                .setValue("unlocked")
                .addOnSuccessListener(v -> {
                    isUnlocked = true;
                    progressBar.setVisibility(View.GONE);
                    showRetrieveDialog();
                });
    }

    private void showRetrieveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Box Unlocked")
                .setMessage("Take your food and tap Done.")
                .setPositiveButton("Done", (d, w) -> completeRetrieval())
                .setCancelable(false)
                .show();
    }

    private void completeRetrieval() {
        hasRetrieved = true;

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("status")
                .setValue("available");

        incrementUserDeliveries();   // ✅ per account
        incrementBoxDeliveries();    // ✅ optional
        logHistory("retrieved");

        notificationHelper.notifyFoodRetrieved(boxNumber);

        new AlertDialog.Builder(this)
                .setTitle("Success")
                .setMessage("Food retrieved successfully!")
                .setPositiveButton("OK", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    /* ================= STATISTICS ================= */

    private void incrementUserDeliveries() {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        firebaseHelper.getDatabaseReference()
                .child("users")
                .child(userId)
                .child("statistics")
                .child("totalDeliveries")
                .get()
                .addOnSuccessListener(snap -> {
                    int current = snap.exists() ? snap.getValue(Integer.class) : 0;
                    firebaseHelper.getDatabaseReference()
                            .child("users")
                            .child(userId)
                            .child("statistics")
                            .child("totalDeliveries")
                            .setValue(current + 1);
                });
    }

    private void incrementBoxDeliveries() {
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(boxId)
                .child("statistics")
                .child("totalDeliveries")
                .get()
                .addOnSuccessListener(snap -> {
                    int current = snap.exists() ? snap.getValue(Integer.class) : 0;
                    firebaseHelper.getDatabaseReference()
                            .child("boxes")
                            .child(boxId)
                            .child("statistics")
                            .child("totalDeliveries")
                            .setValue(current + 1);
                });
    }

    private void logHistory(String action) {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        String key = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(userId)
                .push()
                .getKey();

        if (key != null) {
            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(key)
                    .child("boxNumber")
                    .setValue(boxNumber);

            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(key)
                    .child("action")
                    .setValue(action);

            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(userId)
                    .child(key)
                    .child("timestamp")
                    .setValue(System.currentTimeMillis());
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
        handler.removeCallbacksAndMessages(null);
    }
}
