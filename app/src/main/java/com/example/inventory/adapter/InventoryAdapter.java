package com.example.inventory.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory.R;
import com.example.inventory.model.InventoryItem;
import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    private List<InventoryItem> items;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
    }

    public InventoryAdapter(List<InventoryItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = items.get(position);

        holder.name.setText(item.getName());
        holder.qty.setText("Qty: " + item.getQuantity());
        holder.tvPrice.setText("$" + String.format("%.2f", item.getPrice()));

        // THE FIX: Pull the date from the database and set it
        String dateStr = item.getDateAdded();
        if (dateStr != null && !dateStr.isEmpty()) {
            holder.date.setText("Added: " + dateStr);
        } else {
            holder.date.setText("Added: Unknown");
        }

        // Traffic Light Logic:
        if (item.getQuantity() <= 0) {
            // OUT OF STOCK (0 or less) -> RED
            holder.statusIndicator.setBackgroundColor(Color.RED);
        } else if (item.getQuantity() > 0 && item.getQuantity() <= 5) {
            // LOW STOCK (1 to 5) -> YELLOW
            holder.statusIndicator.setBackgroundColor(Color.parseColor("#FFC107"));
        } else {
            // IN STOCK (Greater than 5) -> GREEN
            holder.statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        }


        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, qty, date, tvPrice; // Added tvPrice here
        View statusIndicator;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvName);
            qty = itemView.findViewById(R.id.tvQty);
            date = itemView.findViewById(R.id.tvDate);
            tvPrice = itemView.findViewById(R.id.tvPrice); // Connected it to the XML ID here
            statusIndicator = itemView.findViewById(R.id.viewStatus);
        }
    }

}