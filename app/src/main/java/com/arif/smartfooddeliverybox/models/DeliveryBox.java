package com.arif.smartfooddeliverybox.models;

public class DeliveryBox {
    private String boxId;
    private String boxNumber;
    private String status;
    private boolean isPhysical;
    private boolean enabled;
    private long lastHeartbeat;
    private String unlockedBy;
    private long unlockedAt;
    private long deliveredAt;
    private BoxStatistics statistics;

    // Empty constructor for Firebase
    public DeliveryBox() {
        this.statistics = new BoxStatistics();
    }

    public DeliveryBox(String boxId, String boxNumber, String status, boolean isPhysical) {
        this.boxId = boxId;
        this.boxNumber = boxNumber;
        this.status = status;
        this.isPhysical = isPhysical;
        this.enabled = true;
        this.statistics = new BoxStatistics();
    }

    // Getters and Setters
    public String getBoxId() { return boxId; }
    public void setBoxId(String boxId) { this.boxId = boxId; }

    public String getBoxNumber() { return boxNumber; }
    public void setBoxNumber(String boxNumber) { this.boxNumber = boxNumber; }

    public String getStatus() {
        if (status == null || status.isEmpty()) {
            return "available";
        }
        return status;
    }
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

    public BoxStatistics getStatistics() {
        if (statistics == null) statistics = new BoxStatistics();
        return statistics;
    }
    public void setStatistics(BoxStatistics statistics) { this.statistics = statistics; }

    // Helper methods
    public String getStatusColor() {
        switch (getStatus()) {
            case "available": return "#4CAF50"; // Green
            case "unlocked": return "#2196F3"; // Blue
            case "occupied": return "#9C27B0"; // Purple
            case "reserved": return "#FF9800"; // Orange
            default: return "#757575"; // Grey
        }
    }

    public String getStatusText() {
        switch (getStatus()) {
            case "available": return "Available";
            case "unlocked": return "Unlocked";
            case "occupied": return "Occupied";
            case "reserved": return "Reserved";
            default: return "Unknown";
        }
    }

    public boolean isAvailable() {
        return "available".equals(getStatus()) && enabled;
    }

    public boolean isOnline() {
        if (!isPhysical) return true;
        return (System.currentTimeMillis() - lastHeartbeat) < 60000; // 1 minute
    }

    // Nested class for statistics
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