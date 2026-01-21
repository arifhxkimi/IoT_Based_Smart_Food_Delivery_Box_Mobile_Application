package com.arif.smartfooddeliverybox.models;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Model class for the Smart Food Delivery Box.
 * Online/offline is determined ONLY by lastHeartbeat (epoch millis).
 */
@IgnoreExtraProperties
public class DeliveryBox {

    // --- Firebase fields (must match your RTDB keys) ---
    private String boxId;        // optional in DB, we set from snapshot key
    private String boxNumber;    // "1", "2"
    private String name;         // "Box 1"
    private String status;       // idle/available/unlocked_delivery/occupied/unlocked_retrieval/etc
    private boolean physical;    // in DB you have "physical: true"
    private boolean enabled;
    private long lastHeartbeat;  // epoch ms, e.g. 1768840581000

    // Session tracking (optional but useful)
    private String unlockedBy;
    private long unlockedAt;
    private long deliveredAt;

    // Statistics (optional)
    private BoxStatistics statistics;

    public DeliveryBox() {
        // Firebase needs empty constructor
    }

    /** ONLINE logic: only for physical devices. */
    public boolean isOnline() {
        if (!physical) return false;
        if (lastHeartbeat <= 0) return false;

        long now = System.currentTimeMillis();
        long diff = now - lastHeartbeat;

        // If clock is weird (future timestamp), treat as online instead of breaking UI
        if (diff < 0) diff = 0;

        // 60 seconds threshold (tune this if you update heartbeat slower/faster)
        return diff < 8_000;
    }

    /** Available if idle/available (legacy). */
    public boolean isAvailable() {
        return "idle".equalsIgnoreCase(status) || "available".equalsIgnoreCase(status);
    }

    public String getNameSafe() {
        if (name != null && !name.trim().isEmpty()) return name;
        if (boxNumber != null && !boxNumber.trim().isEmpty()) return "Box " + boxNumber;
        return "Box";
    }

    /** Status text: if offline, show OFFLINE first. */
    public String getStatusText() {
        if (physical && !isOnline()) return "OFFLINE";
        if (status == null || status.trim().isEmpty()) return "Unknown";

        switch (status.toLowerCase()) {
            case "idle":
            case "available":
                return "Ready";

            case "unlocked_for_delivery":
            case "unlocked_delivery":
                return "Awaiting Food";

            case "delivery_detected":
                return "Locking...";

            case "occupied":
                return "Has Food";

            case "unlocked_for_retrieval":
            case "unlocked_retrieval":
                return "Retrieving";

            case "retrieval_in_progress":
                return "Completing...";

            default:
                return status.substring(0, 1).toUpperCase() + status.substring(1);
        }
    }

    /** Status color: if offline -> grey. */
    public String getStatusColor() {
        if (physical && !isOnline()) return "#9E9E9E";
        if (status == null || status.trim().isEmpty()) return "#607D8B";

        switch (status.toLowerCase()) {
            case "idle":
            case "available":
                return "#4CAF50"; // green

            case "unlocked_for_delivery":
            case "unlocked_delivery":
            case "delivery_detected":
                return "#FF9800"; // orange

            case "occupied":
                return "#9C27B0"; // purple

            case "unlocked_for_retrieval":
            case "unlocked_retrieval":
            case "retrieval_in_progress":
                return "#2196F3"; // blue

            default:
                return "#607D8B";
        }
    }

    // ---------------- Getters / Setters ----------------

    public String getBoxId() { return boxId; }
    public void setBoxId(String boxId) { this.boxId = boxId; }

    public String getBoxNumber() { return boxNumber; }
    public void setBoxNumber(String boxNumber) { this.boxNumber = boxNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // IMPORTANT: DB key is "physical"
    public boolean isPhysical() { return physical; }
    public void setPhysical(boolean physical) { this.physical = physical; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public String getUnlockedBy() { return unlockedBy; }
    public void setUnlockedBy(String unlockedBy) { this.unlockedBy = unlockedBy; }

    public long getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(long unlockedAt) { this.unlockedAt = unlockedAt; }

    public long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(long deliveredAt) { this.deliveredAt = deliveredAt; }

    public BoxStatistics getStatistics() { return statistics; }
    public void setStatistics(BoxStatistics statistics) { this.statistics = statistics; }

    @IgnoreExtraProperties
    public static class BoxStatistics {
        private int totalDeliveries;
        private int totalRetrievals;
        private long lastUsed;

        public BoxStatistics() {}

        public int getTotalDeliveries() { return totalDeliveries; }
        public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }

        public int getTotalRetrievals() { return totalRetrievals; }
        public void setTotalRetrievals(int totalRetrievals) { this.totalRetrievals = totalRetrievals; }

        public long getLastUsed() { return lastUsed; }
        public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }
    }
}
