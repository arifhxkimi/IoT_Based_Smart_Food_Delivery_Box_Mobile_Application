package com.arif.smartfooddeliverybox.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DashboardFragment extends BaseInsetFragment implements BoxAdapter.OnBoxClickListener {

    private RecyclerView recyclerViewBoxes;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyState, tvAvailableCount, tvOccupiedCount, tvTotalDeliveries, tvLastUpdated, tvAppTitle;
    private View viewStatusDot;
    private MaterialCardView cardStatistics;
    private FloatingActionButton fabAddDelivery;

    private BoxAdapter boxAdapter;
    private FirebaseHelper firebaseHelper;
    private final List<DeliveryBox> boxList = new ArrayList<>();
    private ValueEventListener boxesListener;

    private int emptyCount = 0;
    private int hasFoodCount = 0;

    // Cache so UI updates even if Firebase doesn't change
    private boolean hasAnyPhysical = false;
    private boolean anyPhysicalOnlineCached = false;
    private long latestLastUsed = 0;

    private static final long UI_REFRESH_MS = 5000;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshLiveOfflineFromCachedData();
            uiHandler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    private static final long HEARTBEAT_THRESHOLD_MS = 15_000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // ✅ Fix status bar overlap globally for this fragment
        applyStatusBarInset(view);

        firebaseHelper = FirebaseHelper.getInstance();

        initViews(view);
        setupRecyclerView();
        setupSwipeRefresh();
        setupFab();
        setupAdminMenu();

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
        viewStatusDot = view.findViewById(R.id.viewStatusDot);
        cardStatistics = view.findViewById(R.id.cardStatistics);
        fabAddDelivery = view.findViewById(R.id.fabAddDelivery);
        tvAppTitle = view.findViewById(R.id.tvAppTitle);
    }

    private void setupRecyclerView() {
        boxAdapter = new BoxAdapter(getContext(), boxList, this);
        recyclerViewBoxes.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewBoxes.setAdapter(boxAdapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadBoxes);
    }

    private void setupFab() {
        fabAddDelivery.setOnClickListener(v -> {

            if (hasAnyPhysical && !anyPhysicalOnlineCached) {
                Toast.makeText(getContext(),
                        "Devices offline. Turn on ESP32 + WiFi then refresh.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ pick first available box that is NOT locked to another user
            for (DeliveryBox box : boxList) {
                if (box == null) continue;
                if (!box.isEnabled()) continue;

                if (box.isAvailable() && !isLockedToAnotherUser(box)) {
                    launchUnlockActivity(box);
                    return;
                }
            }

            Toast.makeText(getContext(), "No available boxes at the moment", Toast.LENGTH_SHORT).show();
        });
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
                emptyCount = 0;
                hasFoodCount = 0;

                hasAnyPhysical = false;
                latestLastUsed = 0;

                for (DataSnapshot boxSnapshot : snapshot.getChildren()) {
                    DeliveryBox box = boxSnapshot.getValue(DeliveryBox.class);
                    if (box == null) continue;
                    if (!box.isEnabled()) continue;

                    box.setBoxId(boxSnapshot.getKey());
                    boxList.add(box);

                    String status = box.getStatus();
                    if ("idle".equalsIgnoreCase(status) || "available".equalsIgnoreCase(status)) {
                        emptyCount++;
                    } else if ("occupied".equalsIgnoreCase(status)) {
                        hasFoodCount++;
                    }

                    if (box.isPhysical()) hasAnyPhysical = true;

                    if (box.getStatistics() != null) {
                        long lastUsed = box.getStatistics().getLastUsed();
                        if (lastUsed > latestLastUsed) latestLastUsed = lastUsed;
                    }
                }

                updateCounts();
                updateLastUsed(latestLastUsed);

                refreshLiveOfflineFromCachedData();

                boxAdapter.notifyDataSetChanged();
                updateEmptyState();
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("boxes")
                .addValueEventListener(boxesListener);
    }

    private void refreshLiveOfflineFromCachedData() {
        boolean anyPhysicalOnline = false;

        long now = System.currentTimeMillis();

        for (DeliveryBox box : boxList) {
            if (box == null) continue;
            if (!box.isPhysical()) continue;

            long hb = box.getLastHeartbeat();
            if (hb > 0) {
                long diff = now - hb;
                if (diff < 0) diff = 0;
                if (diff < HEARTBEAT_THRESHOLD_MS) {
                    anyPhysicalOnline = true;
                    break;
                }
            }
        }

        anyPhysicalOnlineCached = anyPhysicalOnline;
        updateDeviceBadge(hasAnyPhysical, anyPhysicalOnline);

        if (boxAdapter != null) boxAdapter.notifyDataSetChanged();
    }

    private void updateCounts() {
        if (tvAvailableCount != null) tvAvailableCount.setText(emptyCount + " Empty");
        if (tvOccupiedCount != null) tvOccupiedCount.setText(hasFoodCount + " Has Food");
    }

    private void updateDeviceBadge(boolean hasAnyPhysical, boolean anyPhysicalOnline) {
        if (tvLastUpdated == null || viewStatusDot == null) return;

        if (!hasAnyPhysical) {
            tvLastUpdated.setText("No device connected");
            viewStatusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9E9E9E));
            return;
        }

        if (anyPhysicalOnline) {
            tvLastUpdated.setText("Live");
            viewStatusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        } else {
            tvLastUpdated.setText("Devices Offline");
            viewStatusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
        }
    }

    private void updateLastUsed(long lastUsedTimestamp) {
        if (tvTotalDeliveries == null) return;

        if (lastUsedTimestamp <= 0) {
            tvTotalDeliveries.setText("Last used: Never");
            return;
        }

        long diffMillis = System.currentTimeMillis() - lastUsedTimestamp;
        if (diffMillis < 0) diffMillis = 0;

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);
        long days = TimeUnit.MILLISECONDS.toDays(diffMillis);

        String text;
        if (minutes < 1) text = "Just now";
        else if (minutes < 60) text = minutes + " min ago";
        else if (hours < 24) text = hours + " hours ago";
        else text = days + " days ago";

        tvTotalDeliveries.setText("Last used: " + text);
    }

    private void updateEmptyState() {
        boolean isEmpty = boxList.isEmpty();

        if (tvEmptyState != null) tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerViewBoxes != null) recyclerViewBoxes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (cardStatistics != null) cardStatistics.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // =========================
    // ✅ OWNERSHIP PROTECTION (CODE-ONLY)
    // =========================

    private boolean isInUseStatus(String status) {
        if (status == null) return false;
        status = status.toLowerCase();

        return status.equals("occupied")
                || status.equals("unlocked_for_delivery")
                || status.equals("unlocked_delivery")
                || status.equals("delivery_detected")
                || status.equals("unlocked_for_retrieval")
                || status.equals("unlocked_retrieval")
                || status.equals("retrieval_in_progress");
    }

    /**
     * Returns true if box is currently in-use by another user.
     * Uses your existing field: unlockedBy
     */
    private boolean isLockedToAnotherUser(DeliveryBox box) {
        String uid = firebaseHelper.getCurrentUserId();
        if (uid == null) return true;

        String status = box.getStatus();
        if (!isInUseStatus(status)) return false;

        String owner = box.getUnlockedBy();
        if (owner == null || owner.trim().isEmpty()) {
            // If box is "in use" but owner missing, be safe: block
            return true;
        }

        return !owner.equals(uid);
    }

    @Override
    public void onBoxClick(DeliveryBox box) {
        if (box == null || getContext() == null) return;

        if (hasAnyPhysical && !anyPhysicalOnlineCached) {
            Toast.makeText(getContext(),
                    "Devices offline. Turn on ESP32 + WiFi then refresh.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ BLOCK other users
        if (isLockedToAnotherUser(box)) {
            Toast.makeText(getContext(),
                    "This box is currently in use by another user.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String status = box.getStatus();
        if (status == null) status = "";

        switch (status.toLowerCase()) {
            case "idle":
            case "available":
                launchUnlockActivity(box);
                break;

            case "occupied":
                launchRetrieveActivity(box);
                break;

            case "unlocked_for_delivery":
            case "unlocked_delivery":
                launchUnlockActivity(box);
                break;

            case "unlocked_for_retrieval":
            case "unlocked_retrieval":
                launchRetrieveActivity(box);
                break;

            default:
                Toast.makeText(getContext(), "Status: " + box.getStatusText(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchUnlockActivity(DeliveryBox box) {
        Intent intent = new Intent(getContext(), UnlockForDeliveryActivity.class);
        intent.putExtra("boxId", box.getBoxId());
        intent.putExtra("boxNumber", box.getBoxNumber());
        intent.putExtra("boxLocation", box.getNameSafe());
        startActivity(intent);
    }

    private void launchRetrieveActivity(DeliveryBox box) {
        Intent intent = new Intent(getContext(), RetrieveFoodActivity.class);
        intent.putExtra("boxId", box.getBoxId());
        intent.putExtra("boxNumber", box.getBoxNumber());
        intent.putExtra("boxLocation", box.getNameSafe());
        startActivity(intent);
    }

    private void setupAdminMenu() {
        if (tvAppTitle == null) return;

        tvAppTitle.setOnLongClickListener(v -> {
            String[] options = {"Force Reset All Boxes", "Cancel"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("🛠 Admin Maintenance")
                    .setItems(options, (d, which) -> {
                        if (which == 0) {
                            for (DeliveryBox box : boxList) {
                                if (box == null || box.getBoxId() == null) continue;

                                // NOTE: This bypasses ownership logic. Use carefully in demo.
                                firebaseHelper.getDatabaseReference()
                                        .child("boxes")
                                        .child(box.getBoxId())
                                        .child("status")
                                        .setValue("available");

                                firebaseHelper.getDatabaseReference()
                                        .child("boxes")
                                        .child(box.getBoxId())
                                        .child("unlockedBy")
                                        .setValue(null);
                            }
                            Toast.makeText(getContext(), "Reset sent", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(statusRefreshRunnable);
        uiHandler.post(statusRefreshRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(statusRefreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        uiHandler.removeCallbacks(statusRefreshRunnable);

        if (boxesListener != null) {
            firebaseHelper.getDatabaseReference()
                    .child("boxes")
                    .removeEventListener(boxesListener);
        }
    }
}
