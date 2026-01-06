package com.arif.smartfooddeliverybox.adapters;

import android.content.Context;
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

    private Context context;
    private List<DeliveryBox> boxList;
    private OnBoxClickListener listener;

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
        // Ensure this points to your updated layout file
        View view = LayoutInflater.from(context).inflate(R.layout.item_box, parent, false);
        return new BoxViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BoxViewHolder holder, int position) {
        DeliveryBox box = boxList.get(position);

        // 1. Set Basic Info
        holder.tvBoxNumber.setText(box.getName());
        holder.tvLocation.setText("Tap to manage");

        // 2. Set Status Text
        holder.tvStatus.setText(box.getStatusText());

        // 3. Status Styling (Color & Icons)
        String colorHex = box.getStatusColor();
        int color = Color.parseColor(colorHex);

        // Text Color
        holder.tvStatus.setTextColor(color);

        // Status Icon Logic
        if (box.isAvailable()) {
            // Ensure you have ic_lock_open drawable
            holder.ivStatusIcon.setImageResource(R.drawable.ic_lock_open);
        } else {
            // Ensure you have ic_lock drawable
            holder.ivStatusIcon.setImageResource(R.drawable.ic_lock);
        }
        holder.ivStatusIcon.setColorFilter(color);

        // 4. Tint the Status Pill Background
        // This applies a light version (20% opacity) of the status color to the pill
        try {
            holder.statusContainer.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color + 0x20000000)
            );
        } catch (Exception e) {
            // Fallback if color math fails
            holder.statusContainer.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
            );
        }

        // 5. Online/Offline Indicator (Optional Badge)
        if (box.isPhysical()) {
            holder.tvPhysical.setVisibility(View.VISIBLE);
            if (box.isOnline()) {
                holder.tvPhysical.setText("🟢 ONLINE");
                holder.tvPhysical.setTextColor(Color.parseColor("#4CAF50"));
                holder.cardView.setAlpha(1.0f);
            } else {
                holder.tvPhysical.setText("🔴 OFFLINE");
                holder.tvPhysical.setTextColor(Color.parseColor("#F44336"));
                holder.cardView.setAlpha(0.7f);
            }
        } else {
            holder.tvPhysical.setVisibility(View.GONE);
            holder.cardView.setAlpha(1.0f);
        }

        // 6. Click Listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBoxClick(box);
        });
    }

    @Override
    public int getItemCount() {
        return boxList != null ? boxList.size() : 0;
    }

    static class BoxViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvBoxNumber, tvLocation, tvStatus, tvPhysical;
        ImageView ivStatusIcon;
        View statusContainer; // The Layout for the status pill

        public BoxViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            tvBoxNumber = itemView.findViewById(R.id.tvBoxNumber);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvPhysical = itemView.findViewById(R.id.tvPhysical);

            // New views from updated item_box.xml
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            statusContainer = itemView.findViewById(R.id.statusContainer);
        }
    }
}