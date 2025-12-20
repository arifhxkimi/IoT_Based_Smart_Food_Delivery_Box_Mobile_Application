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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.RetrieveFoodActivity;
import com.arif.smartfooddeliverybox.UnlockForDeliveryActivity;
import com.arif.smartfooddeliverybox.adapters.BoxAdapter;
import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends Fragment implements BoxAdapter.OnBoxClickListener {

    private RecyclerView recyclerViewBoxes;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyState, tvAvailableCount, tvOccupiedCount, tvTotalDeliveries;
    private MaterialCardView cardStatistics;
    private FloatingActionButton fabAddDelivery;
    private BoxAdapter boxAdapter;
    private FirebaseHelper firebaseHelper;
    private List<DeliveryBox> boxList;
    private ValueEventListener boxesListener;

    // Statistics
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
        cardStatistics = view.findViewById(R.id.cardStatistics);
        fabAddDelivery = view.findViewById(R.id.fabAddDelivery);
    }

    private void setupRecyclerView() {
        boxAdapter = new BoxAdapter(getContext(), boxList, this);
        recyclerViewBoxes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerViewBoxes.setAdapter(boxAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadBoxes);
    }

    private void setupFab() {
        if (fabAddDelivery != null) {
            fabAddDelivery.setOnClickListener(v -> {
                // Find first available box
                DeliveryBox availableBox = null;
                for (DeliveryBox box : boxList) {
                    if (box.isAvailable() && box.isEnabled() && box.isOnline()) {
                        availableBox = box;
                        break;
                    }
                }

                if (availableBox != null) {
                    Intent intent = new Intent(getActivity(), UnlockForDeliveryActivity.class);
                    intent.putExtra("boxId", availableBox.getBoxId());
                    intent.putExtra("boxNumber", availableBox.getBoxNumber());
                    intent.putExtra("boxLocation", "Box " + availableBox.getBoxNumber());
                    startActivity(intent);
                } else {
                    Toast.makeText(getContext(), "No boxes available", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadBoxes() {
        swipeRefresh.setRefreshing(true);

        if (boxesListener != null) {
            firebaseHelper.getDatabaseReference()
                    .child("boxes")
                    .removeEventListener(boxesListener);
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

                        if ("available".equals(box.getStatus())) {
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
                boxAdapter.notifyDataSetChanged();
                updateEmptyState();
                swipeRefresh.setRefreshing(false);
                checkOfflineBoxes();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(),
                        "Error loading boxes: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // ✅ REAL-TIME LISTENER
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .addValueEventListener(boxesListener);
    }


    private void updateStatistics() {
        if (tvAvailableCount != null) {
            tvAvailableCount.setText(availableCount + " Available");
        }
        if (tvOccupiedCount != null) {
            tvOccupiedCount.setText(occupiedCount + " Occupied");
        }
        if (tvTotalDeliveries != null) {
            tvTotalDeliveries.setText("Total: " + totalDeliveries + " deliveries");
        }
    }

    private void updateEmptyState() {
        if (boxList.isEmpty()) {
            if (tvEmptyState != null) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("No boxes available.\nPlease contact administrator.");
            }
            recyclerViewBoxes.setVisibility(View.GONE);
            if (cardStatistics != null) {
                cardStatistics.setVisibility(View.GONE);
            }
        } else {
            if (tvEmptyState != null) {
                tvEmptyState.setVisibility(View.GONE);
            }
            recyclerViewBoxes.setVisibility(View.VISIBLE);
            if (cardStatistics != null) {
                cardStatistics.setVisibility(View.VISIBLE);
            }
        }
    }

    private void checkOfflineBoxes() {
        if (offlineWarningShown) return;

        for (DeliveryBox box : boxList) {
            if (box.isPhysical() && !box.isOnline()) {
                offlineWarningShown = true;
                Toast.makeText(getContext(),
                        "⚠️ Box " + box.getBoxNumber() + " is offline",
                        Toast.LENGTH_LONG).show();
                break;
            }
        }
    }


    @Override
    public void onBoxClick(DeliveryBox box) {
        if (box == null || getContext() == null) return;

        if (!box.isEnabled()) {
            Toast.makeText(getContext(),
                    "This box is disabled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if box is online
        if (box.isPhysical() && !box.isOnline()) {
            showOfflineDialog(box);
            return;
        }

        String status = box.getStatus();

        if ("available".equals(status)) {
            // Unlock for delivery
            Intent intent = new Intent(getContext(), UnlockForDeliveryActivity.class);
            intent.putExtra("boxId", box.getBoxId());
            intent.putExtra("boxNumber", box.getBoxNumber());
            intent.putExtra("boxLocation", "Box " + box.getBoxNumber());
            startActivity(intent);
        }
        else if ("occupied".equals(status)) {
            // Retrieve food
            Intent intent = new Intent(getContext(), RetrieveFoodActivity.class);
            intent.putExtra("boxId", box.getBoxId());
            intent.putExtra("boxNumber", box.getBoxNumber());
            intent.putExtra("boxLocation", "Box " + box.getBoxNumber());
            startActivity(intent);
        }
        else if ("unlocked".equals(status)) {
            Toast.makeText(getContext(),
                    "Box " + box.getBoxNumber() + " is currently unlocked",
                    Toast.LENGTH_SHORT).show();
            showManualOverrideDialog(box);
        }
        else {
            Toast.makeText(getContext(),
                    "Box " + box.getBoxNumber() + " - " + box.getStatusText(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflineDialog(DeliveryBox box) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Box Offline")
                .setMessage("Box " + box.getBoxNumber() + " is currently offline.\n\n" +
                        "The IoT device may be:\n" +
                        "• Powered off\n" +
                        "• Disconnected from WiFi\n" +
                        "• Experiencing technical issues")
                .setPositiveButton("OK", null)
                .setNeutralButton("Try Anyway", (dialog, which) -> {
                    // Allow user to try anyway
                    Intent intent = new Intent(getContext(), UnlockForDeliveryActivity.class);
                    intent.putExtra("boxId", box.getBoxId());
                    intent.putExtra("boxNumber", box.getBoxNumber());
                    intent.putExtra("boxLocation", "Box " + box.getBoxNumber());
                    startActivity(intent);
                })
                .show();
    }

    private void showManualOverrideDialog(DeliveryBox box) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Manual Override")
                .setMessage("Box " + box.getBoxNumber() + " is unlocked.\n\nWhat would you like to do?")
                .setPositiveButton("Force Lock", (dialog, which) -> {
                    forceBoxStatus(box, "available");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void forceBoxStatus(DeliveryBox box, String status) {
        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .child(box.getBoxId())
                .child("status")
                .setValue(status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(),
                            "Box " + box.getBoxNumber() + " force locked",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            "Failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (boxesListener != null && firebaseHelper != null) {
            firebaseHelper.getDatabaseReference()
                    .child("boxes")
                    .removeEventListener(boxesListener);
        }
    }
}