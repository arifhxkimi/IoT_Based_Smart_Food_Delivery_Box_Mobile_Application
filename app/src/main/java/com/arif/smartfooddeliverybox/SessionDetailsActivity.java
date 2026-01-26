package com.arif.smartfooddeliverybox;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionDetailsActivity extends BaseInsetActivity {

    public static final String EXTRA_SESSION_JSON = "extra_session_json";

    private MaterialToolbar toolbar;
    private TextView tvHeaderTitle, tvHeaderSubtitle, tvHeaderMeta;
    private RecyclerView recyclerView;

    private final List<EventItem> events = new ArrayList<>();
    private EventsAdapter adapter;

    private final SimpleDateFormat dateTimeFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_details);

        applyStatusBarInset();

        toolbar = findViewById(R.id.toolbar);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvHeaderMeta = findViewById(R.id.tvHeaderMeta);
        recyclerView = findViewById(R.id.recyclerViewEvents);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Session Details");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventsAdapter(events);
        recyclerView.setAdapter(adapter);

        String json = getIntent().getStringExtra(EXTRA_SESSION_JSON);
        if (json == null || json.trim().isEmpty()) {
            finish();
            return;
        }

        parseAndBind(json);
    }

    private void parseAndBind(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            String boxNumber = obj.optString("boxNumber", "");
            String status = obj.optString("status", "in_progress");
            long startAt = obj.optLong("startAt", 0);
            long endAt = obj.optLong("endAt", 0);

            // Header
            tvHeaderTitle.setText("Box " + boxNumber);

            String statusLabel = statusToLabel(status);
            tvHeaderSubtitle.setText(statusLabel);

            String range;
            if (startAt > 0 && endAt > 0) {
                range = dateTimeFmt.format(new Date(startAt)) + "  →  " + dateTimeFmt.format(new Date(endAt));
            } else if (startAt > 0) {
                range = dateTimeFmt.format(new Date(startAt));
            } else {
                range = "Unknown time";
            }

            String duration = "";
            if (startAt > 0 && endAt > 0 && endAt >= startAt) {
                duration = " • " + formatDuration(endAt - startAt);
            }

            tvHeaderMeta.setText(range + duration);

            // Events
            events.clear();
            JSONArray arr = obj.optJSONArray("events");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    if (e == null) continue;

                    String action = e.optString("action", "");
                    long ts = e.optLong("timestamp", 0);

                    if (!action.isEmpty() && ts > 0) {
                        events.add(new EventItem(action, ts));
                    }
                }
            }

            adapter.notifyDataSetChanged();

            // Empty state handling (optional)
            View empty = findViewById(R.id.layoutEmptyEvents);
            if (empty != null) {
                empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
            }

        } catch (Exception ex) {
            finish();
        }
    }

    private String statusToLabel(String status) {
        if (status == null) return "🟡 In Progress";
        switch (status.toLowerCase()) {
            case "completed":
                return "✅ Completed";
            case "cancelled":
                return "❌ Cancelled";
            default:
                return "🟡 In Progress";
        }
    }

    private String formatDuration(long millis) {
        long sec = millis / 1000;
        long min = sec / 60;
        long hr = min / 60;

        long remMin = min % 60;
        long remSec = sec % 60;

        if (hr > 0) return hr + "h " + remMin + "m";
        if (min > 0) return min + "m " + remSec + "s";
        return remSec + "s";
    }

    // ---------------- Recycler ----------------

    private static class EventItem {
        String action;
        long ts;

        EventItem(String action, long ts) {
            this.action = action;
            this.ts = ts;
        }
    }

    private class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.VH> {

        private final List<EventItem> items;

        EventsAdapter(List<EventItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_session_event_timeline, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            EventItem item = items.get(position);

            h.tvTitle.setText(actionToTitle(item.action));
            h.tvSubtitle.setText(actionToSubtitle(item.action));

            h.tvTime.setText(timeFmt.format(new Date(item.ts)));

            // Hide line on last item
            h.line.setVisibility(position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View dot;
            View line;
            TextView tvTitle, tvSubtitle, tvTime;

            VH(View v) {
                super(v);
                dot = v.findViewById(R.id.dot);
                line = v.findViewById(R.id.line);
                tvTitle = v.findViewById(R.id.tvEventTitle);
                tvSubtitle = v.findViewById(R.id.tvEventSubtitle);
                tvTime = v.findViewById(R.id.tvEventTime);
            }
        }
    }

    private String actionToTitle(String action) {
        if (action == null) return "Activity";
        switch (action.toLowerCase()) {
            case "unlocked_for_delivery":
            case "unlocked_delivery":
                return "🔓 Unlocked for Delivery";
            case "food_stored":
                return "🍕 Food Stored";
            case "unlocked_for_retrieval":
            case "unlocked_retrieval":
                return "📦 Unlocked for Retrieval";
            case "retrieved":
            case "delivery_collected":
            case "collected":
                return "✅ Delivery Collected";
            case "cancelled":
                return "❌ Cancelled";
            default:
                return action.replace("_", " ");
        }
    }

    private String actionToSubtitle(String action) {
        if (action == null) return "";
        switch (action.toLowerCase()) {
            case "unlocked_for_delivery":
            case "unlocked_delivery":
                return "Box opened for rider to place food";
            case "food_stored":
                return "Food detected and stored inside the box";
            case "unlocked_for_retrieval":
            case "unlocked_retrieval":
                return "Box opened for user to retrieve food";
            case "retrieved":
            case "delivery_collected":
            case "collected":
                return "Food successfully collected by user";
            case "cancelled":
                return "Delivery session was cancelled by user";
            default:
                return "";
        }
    }
}
