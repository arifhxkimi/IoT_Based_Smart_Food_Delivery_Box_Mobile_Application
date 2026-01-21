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

import java.util.List;

public class BoxAdapter extends RecyclerView.Adapter<BoxAdapter.BoxViewHolder> {

    private final Context context;
    private List<DeliveryBox> boxList;
    private final OnBoxClickListener listener;

    public interface OnBoxClickListener {
        void onBoxClick(DeliveryBox box);
    }

    public BoxAdapter(Context context, List<DeliveryBox> boxList, OnBoxClickListener listener) {
        this.context = context;
        this.boxList = boxList;
        this.listener = listener;
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
        holder.tvLocation.setText("Ready for delivery");

        boolean isOffline = box.isPhysical() && !box.isOnline();

        // ----------------------------
        // STATUS TEXT & COLOR
        // ----------------------------
        String statusText;
        int statusColor;
        int pillBackgroundColor;
        int iconRes;

        if (isOffline) {
            // 🔒 OFFLINE → looks normal, but unavailable
            statusText = "Unavailable";
            statusColor = Color.parseColor("#9E9E9E");
            pillBackgroundColor = Color.parseColor("#EEEEEE");
            iconRes = R.drawable.ic_lock;
        } else {
            // 🟢 ONLINE → normal state
            statusText = box.getStatusText();
            statusColor = Color.parseColor(box.getStatusColor());

            if (box.isAvailable()) {
                iconRes = R.drawable.ic_lock_open;
            } else {
                iconRes = R.drawable.ic_lock;
            }

            // Soft tinted pill background
            pillBackgroundColor = adjustAlpha(statusColor, 0.15f);
        }

        // ----------------------------
        // APPLY UI
        // ----------------------------
        holder.tvStatus.setText(statusText);
        holder.tvStatus.setTextColor(statusColor);

        holder.ivStatusIcon.setImageResource(iconRes);
        holder.ivStatusIcon.setColorFilter(statusColor);

        holder.statusContainer.setBackgroundTintList(
                ColorStateList.valueOf(pillBackgroundColor)
        );

        // IMPORTANT:
        // ❌ DO NOT grey out card
        // ❌ DO NOT show OFFLINE text on card
        holder.cardView.setAlpha(1f);

        // ----------------------------
        // CLICK HANDLING
        // ----------------------------
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBoxClick(box);
            }
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
