package com.arif.smartfooddeliverybox.fragments;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";

    private RecyclerView recyclerViewHistory;
    private HistoryAdapter historyAdapter;

    // We need TWO lists: one for all data, one for what is shown
    private List<HistoryItem> allHistoryList;
    private List<HistoryItem> displayedList;

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private ChipGroup chipGroupFilter;

    private FirebaseHelper firebaseHelper;
    private ValueEventListener historyListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        initViews(view);
        setupRecyclerView();
        setupFilters(); // NEW: Add filter logic
        loadHistory();

        return view;
    }

    private void initViews(View view) {
        firebaseHelper = FirebaseHelper.getInstance();

        recyclerViewHistory = view.findViewById(R.id.recyclerViewDeliveries);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter); // Bind the ChipGroup

        swipeRefresh.setOnRefreshListener(this::loadHistory);
    }

    private void setupRecyclerView() {
        allHistoryList = new ArrayList<>();
        displayedList = new ArrayList<>();

        // Adapter uses the displayedList
        historyAdapter = new HistoryAdapter(displayedList);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewHistory.setAdapter(historyAdapter);
    }

    // NEW: Handle Chip Clicks
    private void setupFilters() {
        if (chipGroupFilter != null) {
            chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
                String filterType = "all";
                if (checkedId == R.id.chipPending) filterType = "unlocked";
                else if (checkedId == R.id.chipInBox) filterType = "food_stored";
                else if (checkedId == R.id.chipCompleted) filterType = "retrieved";

                applyFilter(filterType);
            });
        }
    }

    private void loadHistory() {
        if (firebaseHelper.getCurrentUserId() == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        swipeRefresh.setRefreshing(true);
        String userId = firebaseHelper.getCurrentUserId();

        if (historyListener != null) {
            firebaseHelper.getDatabaseReference().child("history").child(userId).removeEventListener(historyListener);
        }

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allHistoryList.clear();

                for (DataSnapshot historySnapshot : snapshot.getChildren()) {
                    try {
                        String action = historySnapshot.child("action").getValue(String.class);
                        String boxNumber = historySnapshot.child("boxNumber").getValue(String.class);

                        // FIX SORTING: Robust timestamp parsing
                        long timestamp = 0;
                        Object tsObj = historySnapshot.child("timestamp").getValue();

                        if (tsObj instanceof Long) {
                            timestamp = (Long) tsObj;
                        } else if (tsObj instanceof Double) {
                            timestamp = ((Double) tsObj).longValue();
                        } else if (tsObj instanceof String) {
                            try {
                                timestamp = Long.parseLong((String) tsObj);
                            } catch (NumberFormatException e) {
                                timestamp = 0;
                            }
                        }

                        if (action != null && boxNumber != null) {
                            HistoryItem item = new HistoryItem(action, boxNumber, timestamp);
                            allHistoryList.add(item);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing history item: " + e.getMessage());
                    }
                }

                // FIX SORTING: Sort Newest First (Descending)
                Collections.sort(allHistoryList, (o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));

                // Apply current filter (or default to 'all')
                int checkedId = (chipGroupFilter != null) ? chipGroupFilter.getCheckedChipId() : -1;
                String currentFilter = "all";
                if (checkedId == R.id.chipPending) currentFilter = "unlocked";
                else if (checkedId == R.id.chipInBox) currentFilter = "food_stored";
                else if (checkedId == R.id.chipCompleted) currentFilter = "retrieved";

                applyFilter(currentFilter);
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };

        firebaseHelper.getDatabaseReference().child("history").child(userId).addValueEventListener(historyListener);
    }

    // NEW: Filter Logic
    private void applyFilter(String filterType) {
        displayedList.clear();

        if ("all".equals(filterType)) {
            displayedList.addAll(allHistoryList);
        } else {
            for (HistoryItem item : allHistoryList) {
                if (item.action.equals(filterType)) {
                    displayedList.add(item);
                }
            }
        }

        historyAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (displayedList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerViewHistory.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerViewHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (historyListener != null && firebaseHelper != null) {
            firebaseHelper.getDatabaseReference().child("history").removeEventListener(historyListener);
        }
    }

    // --- Inner Model Class ---
    private static class HistoryItem {
        String action;
        String boxNumber;
        long timestamp;

        public HistoryItem(String action, String boxNumber, long timestamp) {
            this.action = action;
            this.boxNumber = boxNumber;
            this.timestamp = timestamp;
        }
    }

    // --- Adapter (Updated to use item_history_simple.xml) ---
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

        private List<HistoryItem> items;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        public HistoryAdapter(List<HistoryItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Using your existing "item_history_simple" layout
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_simple, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            HistoryItem item = items.get(position);

            holder.tvBox.setText("Box " + item.boxNumber);
            holder.tvDate.setText(dateFormat.format(new Date(item.timestamp)));
            holder.tvTime.setText(timeFormat.format(new Date(item.timestamp)));

            // Styling based on action
            switch (item.action) {
                case "unlocked":
                    holder.tvAction.setText("🔓 Unlocked for Delivery");
                    setColors(holder, "#FF9800"); // Orange
                    break;
                case "food_stored":
                    holder.tvAction.setText("📥 Food Stored");
                    setColors(holder, "#9C27B0"); // Purple
                    break;
                case "retrieved":
                    holder.tvAction.setText("✅ Delivery Collected");
                    setColors(holder, "#4CAF50"); // Green
                    break;
                default:
                    holder.tvAction.setText("📋 " + item.action);
                    setColors(holder, "#757575");
                    break;
            }
        }

        private void setColors(HistoryViewHolder holder, String colorHex) {
            try {
                int color = android.graphics.Color.parseColor(colorHex);
                holder.tvAction.setTextColor(color);
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
            // Updated IDs to match your item_history_simple.xml
            TextView tvAction, tvBox, tvDate, tvTime;

            public HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAction = itemView.findViewById(R.id.tvAction);
                tvBox = itemView.findViewById(R.id.tvBox);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }
}