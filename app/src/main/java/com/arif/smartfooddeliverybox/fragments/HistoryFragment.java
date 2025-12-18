package com.arif.smartfooddeliverybox.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private List<HistoryItem> historyList;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;

    private FirebaseHelper firebaseHelper;
    private ValueEventListener historyListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        initViews(view);
        setupRecyclerView();
        loadHistory();

        return view;
    }

    private void initViews(View view) {
        firebaseHelper = FirebaseHelper.getInstance();

        recyclerViewHistory = view.findViewById(R.id.recyclerViewDeliveries);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);

        swipeRefresh.setOnRefreshListener(this::loadHistory);
    }

    private void setupRecyclerView() {
        historyList = new ArrayList<>();
        historyAdapter = new HistoryAdapter(historyList);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewHistory.setAdapter(historyAdapter);
    }

    private void loadHistory() {
        if (firebaseHelper.getCurrentUserId() == null) {
            swipeRefresh.setRefreshing(false);
            showEmptyState();
            return;
        }

        swipeRefresh.setRefreshing(true);
        String userId = firebaseHelper.getCurrentUserId();

        Log.d(TAG, "Loading history for user: " + userId);

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();

                Log.d(TAG, "History snapshot exists: " + snapshot.exists());
                Log.d(TAG, "History children count: " + snapshot.getChildrenCount());

                for (DataSnapshot historySnapshot : snapshot.getChildren()) {
                    try {
                        String action = historySnapshot.child("action").getValue(String.class);
                        String boxNumber = historySnapshot.child("boxNumber").getValue(String.class);
                        Long timestamp = historySnapshot.child("timestamp").getValue(Long.class);

                        Log.d(TAG, "History entry - Action: " + action + ", Box: " + boxNumber + ", Time: " + timestamp);

                        if (action != null && boxNumber != null && timestamp != null) {
                            HistoryItem item = new HistoryItem(action, boxNumber, timestamp);
                            historyList.add(item);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing history item: " + e.getMessage());
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(historyList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                historyAdapter.notifyDataSetChanged();
                updateEmptyState();
                swipeRefresh.setRefreshing(false);

                Log.d(TAG, "Loaded " + historyList.size() + " history items");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                if (getContext() != null && isAdded()) {
                    Toast.makeText(getContext(), "Failed to load history: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
                Log.e(TAG, "Database error: " + error.getMessage());
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("history")
                .child(userId)
                .addValueEventListener(historyListener);
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            showEmptyState();
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerViewHistory.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState() {
        layoutEmpty.setVisibility(View.VISIBLE);
        recyclerViewHistory.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (historyListener != null && firebaseHelper != null && firebaseHelper.getCurrentUserId() != null) {
            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .child(firebaseHelper.getCurrentUserId())
                    .removeEventListener(historyListener);
        }
    }

    // Simple HistoryItem class
    private static class HistoryItem {
        String action;
        String boxNumber;
        long timestamp;

        public HistoryItem(String action, String boxNumber, long timestamp) {
            this.action = action;
            this.boxNumber = boxNumber;
            this.timestamp = timestamp;
        }

        public String getActionText() {
            switch (action) {
                case "unlocked": return "🔓 Box Unlocked for Delivery";
                case "food_stored": return "📥 Food Delivered";
                case "retrieved": return "✅ Food Retrieved";
                default: return "📋 " + action;
            }
        }

        public String getActionColor() {
            switch (action) {
                case "unlocked": return "#2196F3"; // Blue
                case "food_stored": return "#9C27B0"; // Purple
                case "retrieved": return "#4CAF50"; // Green
                default: return "#757575"; // Gray
            }
        }
    }

    // Simple Adapter
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
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_simple, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            HistoryItem item = items.get(position);

            holder.tvAction.setText(item.getActionText());
            holder.tvBox.setText("Box " + item.boxNumber);
            holder.tvDate.setText(dateFormat.format(new Date(item.timestamp)));
            holder.tvTime.setText(timeFormat.format(new Date(item.timestamp)));

            // Set action color
            try {
                int color = android.graphics.Color.parseColor(item.getActionColor());
                holder.tvAction.setTextColor(color);
            } catch (Exception e) {
                // Ignore color parsing errors
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
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