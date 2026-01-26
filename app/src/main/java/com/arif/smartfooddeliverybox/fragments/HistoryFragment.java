package com.arif.smartfooddeliverybox.fragments;

import android.content.Intent;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.SessionDetailsActivity;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryFragment extends BaseInsetFragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyMessage;
    private ChipGroup chipGroupFilter;

    private ImageButton btnCalendar, btnResetDate;
    private TextView tvDateFilter;

    private FirebaseHelper firebaseHelper;
    private ValueEventListener historyListener;
    private DatabaseReference historyRef;

    // Raw history (from Firebase)
    private final List<HistoryItem> allHistory = new ArrayList<>();

    // Sessions (grouped UI)
    private final List<DeliverySession> allSessions = new ArrayList<>();

    // Adapter list (date headers + sessions)
    private final List<HistoryListItem> displayList = new ArrayList<>();
    private HistoryAdapter adapter;

    private Long selectedDate = null;

    // Filters: all / in_progress / completed / cancelled
    private String currentFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_history, container, false);
        applyStatusBarInset(view);

        firebaseHelper = FirebaseHelper.getInstance();

        recyclerView = view.findViewById(R.id.recyclerViewDeliveries);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);
        btnCalendar = view.findViewById(R.id.btnCalendar);
        btnResetDate = view.findViewById(R.id.btnResetDate);
        tvDateFilter = view.findViewById(R.id.tvDateFilter);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter(displayList);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadHistory);

        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            updateCurrentFilter(checkedId);
            applyFilters();
        });

        btnCalendar.setOnClickListener(v -> showDatePicker());
        btnResetDate.setOnClickListener(v -> clearDateFilter());

        // init filter state
        updateCurrentFilter(chipGroupFilter.getCheckedChipId());

        loadHistory();
        return view;
    }

    // ---------------- LOAD HISTORY ----------------

    private void loadHistory() {
        String userId = firebaseHelper.getCurrentUserId();
        if (userId == null) return;

        safeSetRefreshing(true);

        historyRef = firebaseHelper.getDatabaseReference()
                .child("history")
                .child(userId);

        detachHistoryListener();

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

                // newest first
                Collections.sort(allHistory, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                // dedupe spam (same action+box within 10 seconds)
                List<HistoryItem> deduped = new ArrayList<>();
                HistoryItem prevKept = null;
                for (HistoryItem item : allHistory) {
                    if (isDuplicate(item, prevKept)) continue;
                    deduped.add(item);
                    prevKept = item;
                }
                allHistory.clear();
                allHistory.addAll(deduped);

                // build sessions
                allSessions.clear();
                allSessions.addAll(buildSessionsFromHistory(allHistory));

                applyFilters();
                safeSetRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                safeSetRefreshing(false);
                if (!isAdded() || getContext() == null) return;

                Toast.makeText(requireContext(),
                        "Error loading history: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        historyRef.addValueEventListener(historyListener);
    }

    private void safeSetRefreshing(boolean refreshing) {
        if (swipeRefresh == null) return;
        swipeRefresh.setRefreshing(refreshing);
    }

    private void detachHistoryListener() {
        if (historyRef != null && historyListener != null) {
            historyRef.removeEventListener(historyListener);
        }
        historyListener = null;
    }

    // ---------------- FILTERS ----------------

    private void updateCurrentFilter(int checkedId) {
        currentFilter = "all";

        if (checkedId == R.id.chipInProgress) {
            currentFilter = "in_progress";
        } else if (checkedId == R.id.chipCompleted) {
            currentFilter = "completed";
        } else if (checkedId == R.id.chipCancelled) {
            currentFilter = "cancelled";
        } else {
            currentFilter = "all";
        }
    }

    private boolean passesSessionFilter(DeliverySession s) {
        if ("all".equals(currentFilter)) return true;

        String st = safe(s.status);
        if (st.isEmpty()) st = "in_progress";

        return st.equals(currentFilter);
    }

    // ---------------- DUPLICATE RULE ----------------

    private boolean isDuplicate(HistoryItem current, HistoryItem previousKept) {
        if (previousKept == null) return false;
        if (current.action == null || previousKept.action == null) return false;
        if (current.box == null || previousKept.box == null) return false;

        if (!current.action.equals(previousKept.action)) return false;
        if (!current.box.equals(previousKept.box)) return false;

        return Math.abs(current.timestamp - previousKept.timestamp) <= 10_000;
    }

    // ---------------- SESSION GROUPING ----------------

    private List<DeliverySession> buildSessionsFromHistory(List<HistoryItem> newestFirst) {

        List<HistoryItem> asc = new ArrayList<>(newestFirst);
        Collections.sort(asc, (a, b) -> Long.compare(a.timestamp, b.timestamp));

        List<DeliverySession> sessions = new ArrayList<>();
        DeliverySession current = null;

        for (HistoryItem e : asc) {
            String a = safe(e.action);

            boolean isStart = a.equals("unlocked_for_delivery") || a.equals("unlocked_delivery");
            boolean isEnd = a.equals("retrieved")
                    || a.equals("delivery_collected")
                    || a.equals("collected")
                    || a.equals("cancelled");

            if (isStart) {
                if (current != null) {
                    current.status = "in_progress";
                    sessions.add(current);
                }

                current = new DeliverySession();
                current.boxNumber = e.box;
                current.startAt = e.timestamp;
                current.status = "in_progress";
                current.events.add(e);
                current.hasUnlockDelivery = true;
                continue;
            }

            if (current == null) continue;

            current.events.add(e);

            if (a.equals("food_stored")) current.hasFoodStored = true;
            if (a.equals("unlocked_for_retrieval") || a.equals("unlocked_retrieval")) current.hasUnlockRetrieval = true;

            if (isEnd) {
                current.endAt = e.timestamp;

                if (a.equals("cancelled")) {
                    current.status = "cancelled";
                } else {
                    current.hasCollected = true;
                    current.status = "completed";
                }

                sessions.add(current);
                current = null;
            }
        }

        if (current != null) {
            current.status = "in_progress";
            sessions.add(current);
        }

        Collections.sort(sessions, (s1, s2) -> Long.compare(s2.startAt, s1.startAt));
        return sessions;
    }

    private String safe(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    // ---------------- APPLY FILTERS ----------------

    private void applyFilters() {
        displayList.clear();

        SimpleDateFormat dateHeaderFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String lastDate = "";

        for (DeliverySession s : allSessions) {

            if (!passesSessionFilter(s)) continue;

            if (selectedDate != null && !isSameDay(s.startAt, selectedDate)) continue;

            String date = dateHeaderFormat.format(new Date(s.startAt));
            if (!date.equals(lastDate)) {
                displayList.add(new HistoryListItem(date));
                lastDate = date;
            }

            displayList.add(new HistoryListItem(s));
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
        updateHeaderCount();
    }

    // ---------------- DATE PICKER ----------------

    private void showDatePicker() {
        long today = MaterialDatePicker.todayInUtcMilliseconds();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(selectedDate != null ? selectedDate : today)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDate = selection;

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            String dateStr = sdf.format(new Date(selection));
            tvDateFilter.setText("📅 " + dateStr);
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

    // ---------------- DAY COMPARISON ----------------

    private boolean isSameDay(long timestamp, long selectedDateUTC) {
        Calendar itemCal = Calendar.getInstance();
        itemCal.setTimeInMillis(timestamp);

        Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        selectedCal.setTimeInMillis(selectedDateUTC);

        int selectedYear = selectedCal.get(Calendar.YEAR);
        int selectedMonth = selectedCal.get(Calendar.MONTH);
        int selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH);

        Calendar localSelectedDate = Calendar.getInstance();
        localSelectedDate.clear();
        localSelectedDate.set(selectedYear, selectedMonth, selectedDay);

        return itemCal.get(Calendar.YEAR) == localSelectedDate.get(Calendar.YEAR) &&
                itemCal.get(Calendar.MONTH) == localSelectedDate.get(Calendar.MONTH) &&
                itemCal.get(Calendar.DAY_OF_MONTH) == localSelectedDate.get(Calendar.DAY_OF_MONTH);
    }

    // ---------------- EMPTY STATE ----------------

    private void updateEmptyState() {
        if (displayList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

            String message;
            if (selectedDate != null && !"all".equals(currentFilter)) {
                message = "No sessions found for selected date and filter";
            } else if (selectedDate != null) {
                message = "No sessions found on this date";
            } else if (!"all".equals(currentFilter)) {
                message = "No sessions found for this filter";
            } else {
                message = "No activity yet\n\nYour delivery history will appear here";
            }

            if (tvEmptyMessage != null) tvEmptyMessage.setText(message);

        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateHeaderCount() {
        if (tvDateFilter == null) return;

        int totalSessions = allSessions.size();
        int visibleSessions = 0;

        for (HistoryListItem item : displayList) {
            if (item.type == HistoryListItem.TYPE_SESSION) visibleSessions++;
        }

        String headerText;

        if (selectedDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateStr = sdf.format(new Date(selectedDate));

            headerText = (visibleSessions == 0)
                    ? "📅 " + dateStr + " - No sessions"
                    : "📅 " + dateStr + " - " + visibleSessions + " sessions";
        } else {
            if (totalSessions == 0) headerText = "No activities yet";
            else if (!"all".equals(currentFilter)) headerText = visibleSessions + " of " + totalSessions + " sessions";
            else headerText = totalSessions + " total sessions";
        }

        tvDateFilter.setText(headerText);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        detachHistoryListener();
        historyRef = null;

        swipeRefresh = null;
        recyclerView = null;
        layoutEmpty = null;
        tvEmptyMessage = null;
        chipGroupFilter = null;
        btnCalendar = null;
        btnResetDate = null;
        tvDateFilter = null;
    }

    // ---------------- SESSION DETAILS OPEN ----------------

    private void openSessionDetails(DeliverySession s) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("boxNumber", s.boxNumber == null ? "" : s.boxNumber);
            obj.put("status", s.status == null ? "in_progress" : s.status);
            obj.put("startAt", s.startAt);
            obj.put("endAt", s.endAt);

            List<HistoryItem> asc = new ArrayList<>(s.events);
            Collections.sort(asc, (a, b) -> Long.compare(a.timestamp, b.timestamp));

            JSONArray arr = new JSONArray();
            for (HistoryItem e : asc) {
                JSONObject eo = new JSONObject();
                eo.put("action", e.action == null ? "" : e.action);
                eo.put("timestamp", e.timestamp);
                eo.put("boxNumber", e.box == null ? "" : e.box);
                arr.put(eo);
            }
            obj.put("events", arr);

            Intent i = new Intent(requireContext(), SessionDetailsActivity.class);
            i.putExtra(SessionDetailsActivity.EXTRA_SESSION_JSON, obj.toString());
            startActivity(i);

        } catch (Exception ignored) { }
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

    private static class DeliverySession {
        String boxNumber;
        long startAt;
        long endAt; // 0 if not ended
        String status; // in_progress / completed / cancelled

        boolean hasUnlockDelivery;
        boolean hasFoodStored;
        boolean hasUnlockRetrieval;
        boolean hasCollected;

        List<HistoryItem> events = new ArrayList<>();
    }

    private static class HistoryListItem {
        static final int TYPE_DATE = 0;
        static final int TYPE_SESSION = 1;

        int type;
        String date;
        DeliverySession session;

        HistoryListItem(String date) {
            this.type = TYPE_DATE;
            this.date = date;
        }

        HistoryListItem(DeliverySession session) {
            this.type = TYPE_SESSION;
            this.session = session;
        }
    }

    // ---------------- ADAPTER ----------------

    private class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<HistoryListItem> items;
        private final SimpleDateFormat timeOnly = new SimpleDateFormat("hh:mm a", Locale.getDefault());

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
                return new SessionVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            HistoryListItem li = items.get(pos);

            if (holder instanceof DateVH) {
                ((DateVH) holder).tvDate.setText(li.date);
                return;
            }

            SessionVH vh = (SessionVH) holder;
            DeliverySession s = li.session;

            vh.tvBox.setText("Box " + (s.boxNumber == null ? "" : s.boxNumber));

            if (s.endAt > 0) {
                vh.tvTime.setText(timeOnly.format(new Date(s.startAt)) + " → " + timeOnly.format(new Date(s.endAt)));
            } else {
                vh.tvTime.setText(timeOnly.format(new Date(s.startAt)));
            }

            vh.tvAction.setText(getStatusLabel(s));
            vh.tvSubtitle.setText(getTimelineLabel(s));
            vh.tvAction.setTextColor(0xFF212121);

            vh.itemView.setOnClickListener(v -> openSessionDetails(s));
        }

        private String getStatusLabel(DeliverySession s) {
            String st = safe(s.status);
            if (st.equals("cancelled")) return "❌ Cancelled";
            if (st.equals("completed") || s.hasCollected) return "✅ Completed";
            return "🟡 In Progress";
        }

        private String getTimelineLabel(DeliverySession s) {
            String st = safe(s.status);
            if (st.equals("cancelled")) return "Unlocked → Cancelled";
            if (s.hasCollected) return "Unlocked → Stored → Retrieved";
            if (s.hasFoodStored) return "Unlocked → Stored";
            return "Unlocked → Waiting for food";
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

        class SessionVH extends RecyclerView.ViewHolder {
            TextView tvAction, tvSubtitle, tvBox, tvTime;
            SessionVH(View v) {
                super(v);
                tvAction = v.findViewById(R.id.tvAction);
                tvSubtitle = v.findViewById(R.id.tvSubtitle);
                tvBox = v.findViewById(R.id.tvBox);
                tvTime = v.findViewById(R.id.tvTime);
            }
        }
    }
}
