package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventory.model.Transaction;
import com.google.firebase.firestore.FirebaseFirestore;

public class ItemDetailActivity extends AppCompatActivity {

    private TextView tvName, tvQty, tvPrice;
    private Button btnStockIn, btnStockOut, btnEdit, btnDelete;
    private ImageView btnBack;
    private FirebaseFirestore db;
    private String itemId, itemName;
    private int currentQty;

    // SECURITY FIX: Default to "Staff" (Restricted).
    // You only get Admin powers if the Intent explicitly says "Admin".
    private String userRole = "Staff";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        db = FirebaseFirestore.getInstance();

        if (getIntent() != null) {
            itemId = getIntent().getStringExtra("itemId");
            itemName = getIntent().getStringExtra("itemName");
            currentQty = getIntent().getIntExtra("currentQty", 0);

            // Get Role from Previous Screen
            if (getIntent().hasExtra("USER_ROLE")) {
                userRole = getIntent().getStringExtra("USER_ROLE");
            }
        }

        // Bind Views
        tvName = findViewById(R.id.tvDetailName);
        tvQty = findViewById(R.id.tvDetailQty);
        tvPrice = findViewById(R.id.tvDetailPrice);
        btnStockIn = findViewById(R.id.btnStockIn);
        btnStockOut = findViewById(R.id.btnStockOut);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBack);

        tvName.setText(itemName);
        tvQty.setText(String.valueOf(currentQty));

        // SECURITY CHECK: HIDE BUTTONS IF STAFF
        if ("Staff".equalsIgnoreCase(userRole)) {
            // HIDE THEM
            btnEdit.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        } else {
            // SHOW THEM (Only for Admin)
            btnEdit.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);
        }

        setupButtons();
        refreshItemDetails();
    }

    private void setupButtons() {
        btnStockIn.setOnClickListener(v -> updateStock(1));
        btnStockOut.setOnClickListener(v -> updateStock(-1));

        // Extra Security: Even if button is visible via hack, block the click
        btnEdit.setOnClickListener(v -> {
            if("Admin".equalsIgnoreCase(userRole)) {
                Intent intent = new Intent(ItemDetailActivity.this, AddItemActivity.class);
                intent.putExtra("itemId", itemId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Admin Only", Toast.LENGTH_SHORT).show();
            }
        });

        btnDelete.setOnClickListener(v -> {
            if("Admin".equalsIgnoreCase(userRole)) {
                showDeleteConfirmation();
            } else {
                Toast.makeText(this, "Admin Only", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void refreshItemDetails() {
        db.collection("inventory").document(itemId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Double price = documentSnapshot.getDouble("price");
                        Long qty = documentSnapshot.getLong("quantity");
                        if (price != null) tvPrice.setText(String.format("$%.2f", price));
                        if (qty != null) {
                            currentQty = qty.intValue();
                            tvQty.setText(String.valueOf(currentQty));
                        }
                    }
                });
    }

    private void updateStock(int change) {
        int newQty = currentQty + change;
        if (newQty < 0) {
            Toast.makeText(this, "Stock cannot be negative", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("inventory").document(itemId).update("quantity", newQty)
                .addOnSuccessListener(aVoid -> {
                    currentQty = newQty;
                    tvQty.setText(String.valueOf(currentQty));
                    logTransaction(change);
                    Toast.makeText(this, (change>0?"Added":"Removed"), Toast.LENGTH_SHORT).show();
                });
    }

    private void logTransaction(int change) {
        Transaction transaction = new Transaction(itemName, (change > 0 ? "IN" : "OUT"), Math.abs(change));
        db.collection("transactions").add(transaction);
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("inventory").document(itemId).delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}