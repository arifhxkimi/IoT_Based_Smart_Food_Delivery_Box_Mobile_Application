package com.arif.smartfooddeliverybox.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.models.DeliveryBox;
import com.arif.smartfooddeliverybox.utils.FirebaseHelper;

import java.util.List;

public class BoxAdapter extends RecyclerView.Adapter<BoxAdapter.BoxViewHolder> {

    private final Context context;
    private List<DeliveryBox> boxList;
    private final OnBoxClickListener listener;

    private final FirebaseHelper firebaseHelper;
    private final String currentUserId;

    public interface OnBoxClickListener {
        void onBoxClick(DeliveryBox box);
    }

    public BoxAdapter(Context context, List<DeliveryBox> boxList, OnBoxClickListener listener) {
        this.context = context;
        this.boxList = boxList;
        this.listener = listener;

        firebaseHelper = FirebaseHelper.getInstance();
        currentUserId = firebaseHelper.getCurrentUserId();
    }

    public void setBoxList(List<DeliveryBox> boxList) {
        this.boxList = boxList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BoxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_box, parent, false);
        return new BoxViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BoxViewHolder holder, int position) {
        DeliveryBox box = boxList.get(position);

        // ----------------------------
        // BASIC INFO
        // ----------------------------
        holder.tvBoxNumber.setText(box.getNameSafe());

        // ----------------------------
        // OFFLINE CHECK (physical only)
        // ----------------------------
        boolean isOffline = box.isPhysical() && !box.isOnline();

        // ----------------------------
        // OWNERSHIP CHECK
        // ----------------------------
        String ownerId = box.getUnlockedBy();
        boolean hasOwner = ownerId != null && !ownerId.trim().isEmpty();
        boolean isMine = hasOwner && currentUserId != null && currentUserId.equals(ownerId);

        // ----------------------------
        // STATUS
        // ----------------------------
        String rawStatus = box.getStatus() == null ? "" : box.getStatus().toLowerCase();

        String locationText; // your second line
        String statusText;   // pill text

        int statusColor;
        int pillBackgroundColor;
        int iconRes;

        if (isOffline) {
            // OFFLINE (Unavailable)
            locationText = "Device offline";
            statusText = "Unavailable";
            statusColor = Color.parseColor("#9E9E9E");
            pillBackgroundColor = Color.parseColor("#EEEEEE");
            iconRes = R.drawable.ic_lock;
        } else {

            // Location line (contextual)
            switch (rawStatus) {
                case "idle":
                case "available":
                    locationText = "Ready for delivery";
                    break;

                case "unlocked_delivery":
                case "unlocked_for_delivery":
                    locationText = "Waiting for rider";
                    break;

                case "occupied":
                    locationText = isMine ? "Your food is inside" : "Box is in use";
                    break;

                case "unlocked_for_retrieval":
                case "unlocked_retrieval":
                case "retrieval_in_progress":
                    locationText = isMine ? "Retrieving your food" : "Retrieval in progress";
                    break;

                default:
                    locationText = "Status: " + box.getStatusText();
                    break;
            }

            // Pill text (short)
            // ✅ Important UX: show "In Use" if not mine, so user B knows
            if ((rawStatus.equals("occupied")
                    || rawStatus.equals("unlocked_for_retrieval")
                    || rawStatus.equals("unlocked_retrieval")
                    || rawStatus.equals("retrieval_in_progress"))
                    && hasOwner && !isMine) {

                statusText = "In Use";
                statusColor = Color.parseColor("#F44336"); // red-ish
                pillBackgroundColor = adjustAlpha(statusColor, 0.12f);
                iconRes = R.drawable.ic_lock;

            } else {
                // Default uses your model mapping
                statusText = box.getStatusText();

                // Use your model color, but safeguard if parsing fails
                int parsed;
                try {
                    parsed = Color.parseColor(box.getStatusColor());
                } catch (Exception e) {
                    parsed = Color.parseColor("#607D8B");
                }
                statusColor = parsed;
                pillBackgroundColor = adjustAlpha(statusColor, 0.15f);

                // Icon
                if (box.isAvailable()) iconRes = R.drawable.ic_lock_open;
                else iconRes = R.drawable.ic_lock;
            }
        }

        // ----------------------------
        // APPLY UI
        // ----------------------------
        holder.tvLocation.setText(locationText);

        holder.tvStatus.setText(statusText);
        holder.tvStatus.setTextColor(statusColor);

        holder.ivStatusIcon.setImageResource(iconRes);
        holder.ivStatusIcon.setColorFilter(statusColor);

        holder.statusContainer.setBackgroundTintList(ColorStateList.valueOf(pillBackgroundColor));

        holder.cardView.setAlpha(1f);

        // ----------------------------
        // CLICK HANDLING
        // ----------------------------
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBoxClick(box);
        });
    }

    @Override
    public int getItemCount() {
        return boxList != null ? boxList.size() : 0;
    }

    // ----------------------------
    // VIEW HOLDER
    // ----------------------------
    static class BoxViewHolder extends RecyclerView.ViewHolder {

        CardView cardView;
        TextView tvBoxNumber, tvLocation, tvStatus;
        ImageView ivStatusIcon;
        View statusContainer;

        public BoxViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            tvBoxNumber = itemView.findViewById(R.id.tvBoxNumber);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            statusContainer = itemView.findViewById(R.id.statusContainer);
        }
    }

    // ----------------------------
    // HELPER: COLOR ALPHA
    // ----------------------------
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }
}
