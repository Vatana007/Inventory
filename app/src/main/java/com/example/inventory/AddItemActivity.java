package com.example.inventory;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

// Make sure this import matches your actual Model package name
import com.example.inventory.model.InventoryItem;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AddItemActivity extends AppCompatActivity {

    private TextInputEditText etName, etQty, etPrice, etMinStock;
    private FirebaseFirestore db;

    // Variables to handle "Edit Mode"
    private String existingItemId = null;
    private String originalDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        db = FirebaseFirestore.getInstance();

        // 1. Initialize Views
        etName = findViewById(R.id.etName);
        etQty = findViewById(R.id.etQty);
        etPrice = findViewById(R.id.etPrice);
        etMinStock = findViewById(R.id.etMinStock);

        Button btnSave = findViewById(R.id.btnSave);
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);

        // 2. Setup Back Button
        btnBack.setOnClickListener(v -> finish());

        // 3. CHECK: Are we editing an existing item?
        if (getIntent().hasExtra("itemId")) {
            existingItemId = getIntent().getStringExtra("itemId");

            // Switch UI to "Edit Mode"
            tvTitle.setText("Edit Product");
            btnSave.setText("Update Product");

            // Pre-fill the data from the Intent
            etName.setText(getIntent().getStringExtra("name"));
            etQty.setText(String.valueOf(getIntent().getIntExtra("qty", 0)));
            etPrice.setText(String.valueOf(getIntent().getDoubleExtra("price", 0.0)));
            etMinStock.setText(String.valueOf(getIntent().getIntExtra("minStock", 0)));

            // Preserve the original date if passed
            if(getIntent().hasExtra("dateAdded")){
                originalDate = getIntent().getStringExtra("dateAdded");
            }
        }

        // 4. Save Button Click
        btnSave.setOnClickListener(v -> saveItem());
    }

    private void saveItem() {
        String name = etName.getText().toString().trim();
        String qtyStr = etQty.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String minStockStr = etMinStock.getText().toString().trim();

        // Validation
        if (name.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parse Numbers
        int qty = Integer.parseInt(qtyStr);
        double price = Double.parseDouble(priceStr);
        int minStock = minStockStr.isEmpty() ? 0 : Integer.parseInt(minStockStr);

        // Date Logic
        String dateToSave;
        if (originalDate != null) {
            dateToSave = originalDate; // Keep original date if editing
        } else {
            // Generate Today's Date (Format: Oct 24, 2023)
            dateToSave = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        }

        // Create the Item Object (Using the NEW Constructor with Date)
        // Ensure your InventoryItem.java model has this constructor!
        InventoryItem item = new InventoryItem(name, qty, price, minStock, dateToSave);

        // Save to Firebase
        if (existingItemId != null) {
            // UPDATE Existing Item
            db.collection("inventory").document(existingItemId)
                    .set(item) // Overwrites data with the new values
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Item Updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Update Failed", Toast.LENGTH_SHORT).show());
        } else {
            // ADD New Item
            db.collection("inventory").add(item)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Item Added!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error adding item", Toast.LENGTH_SHORT).show());
        }
    }
}