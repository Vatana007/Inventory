package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;

public class ItemDetailActivity extends AppCompatActivity {

    private TextView tvName, tvQty;
    private FirebaseFirestore db;
    private String itemId;
    private int currentQty;
    private String itemName;
    private double itemPrice = 0.0; // We need this for editing
    private int itemMinStock = 0;   // We need this for editing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        db = FirebaseFirestore.getInstance();

        // 1. Get Data from Intent
        itemId = getIntent().getStringExtra("itemId");
        itemName = getIntent().getStringExtra("itemName");
        currentQty = getIntent().getIntExtra("currentQty", 0);

        // Note: You should update your MainActivity adapter to pass these extra fields if possible,
        // but for now, we will load them from Firebase to be safe.

        tvName = findViewById(R.id.tvDetailName);
        tvQty = findViewById(R.id.tvDetailQty);

        tvName.setText(itemName);
        tvQty.setText(String.valueOf(currentQty));

        // 2. Setup Stock Buttons (+ / -)
        findViewById(R.id.btnAddStock).setOnClickListener(v -> updateStock(1));
        findViewById(R.id.btnRemoveStock).setOnClickListener(v -> updateStock(-1));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 3. Setup EDIT Button
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            // First, fetch the latest price/minStock to ensure we edit correct data
            db.collection("inventory").document(itemId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Intent intent = new Intent(ItemDetailActivity.this, AddItemActivity.class);
                    intent.putExtra("itemId", itemId);
                    intent.putExtra("name", itemName);
                    intent.putExtra("qty", currentQty);

                    // Get hidden fields from database
                    double price = documentSnapshot.getDouble("price") != null ? documentSnapshot.getDouble("price") : 0.0;
                    int minStock = documentSnapshot.getLong("minStock") != null ? documentSnapshot.getLong("minStock").intValue() : 0;

                    intent.putExtra("price", price);
                    intent.putExtra("minStock", minStock);

                    startActivity(intent);
                    finish(); // Close this detail screen so when we come back, it refreshes
                }
            });
        });

        // 4. Setup DELETE Button
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Item")
                    .setMessage("Are you sure you want to delete " + itemName + "?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteItem())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateStock(int change) {
        int newQty = currentQty + change;
        if (newQty < 0) return; // Prevent negative stock

        db.collection("inventory").document(itemId)
                .update("quantity", newQty)
                .addOnSuccessListener(aVoid -> {
                    currentQty = newQty;
                    tvQty.setText(String.valueOf(currentQty));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating", Toast.LENGTH_SHORT).show());
    }

    private void deleteItem() {
        db.collection("inventory").document(itemId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item Deleted", Toast.LENGTH_SHORT).show();
                    finish(); // Close screen and go back to list
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting", Toast.LENGTH_SHORT).show());
    }
}