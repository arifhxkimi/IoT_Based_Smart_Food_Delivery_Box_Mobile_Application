package com.arif.smartfooddeliverybox.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Delivery {
    private String deliveryId;
    private String userId;
    private String trackingNo;
    private String boxAssigned; // box1, box2, etc.
    private String status; // pending, in_box, completed, cancelled
    private long expectedTime;
    private long deliveredTime;
    private long completedTime;
    private String items;
    private String deliveryService;
    private String notes;

    public Delivery() {
        // Required empty constructor for Firebase
    }

    public Delivery(String deliveryId, String userId, String trackingNo, String status) {
        this.deliveryId = deliveryId;
        this.userId = userId;
        this.trackingNo = trackingNo;
        this.status = status;
        this.boxAssigned = null;
        this.expectedTime = 0;
        this.deliveredTime = 0;
        this.completedTime = 0;
        this.items = "";
        this.deliveryService = "";
        this.notes = "";
    }

    // Getters and Setters
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public String getBoxAssigned() { return boxAssigned; }
    public void setBoxAssigned(String boxAssigned) { this.boxAssigned = boxAssigned; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getExpectedTime() { return expectedTime; }
    public void setExpectedTime(long expectedTime) { this.expectedTime = expectedTime; }

    public long getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(long deliveredTime) { this.deliveredTime = deliveredTime; }

    public long getCompletedTime() { return completedTime; }
    public void setCompletedTime(long completedTime) { this.completedTime = completedTime; }

    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }

    public String getDeliveryService() { return deliveryService; }
    public void setDeliveryService(String deliveryService) { this.deliveryService = deliveryService; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // Helper methods
    public String getFormattedExpectedTime() {
        if (expectedTime == 0) return "Not set";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(expectedTime));
    }

    public String getFormattedDeliveredTime() {
        if (deliveredTime == 0) return "Not delivered";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(deliveredTime));
    }

    public String getStatusColor() {
        switch (status) {
            case "pending": return "#FF9800"; // Orange
            case "in_box": return "#2196F3"; // Blue
            case "completed": return "#4CAF50"; // Green
            case "cancelled": return "#F44336"; // Red
            default: return "#757575"; // Grey
        }
    }

    public String getStatusText() {
        switch (status) {
            case "pending": return "Pending";
            case "in_box": return "In Box";
            case "completed": return "Completed";
            case "cancelled": return "Cancelled";
            default: return "Unknown";
        }
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isInBox() {
        return "in_box".equals(status);
    }

    public boolean hasBoxAssigned() {
        return boxAssigned != null && !boxAssigned.isEmpty();
    }

    // NEW METHODS - Add these to fix the errors

    /**
     * Returns the delivery time (when parcel was delivered to box)
     * This is an alias for getDeliveredTime() for compatibility with DeliveryAdapter
     */
    public long getDeliveryTime() {
        return deliveredTime;
    }

    /**
     * Returns the collection time (when user collected the parcel)
     * This is an alias for getCompletedTime() for compatibility with DeliveryAdapter
     */
    public long getCollectionTime() {
        return completedTime;
    }

    /**
     * Calculates and returns the duration between delivery and collection in milliseconds
     * Duration = completedTime - deliveredTime
     */
    public long getDuration() {
        if (completedTime > 0 && deliveredTime > 0) {
            return completedTime - deliveredTime;
        }
        return 0;
    }
}