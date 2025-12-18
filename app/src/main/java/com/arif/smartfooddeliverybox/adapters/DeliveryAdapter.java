package com.arif.smartfooddeliverybox.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arif.smartfooddeliverybox.R;
import com.arif.smartfooddeliverybox.models.Delivery;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DeliveryAdapter extends RecyclerView.Adapter<DeliveryAdapter.DeliveryViewHolder> {

    private Context context;
    private List<Delivery> deliveryList;
    private List<Delivery> filteredList;
    private OnDeliveryClickListener listener;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    // Add the interface
    public interface OnDeliveryClickListener {
        void onDeliveryClick(Delivery delivery);
    }

    // Keep your original no-argument constructor
    public DeliveryAdapter() {
        this.deliveryList = new ArrayList<>();
        this.filteredList = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    // ADD THIS NEW CONSTRUCTOR (with 3 parameters)
    public DeliveryAdapter(Context context, List<Delivery> filteredList, OnDeliveryClickListener listener) {
        this.context = context;
        this.deliveryList = new ArrayList<>();
        this.filteredList = filteredList != null ? filteredList : new ArrayList<>();
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    public void setDeliveryList(List<Delivery> deliveryList) {
        this.deliveryList = deliveryList;
        if (filteredList == null) {
            filteredList = new ArrayList<>();
        }
        filteredList.clear();
        filteredList.addAll(deliveryList);
        notifyDataSetChanged();
    }

    // Add filter method
    public void filter(String filterStatus) {
        if (filteredList == null) {
            filteredList = new ArrayList<>();
        }
        filteredList.clear();

        if ("all".equals(filterStatus)) {
            filteredList.addAll(deliveryList);
        } else {
            for (Delivery delivery : deliveryList) {
                if (delivery.getStatus().equals(filterStatus)) {
                    filteredList.add(delivery);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeliveryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivery, parent, false);
        return new DeliveryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeliveryViewHolder holder, int position) {
        List<Delivery> listToUse = (filteredList != null && !filteredList.isEmpty())
                ? filteredList : deliveryList;

        Delivery delivery = listToUse.get(position);

        // Set date and time
        holder.tvDeliveryDate.setText(dateFormat.format(new Date(delivery.getDeliveryTime())));
        holder.tvDeliveryTime.setText(timeFormat.format(new Date(delivery.getDeliveryTime())));

        // Set status
        if ("completed".equals(delivery.getStatus())) {
            holder.tvStatus.setText("Collected");
            holder.tvStatus.setBackgroundColor(holder.itemView.getContext()
                    .getColor(R.color.status_unlocked));

            // Show collection time
            holder.tvCollectionTime.setText(timeFormat.format(new Date(delivery.getCollectionTime())));

            // Calculate and show duration
            long duration = delivery.getDuration();
            String durationStr = formatDuration(duration);
            holder.tvDuration.setText(durationStr);
            holder.tvDuration.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatus.setText("Pending");
            holder.tvStatus.setBackgroundColor(holder.itemView.getContext()
                    .getColor(R.color.status_warning));
            holder.tvCollectionTime.setText("Not collected yet");
            holder.tvDuration.setVisibility(View.GONE);
        }

        // Add click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeliveryClick(delivery);
            }
        });
    }

    @Override
    public int getItemCount() {
        if (filteredList != null && !filteredList.isEmpty()) {
            return filteredList.size();
        }
        return deliveryList.size();
    }

    private String formatDuration(long milliseconds) {
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        } else {
            return String.format(Locale.getDefault(), "%dm", minutes);
        }
    }

    static class DeliveryViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeliveryDate, tvDeliveryTime, tvStatus, tvCollectionTime, tvDuration;

        public DeliveryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeliveryDate = itemView.findViewById(R.id.tvDeliveryDate);
            tvDeliveryTime = itemView.findViewById(R.id.tvDeliveryTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvCollectionTime = itemView.findViewById(R.id.tvCollectionTime);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }
}