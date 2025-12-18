package com.arif.smartfooddeliverybox.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    // ADD THIS METHOD - DashboardFragment calls it
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

        holder.tvBoxNumber.setText("Box " + box.getBoxNumber());

        // Show location as "Box X" format
        holder.tvLocation.setText("Box " + box.getBoxNumber());

        holder.tvStatus.setText(box.getStatusText());

        // Set status color
        try {
            holder.tvStatus.setTextColor(Color.parseColor(box.getStatusColor()));
            holder.cardView.setCardBackgroundColor(Color.parseColor(box.getStatusColor() + "20")); // 20% opacity
        } catch (IllegalArgumentException e) {
            holder.tvStatus.setTextColor(Color.GRAY);
        }

        // Show physical badge and online status
        if (box.isPhysical()) {
            holder.tvPhysical.setVisibility(View.VISIBLE);

            // Show online/offline indicator
            if (box.isOnline()) {
                holder.tvPhysical.setText("🟢 ONLINE");
                holder.tvPhysical.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                holder.tvPhysical.setText("🔴 OFFLINE");
                holder.tvPhysical.setTextColor(Color.parseColor("#F44336"));
            }
        } else {
            holder.tvPhysical.setVisibility(View.GONE);
        }

        // Dim card if offline
        if (box.isPhysical() && !box.isOnline()) {
            holder.cardView.setAlpha(0.6f);
        } else {
            holder.cardView.setAlpha(1.0f);
        }

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

    static class BoxViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvBoxNumber, tvLocation, tvStatus, tvPhysical;

        public BoxViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            tvBoxNumber = itemView.findViewById(R.id.tvBoxNumber);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvPhysical = itemView.findViewById(R.id.tvPhysical);
        }
    }
}