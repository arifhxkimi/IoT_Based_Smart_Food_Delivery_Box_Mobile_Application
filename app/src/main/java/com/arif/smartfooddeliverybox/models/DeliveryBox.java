package com.arif.smartfooddeliverybox.models;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Model class for the Smart Food Box.
 * Matches the structure in Firebase > boxes > {boxId}
 */
@IgnoreExtraProperties
public class DeliveryBox {

    // --- Firebase Fields (Must match JSON keys exactly) ---
    private String boxId;
    private String boxNumber;
    private String status;      // "available", "occupied", "unlocked_delivery", "unlocked_retrieval"
    private boolean isPhysical;
    private boolean enabled;
    private long lastHeartbeat;

    // Tracking fields (from your JSON)
    private String unlockedBy;
    private long unlockedAt;
    private long deliveredAt;

    // Nested Statistics Object
    private BoxStatistics statistics;

    // --- 1. Constructors ---

    public DeliveryBox() {
        this.statistics = new BoxStatistics();
    }

    // --- 2. Logic Helpers (For UI) ---

    public boolean isOnline() {
        long currentTime = System.currentTimeMillis();
        if (lastHeartbeat == 0) return false;
        return (currentTime - lastHeartbeat) < 60000;
    }

    public String getName() {
        return "Box " + boxNumber;
    }

    public boolean isAvailable() {
        return "available".equalsIgnoreCase(status);
    }

    /**
     * Returns a user-friendly status text handling the new 4-state logic.
     */
    public String getStatusText() {
        if (isPhysical && !isOnline()) {
            return "OFFLINE";
        }

        if ("available".equalsIgnoreCase(status)) return "Available";
        if ("occupied".equalsIgnoreCase(status)) return "Occupied";

        // NEW STATES
        if ("unlocked_delivery".equalsIgnoreCase(status)) return "Waiting for Rider";
        if ("unlocked_retrieval".equalsIgnoreCase(status)) return "Retrieving Food";

        // Fallback for legacy "unlocked"
        if ("unlocked".equalsIgnoreCase(status)) return "Unlocked";

        // Capitalize default
        if (status != null && !status.isEmpty()) {
            return status.substring(0, 1).toUpperCase() + status.substring(1);
        }
        return "Unknown";
    }

    /**
     * Returns the hex color code for the status.
     */
    public String getStatusColor() {
        if (isPhysical && !isOnline()) {
            return "#9E9E9E"; // Grey (Offline)
        }
        if ("available".equalsIgnoreCase(status)) return "#4CAF50"; // Green
        if ("occupied".equalsIgnoreCase(status)) return "#9C27B0"; // Purple

        // NEW STATES
        if ("unlocked_delivery".equalsIgnoreCase(status)) return "#FF9800"; // Orange (Warning/Action Needed)
        if ("unlocked_retrieval".equalsIgnoreCase(status)) return "#2196F3"; // Blue (User Action)

        if ("unlocked".equalsIgnoreCase(status)) return "#FF9800"; // Legacy Orange

        return "#607D8B"; // Blue Grey (Default)
    }

    // --- 3. Getters and Setters ---

    public String getBoxId() { return boxId; }
    public void setBoxId(String boxId) { this.boxId = boxId; }

    public String getBoxNumber() { return boxNumber; }
    public void setBoxNumber(String boxNumber) { this.boxNumber = boxNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isPhysical() { return isPhysical; }
    public void setPhysical(boolean physical) { isPhysical = physical; }

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

    // --- 4. Inner Class for Statistics ---

    @IgnoreExtraProperties
    public static class BoxStatistics {
        private int totalDeliveries;
        private int totalRetrievals;
        private long lastUsed;

        public BoxStatistics() {
            this.totalDeliveries = 0;
            this.totalRetrievals = 0;
            this.lastUsed = 0;
        }

        public int getTotalDeliveries() { return totalDeliveries; }
        public void setTotalDeliveries(int totalDeliveries) { this.totalDeliveries = totalDeliveries; }

        public int getTotalRetrievals() { return totalRetrievals; }
        public void setTotalRetrievals(int totalRetrievals) { this.totalRetrievals = totalRetrievals; }

        public long getLastUsed() { return lastUsed; }
        public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }
    }
}