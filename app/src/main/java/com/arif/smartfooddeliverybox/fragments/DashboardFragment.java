package com.arif.smartfooddeliverybox.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.RetrieveFoodActivity;
import com.arif.smartfooddeliverybox.UnlockForDeliveryActivity;
import com.arif.smartfooddeliverybox.adapters.BoxAdapter;
import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment implements BoxAdapter.OnBoxClickListener {

    private RecyclerView recyclerViewBoxes;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyState, tvAvailableCount, tvOccupiedCount, tvTotalDeliveries, tvLastUpdated, tvAppTitle;
    private MaterialCardView cardStatistics;
    private FloatingActionButton fabAddDelivery;

    private BoxAdapter boxAdapter;
    private FirebaseHelper firebaseHelper;
    private List<DeliveryBox> boxList;
    private ValueEventListener boxesListener;

    private int availableCount = 0;
    private int occupiedCount = 0;
    private int totalDeliveries = 0;
    private boolean offlineWarningShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        firebaseHelper = FirebaseHelper.getInstance();
        boxList = new ArrayList<>();

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupFab();
        loadBoxes();

        return view;
    }

    private void initViews(View view) {
        recyclerViewBoxes = view.findViewById(R.id.recyclerViewBoxes);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        tvAvailableCount = view.findViewById(R.id.tvAvailableCount);
        tvOccupiedCount = view.findViewById(R.id.tvOccupiedCount);
        tvTotalDeliveries = view.findViewById(R.id.tvTotalDeliveries);
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        cardStatistics = view.findViewById(R.id.cardStatistics);
        fabAddDelivery = view.findViewById(R.id.fabAddDelivery);
        tvAppTitle = view.findViewById(R.id.tvAppTitle);

        setupAdminMenu();
    }

    private void setupRecyclerView() {
        boxAdapter = new BoxAdapter(getContext(), boxList, this);
        recyclerViewBoxes.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewBoxes.setAdapter(boxAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadBoxes);
    }

    // POINT 4: Fixed Smart Plus Button
    private void setupFab() {
        if (fabAddDelivery != null) {
            fabAddDelivery.setOnClickListener(v -> {
                DeliveryBox bestBox = null;
                DeliveryBox offlineBox = null;

                // Loop to find the best candidate
                for (DeliveryBox box : boxList) {
                    if (box.isAvailable() && box.isEnabled()) {
                        // Check logic: If it's physical, it should be online.
                        // But if it's the only one we have, we might accept it (handled below).
                        boolean isOnline = !box.isPhysical() || box.isOnline();

                        if (isOnline) {
                            bestBox = box;
                            break; // Found an Online & Available box! Priority #1.
                        } else if (offlineBox == null) {
                            offlineBox = box; // Found an Offline & Available box. Priority #2.
                        }
                    }
                }

                if (bestBox != null) {
                    // Priority 1: Launch immediately
                    launchUnlockActivity(bestBox);
                } else if (offlineBox != null) {
                    // Priority 2: Show offline warning (User can choose to proceed)
                    showOfflineDialog(offlineBox);
                } else {
                    // No boxes available at all
                    Toast.makeText(getContext(), "No boxes available. Please wait for a slot.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadBoxes() {
        swipeRefresh.setRefreshing(true);

        if (boxesListener != null) {
            firebaseHelper.getDatabaseReference().child("boxes").removeEventListener(boxesListener);
        }

        boxesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boxList.clear();
                availableCount = 0;
                occupiedCount = 0;
                totalDeliveries = 0;

                for (DataSnapshot boxSnapshot : snapshot.getChildren()) {
                    DeliveryBox box = boxSnapshot.getValue(DeliveryBox.class);

                    if (box != null && box.isEnabled()) {
                        box.setBoxId(boxSnapshot.getKey());
                        boxList.add(box);

                        if (box.isAvailable()) {
                            availableCount++;
                        } else if ("occupied".equals(box.getStatus())) {
                            occupiedCount++;
                        }

                        if (box.getStatistics() != null) {
                            totalDeliveries += box.getStatistics().getTotalDeliveries();
                        }
                    }
                }

                updateStatistics();
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                if (tvLastUpdated != null) {
                    tvLastUpdated.setText("Updated: " + sdf.format(new Date()));
                }

                boxAdapter.notifyDataSetChanged();
                updateEmptyState();
                swipeRefresh.setRefreshing(false);
                checkOfflineBoxes();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                if (getContext() != null) Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        firebaseHelper.getDatabaseReference().child("boxes").addValueEventListener(boxesListener);
    }

    private void updateStatistics() {
        if (tvAvailableCount != null) tvAvailableCount.setText(availableCount + " Available");
        if (tvOccupiedCount != null) tvOccupiedCount.setText(occupiedCount + " Occupied");
        if (tvTotalDeliveries != null) tvTotalDeliveries.setText("Total: " + totalDeliveries + " deliveries");
    }

    private void updateEmptyState() {
        boolean isEmpty = boxList.isEmpty();
        if (tvEmptyState != null) tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerViewBoxes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (cardStatistics != null) cardStatistics.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void checkOfflineBoxes() {
        if (offlineWarningShown) return;
        for (DeliveryBox box : boxList) {
            if (box.isPhysical() && !box.isOnline()) {
                offlineWarningShown = true;
                Toast.makeText(getContext(), "⚠️ " + box.getName() + " is currently OFFLINE", Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    @Override
    public void onBoxClick(DeliveryBox box) {
        if (box == null || getContext() == null) return;

        // 1. Check Offline
        if (box.isPhysical() && !box.isOnline()) {
            showOfflineDialog(box);
            return;
        }

        String status = box.getStatus();
        String currentUserId = firebaseHelper.getCurrentUserId();

        // POINT 1 & 3: Handle Access Control & Re-entry
        if ("available".equals(status)) {
            launchUnlockActivity(box);
        }
        else if ("occupied".equals(status)) {
            launchRetrieveActivity(box);
        }
        else if ("unlocked_delivery".equals(status)) {
            if (currentUserId != null && currentUserId.equals(box.getUnlockedBy())) {
                Toast.makeText(getContext(), "Resuming your delivery session...", Toast.LENGTH_SHORT).show();
                launchUnlockActivity(box);
            } else {
                Toast.makeText(getContext(), "⛔ Box is currently waiting for a rider (User: " + getSafeUid(box.getUnlockedBy()) + ")", Toast.LENGTH_SHORT).show();
            }
        }
        else if ("unlocked_retrieval".equals(status)) {
            if (currentUserId != null && currentUserId.equals(box.getUnlockedBy())) {
                launchRetrieveActivity(box);
            } else {
                Toast.makeText(getContext(), "⛔ Box is currently being retrieved", Toast.LENGTH_SHORT).show();
            }
        }
        else {
            Toast.makeText(getContext(), "Status: " + box.getStatusText(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getSafeUid(String uid) {
        if(uid == null) return "Unknown";
        return uid.substring(0, Math.min(uid.length(), 5)) + "...";
    }

    private void launchUnlockActivity(DeliveryBox box) {
        Intent intent = new Intent(getContext(), UnlockForDeliveryActivity.class);
        intent.putExtra("boxId", box.getBoxId());
        intent.putExtra("boxNumber", box.getBoxNumber());
        intent.putExtra("boxLocation", box.getName());
        startActivity(intent);
    }

    private void launchRetrieveActivity(DeliveryBox box) {
        Intent intent = new Intent(getContext(), RetrieveFoodActivity.class);
        intent.putExtra("boxId", box.getBoxId());
        intent.putExtra("boxNumber", box.getBoxNumber());
        intent.putExtra("boxLocation", box.getName());
        startActivity(intent);
    }

    private void showOfflineDialog(DeliveryBox box) {
        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Box Offline")
                .setMessage(box.getName() + " is offline.\nCheck power or WiFi.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Debug", (d, w) -> launchUnlockActivity(box))
                .show();
    }

    // --- Admin Menu Logic ---
    private void setupAdminMenu() {
        if (tvAppTitle != null) {
            tvAppTitle.setOnLongClickListener(v -> {
                showAdminDialog();
                return true;
            });
        }
    }

    private void showAdminDialog() {
        String[] options = {"Force Reset All Boxes", "Cancel"};
        new AlertDialog.Builder(requireContext())
                .setTitle("🛠️ Admin Maintenance")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        for(DeliveryBox box : boxList) forceBoxStatus(box, "available");
                    }
                })
                .show();
    }

    private void forceBoxStatus(DeliveryBox box, String status) {
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(box.getBoxId())
                .child("status")
                .setValue(status)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Status updated", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (boxesListener != null) {
            firebaseHelper.getDatabaseReference().child("boxes").removeEventListener(boxesListener);
        }
    }
}