package com.arif.smartfooddeliverybox.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyMessage; // NEW: For dynamic empty messages
    private ChipGroup chipGroupFilter;

    private ImageButton btnCalendar, btnResetDate;
    private TextView tvDateFilter;

    private FirebaseHelper firebaseHelper;
    private ValueEventListener historyListener;

    private List<HistoryItem> allHistory = new ArrayList<>();
    private List<HistoryListItem> displayList = new ArrayList<>();
    private HistoryAdapter adapter;

    private Long selectedDate = null;
    private String currentActionFilter = "all"; // NEW: Track current filter

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_history, container, false);

        firebaseHelper = FirebaseHelper.getInstance();

        recyclerView = view.findViewById(R.id.recyclerViewDeliveries);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage); // NEW
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);
        btnCalendar = view.findViewById(R.id.btnCalendar);
        btnResetDate = view.findViewById(R.id.btnResetDate);
        tvDateFilter = view.findViewById(R.id.tvDateFilter);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter(displayList);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadHistory);

        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> applyFilters());

        btnCalendar.setOnClickListener(v -> showDatePicker());
        btnResetDate.setOnClickListener(v -> clearDateFilter());


        loadHistory();
        return view;
    }

    // ---------------- LOAD HISTORY ----------------

    private void loadHistory() {
        if (firebaseHelper.getCurrentUserId() == null) return;

        swipeRefresh.setRefreshing(true);
        String userId = firebaseHelper.getCurrentUserId();

        if (historyListener != null) {
            firebaseHelper.getDatabaseReference()
                    .child("history").child(userId)
                    .removeEventListener(historyListener);
        }

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allHistory.clear();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String action = snap.child("action").getValue(String.class);
                    String box = snap.child("boxNumber").getValue(String.class);

                    long ts = 0;
                    Object tObj = snap.child("timestamp").getValue();
                    if (tObj instanceof Long) ts = (Long) tObj;
                    else if (tObj instanceof Double) ts = ((Double) tObj).longValue();

                    if (action != null && box != null) {
                        allHistory.add(new HistoryItem(action, box, ts));
                    }
                }

                Collections.sort(allHistory, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                applyFilters();
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(getContext(), "Error loading history: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        firebaseHelper.getDatabaseReference()
                .child("history").child(userId)
                .addValueEventListener(historyListener);
    }

    // ---------------- FILTERING (FIXED) ----------------

    private void applyFilters() {
        displayList.clear();

        // FIXED: Determine action filter
        currentActionFilter = "all";
        int id = chipGroupFilter.getCheckedChipId();

        if (id == R.id.chipUnlocks) {
            currentActionFilter = "unlocked";
        } else if (id == R.id.chipFoodStored) { // FIXED: Added missing Food Stored filter
            currentActionFilter = "food_stored";
        } else if (id == R.id.chipDeliveries) {
            currentActionFilter = "food_stored"; // "Deliveries" means food was stored
        } else if (id == R.id.chipRetrievals) {
            currentActionFilter = "retrieved";
        }

        SimpleDateFormat dateHeaderFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String lastDate = "";

        for (HistoryItem item : allHistory) {

            // FIXED: Apply action filter correctly
            if (!"all".equals(currentActionFilter) && !item.action.equals(currentActionFilter)) {
                continue;
            }

            // FIXED: Apply date filter with proper timezone handling
            if (selectedDate != null && !isSameDay(item.timestamp, selectedDate)) {
                continue;
            }

            // Group by date header
            String date = dateHeaderFormat.format(new Date(item.timestamp));

            if (!date.equals(lastDate)) {
                displayList.add(new HistoryListItem(date));
                lastDate = date;
            }

            displayList.add(new HistoryListItem(item));
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
        updateHeaderCount();
    }

    // ---------------- DATE PICKER (FIXED) ----------------

    private void showDatePicker() {
        // Build date picker starting from today
        long today = MaterialDatePicker.todayInUtcMilliseconds();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(selectedDate != null ? selectedDate : today)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDate = selection;

            // FIXED: Display selected date consistently
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            String dateStr = sdf.format(new Date(selection));
            tvDateFilter.setText("📅 " + dateStr); // FIXED: Added emoji for clarity
            btnResetDate.setVisibility(View.VISIBLE);

            applyFilters();
        });

        picker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void clearDateFilter() {
        selectedDate = null;
        tvDateFilter.setText("Showing all history");
        btnResetDate.setVisibility(View.GONE);
        applyFilters();
    }

    // FIXED: Proper date comparison (was causing 05 Jan showing 20 Dec bug)
    // CORRECTED: Fixed timezone comparison
    private boolean isSameDay(long timestamp, long selectedDateUTC) {
        // Step 1: Convert Firebase timestamp to local calendar
        Calendar itemCal = Calendar.getInstance();
        itemCal.setTimeInMillis(timestamp);

        // Step 2: Convert UTC selected date to local calendar
        Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        selectedCal.setTimeInMillis(selectedDateUTC);

        // Step 3: Extract date components from UTC calendar
        int selectedYear = selectedCal.get(Calendar.YEAR);
        int selectedMonth = selectedCal.get(Calendar.MONTH);
        int selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH);

        // Step 4: Create local calendar with selected date
        Calendar localSelectedDate = Calendar.getInstance();
        localSelectedDate.clear();
        localSelectedDate.set(selectedYear, selectedMonth, selectedDay);

        // Step 5: Compare year, month, and day
        return itemCal.get(Calendar.YEAR) == localSelectedDate.get(Calendar.YEAR) &&
                itemCal.get(Calendar.MONTH) == localSelectedDate.get(Calendar.MONTH) &&
                itemCal.get(Calendar.DAY_OF_MONTH) == localSelectedDate.get(Calendar.DAY_OF_MONTH);
    }

    // FIXED: Dynamic empty state messages
    private void updateEmptyState() {
        if (displayList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

            // FIXED: Show contextual empty message
            String message;
            if (selectedDate != null && !"all".equals(currentActionFilter)) {
                message = "No activities found for selected date and filter";
            } else if (selectedDate != null) {
                message = "No activities found on this date";
            } else if (!"all".equals(currentActionFilter)) {
                message = "No activities found for this filter";
            } else {
                message = "No activity yet\n\nYour delivery history will appear here";
            }

            if (tvEmptyMessage != null) {
                tvEmptyMessage.setText(message);
            }

        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateHeaderCount() {
        if (tvDateFilter == null) return;

        int totalItems = allHistory.size();
        int visibleItems = 0;

        // Count non-header items in display list
        for (HistoryListItem item : displayList) {
            if (item.type == HistoryListItem.TYPE_EVENT) {
                visibleItems++;
            }
        }

        String headerText;

        if (selectedDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateStr = sdf.format(new Date(selectedDate));

            if (visibleItems == 0) {
                headerText = "📅 " + dateStr + " - No activities";
            } else {
                headerText = "📅 " + dateStr + " - " + visibleItems + " activities";
            }
        } else {
            if (totalItems == 0) {
                headerText = "No activities yet";
            } else if (!"all".equals(currentActionFilter)) {
                headerText = visibleItems + " of " + totalItems + " activities";
            } else {
                headerText = totalItems + " total activities";
            }
        }

        tvDateFilter.setText(headerText);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (historyListener != null && firebaseHelper != null) {
            firebaseHelper.getDatabaseReference()
                    .child("history")
                    .removeEventListener(historyListener);
        }
    }

    // ---------------- DATA CLASSES ----------------

    private static class HistoryItem {
        String action;
        String box;
        long timestamp;

        HistoryItem(String action, String box, long timestamp) {
            this.action = action;
            this.box = box;
            this.timestamp = timestamp;
        }
    }

    private static class HistoryListItem {
        static final int TYPE_DATE = 0;
        static final int TYPE_EVENT = 1;

        int type;
        String date;
        HistoryItem item;

        HistoryListItem(String date) {
            this.type = TYPE_DATE;
            this.date = date;
        }

        HistoryListItem(HistoryItem item) {
            this.type = TYPE_EVENT;
            this.item = item;
        }
    }

    // ---------------- ADAPTER (IMPROVED) ----------------

    private class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<HistoryListItem> items;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        HistoryAdapter(List<HistoryListItem> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == HistoryListItem.TYPE_DATE) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_history_date, parent, false);
                return new DateVH(v);
            } else {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_history_simple, parent, false);
                return new EventVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            HistoryListItem item = items.get(pos);

            if (holder instanceof DateVH) {
                ((DateVH) holder).tvDate.setText(item.date);
            } else {
                EventVH vh = (EventVH) holder;
                HistoryItem hi = item.item;

                vh.tvBox.setText("Box " + hi.box);
                vh.tvTime.setText(timeFormat.format(new Date(hi.timestamp)));

                // IMPROVED: Better color scheme and consistency
                switch (hi.action) {
                    case "unlocked":
                        vh.tvAction.setText("🔓 Unlocked for Delivery");
                        // Keep action text dark, only color the emoji/icon
                        vh.tvAction.setTextColor(0xFF212121); // Dark text
                        break;

                    case "food_stored":
                        vh.tvAction.setText("🍕 Food Stored");
                        vh.tvAction.setTextColor(0xFF212121);
                        break;

                    case "retrieved":
                        vh.tvAction.setText("✅ Delivery Collected");
                        vh.tvAction.setTextColor(0xFF212121);
                        break;

                    default:
                        vh.tvAction.setText(hi.action);
                        vh.tvAction.setTextColor(0xFF757575);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class DateVH extends RecyclerView.ViewHolder {
            TextView tvDate;

            DateVH(View v) {
                super(v);
                tvDate = v.findViewById(R.id.tvDateHeader);
            }
        }

        class EventVH extends RecyclerView.ViewHolder {
            TextView tvAction, tvBox, tvTime;

            EventVH(View v) {
                super(v);
                tvAction = v.findViewById(R.id.tvAction);
                tvBox = v.findViewById(R.id.tvBox);
                tvTime = v.findViewById(R.id.tvTime);
            }
        }
    }
}