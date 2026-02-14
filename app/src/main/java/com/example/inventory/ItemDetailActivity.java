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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class ItemDetailActivity extends AppCompatActivity {

    private TextView tvName, tvQty, tvPrice;
    private Button btnStockIn, btnStockOut, btnEdit, btnDelete;
    private ImageView btnBack;
    private FirebaseFirestore db;
    private String itemId, itemName;
    private int currentQty;

    // FIX: Default to Staff. Role is strictly passed via Intent to prevent mix-ups.
    private String userRole = "Staff";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        db = FirebaseFirestore.getInstance(); //

        // 1. DATA RETRIEVAL & ROLE PERSISTENCE
        if (getIntent() != null) {
            itemId = getIntent().getStringExtra("itemId"); //
            itemName = getIntent().getStringExtra("itemName"); //
            currentQty = getIntent().getIntExtra("currentQty", 0); //

            // Catch the Role from the previous screen to prevent dashboard reverting to Staff
            if (getIntent().hasExtra("USER_ROLE")) {
                userRole = getIntent().getStringExtra("USER_ROLE"); //
            }
        }

        // Bind Views
        tvName = findViewById(R.id.tvDetailName); //
        tvQty = findViewById(R.id.tvDetailQty); //
        tvPrice = findViewById(R.id.tvDetailPrice); //
        btnStockIn = findViewById(R.id.btnStockIn); //
        btnStockOut = findViewById(R.id.btnStockOut); //
        btnEdit = findViewById(R.id.btnEdit); //
        btnDelete = findViewById(R.id.btnDelete); //
        btnBack = findViewById(R.id.btnBack); //

        // 2. APPLY ROLE-BASED UI PROTECTION
        applyPermissions();

        // 3. INITIALIZE LISTENERS
        setupButtons();
        observeItemRealTime();
    }

    private void applyPermissions() {
        boolean isAdmin = "Admin".equalsIgnoreCase(userRole);

        // Hide Admin-only buttons from Staff
        btnEdit.setVisibility(isAdmin ? View.VISIBLE : View.GONE); //
        btnDelete.setVisibility(isAdmin ? View.VISIBLE : View.GONE); //

        // Reference the Admin layout containers from your XML
        View adminHeader = findViewById(R.id.tvAdminActionsHeader);
        View adminLayout = findViewById(R.id.layoutAdminActions);

        if (adminHeader != null) adminHeader.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (adminLayout != null) adminLayout.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
    }

    private void observeItemRealTime() {
        // Real-time listener: UI updates automatically when DB changes
        db.collection("inventory").document(itemId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        itemName = snapshot.getString("name"); //
                        Double price = snapshot.getDouble("price"); //
                        Long qty = snapshot.getLong("quantity"); //

                        tvName.setText(itemName); //
                        if (price != null) tvPrice.setText(String.format("$%.2f", price)); //
                        if (qty != null) {
                            currentQty = qty.intValue(); //
                            tvQty.setText(String.valueOf(currentQty)); //
                        }
                    }
                });
    }

    private void setupButtons() {
        // Automatic stock-in/out logic using Buttons
        btnStockIn.setOnClickListener(v -> updateStockAutomatic(1)); //
        btnStockOut.setOnClickListener(v -> {
            if (currentQty > 0) updateStockAutomatic(-1); //
            else Toast.makeText(this, "Stock cannot be negative", Toast.LENGTH_SHORT).show(); //
        });

        // Navigation back logic: ensures role is passed back to MainActivity to fix "Staff reset" bug
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("USER_ROLE", userRole); // Crucial: Re-pass the role back to Home
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Admin-only actions
        btnEdit.setOnClickListener(v -> {
            if ("Admin".equalsIgnoreCase(userRole)) {
                Intent intent = new Intent(this, AddItemActivity.class);
                intent.putExtra("itemId", itemId);
                intent.putExtra("USER_ROLE", userRole); // Keep role persistent
                startActivity(intent);
            }
        });

        btnDelete.setOnClickListener(v -> {
            if ("Admin".equalsIgnoreCase(userRole)) showDeleteConfirmation();
        });
    }

    private void updateStockAutomatic(int change) {
        // ATOMIC INCREMENT: Safe server-side math to prevent data mixing
        db.collection("inventory").document(itemId)
                .update("quantity", FieldValue.increment(change))
                .addOnSuccessListener(aVoid -> {
                    logTransaction(change); //
                    String action = change > 0 ? "Stock Added" : "Stock Removed";
                    Toast.makeText(this, action, Toast.LENGTH_SHORT).show();
                });
    }

    private void logTransaction(int change) {
        Transaction transaction = new Transaction(itemName, (change > 0 ? "IN" : "OUT"), Math.abs(change)); //
        db.collection("transactions").add(transaction); //
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Product") //
                .setMessage("Are you sure?") //
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("inventory").document(itemId).delete() //
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); //
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null) //
                .show();
    }
}