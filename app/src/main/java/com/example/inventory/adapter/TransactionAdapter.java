package com.example.inventory.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory.R;
import com.example.inventory.model.Transaction;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<Transaction> list;

    public TransactionAdapter(List<Transaction> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction item = list.get(position);

        holder.tvName.setText(item.getItemName());

        // Date Format
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy  •  hh:mm a", Locale.getDefault());
        String dateStr = (item.getTimestamp() != null) ? sdf.format(item.getTimestamp()) : "N/A";
        holder.tvDate.setText(dateStr);

        // --- COLOR LOGIC ---
        if ("OUT".equals(item.getType())) {
            // STOCK OUT STYLE (Red)
            holder.tvQty.setText("-" + item.getQuantityChanged());
            holder.tvQty.setTextColor(Color.parseColor("#C62828")); // Dark Red Text

            // Change the background tint of the pill
            holder.tvQty.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFEBEE"))); // Light Red BG

            // Change the sidebar strip color
            holder.imgType.setColorFilter(Color.parseColor("#D32F2F")); // Red Strip

        } else {
            // STOCK IN STYLE (Green)
            holder.tvQty.setText("+" + item.getQuantityChanged());
            holder.tvQty.setTextColor(Color.parseColor("#2E7D32")); // Dark Green Text

            // Change the background tint of the pill
            holder.tvQty.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9"))); // Light Green BG

            // Change the sidebar strip color
            holder.imgType.setColorFilter(Color.parseColor("#43A047")); // Green Strip
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvQty;
        ImageView imgType;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTransName);
            tvDate = itemView.findViewById(R.id.tvTransDate);
            tvQty = itemView.findViewById(R.id.tvTransQty);
            imgType = itemView.findViewById(R.id.imgTransType);
        }
    }
}