package com.example.inventory;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;


import com.example.inventory.model.InventoryItem;
import com.example.inventory.model.Batch; // IMPORT BATCH MODEL
import com.example.inventory.db.LocalDatabaseHelper; // IMPORT SQLITE HELPER
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AddItemActivity extends AppCompatActivity {

    private TextInputEditText etName, etCategory, etQty, etPrice, etSale, etBarcode;
    private ImageView ivBarcodePreview;
    private FirebaseFirestore db;
    private LocalDatabaseHelper localDb; // SQLITE

    private String existingItemId = null;
    private String originalDate = null;
    private String originalBarcode = "";
    private int currentMinStock = 5;

    private void generateBarcode(String text) {
        if (text.isEmpty()) {
            ivBarcodePreview.setVisibility(View.GONE);
            ivBarcodePreview.setImageBitmap(null);
            return;
        }

        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            // Encode the text into a CODE_128 Barcode (Standard retail barcode)
            Bitmap bitmap = barcodeEncoder.encodeBitmap(text, BarcodeFormat.CODE_128, 600, 200);

            ivBarcodePreview.setImageBitmap(bitmap);
            ivBarcodePreview.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
            ivBarcodePreview.setVisibility(View.GONE);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        db = FirebaseFirestore.getInstance();
        localDb = new LocalDatabaseHelper(this); // INIT SQLITE

        etName = findViewById(R.id.etName);
        etCategory = findViewById(R.id.etCategory);
        etQty = findViewById(R.id.etQty);
        etPrice = findViewById(R.id.etPrice);
        etSale = findViewById(R.id.etSale);
        etBarcode = findViewById(R.id.etBarcode);
        ivBarcodePreview = findViewById(R.id.ivBarcodePreview);

        etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                generateBarcode(s.toString().trim());
            }
        });

        Button btnSave = findViewById(R.id.btnSave);
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);

        btnBack.setOnClickListener(v -> finish());

        if (getIntent().hasExtra("itemId")) {
            existingItemId = getIntent().getStringExtra("itemId");

            if(tvTitle != null) tvTitle.setText(getString(R.string.title_edit_product));
            btnSave.setText(getString(R.string.btn_update_product));

            etName.setText(getIntent().getStringExtra("name"));

            if (getIntent().hasExtra("category")) {
                etCategory.setText(getIntent().getStringExtra("category"));
            }

            etQty.setText(String.valueOf(getIntent().getIntExtra("qty", 0)));
            etPrice.setText(String.valueOf(getIntent().getDoubleExtra("price", 0.0)));

            if (getIntent().hasExtra("sale")) {
                etSale.setText(String.valueOf(getIntent().getDoubleExtra("sale", 0.0)));
            }

            if (getIntent().hasExtra("minStock")) {
                currentMinStock = getIntent().getIntExtra("minStock", 5);
            }

            if(getIntent().hasExtra("dateAdded")){
                originalDate = getIntent().getStringExtra("dateAdded");
            }
            if(getIntent().hasExtra("barcode")){
                originalBarcode = getIntent().getStringExtra("barcode");
                etBarcode.setText(originalBarcode); // This will automatically trigger the image generation!
            }
        } else {
            if(tvTitle != null) tvTitle.setText(getString(R.string.title_add_product));
            btnSave.setText(getString(R.string.btn_save_product));
        }

        btnSave.setOnClickListener(v -> saveItem());
    }

    private void saveItem() {
        String name = etName.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String qtyStr = etQty.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String saleStr = etSale.getText().toString().trim();

        if (name.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_fill_required), Toast.LENGTH_SHORT).show();
            return;
        }

        int qty = Integer.parseInt(qtyStr);
        double price = Double.parseDouble(priceStr);
        double sale = saleStr.isEmpty() ? 0.0 : Double.parseDouble(saleStr);

        String dateToSave;
        if (originalDate != null && !originalDate.isEmpty()) {
            dateToSave = originalDate;
        } else {
            dateToSave = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        }

        String finalBarcode = etBarcode.getText().toString().trim();
        InventoryItem item = new InventoryItem(name, qty, price, sale, category, currentMinStock, dateToSave, finalBarcode);

        // CASE 1: UPDATE EXISTING
        if (existingItemId != null) {
            db.collection("inventory").document(existingItemId)
                    .set(item)
                    .addOnSuccessListener(aVoid -> {
                        // SQLITE LOG
                        localDb.logAction("UPDATE", name);

                        Toast.makeText(this, getString(R.string.msg_item_updated), Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.msg_update_failed), Toast.LENGTH_SHORT).show());
        }
        // CASE 2: ADD NEW
        else {
            db.collection("inventory").add(item)
                    .addOnSuccessListener(documentReference -> {
                        String newItemId = documentReference.getId();

                        // --- FIFO BONUS: Create the First Batch ---
                        // We create a sub-collection "batches" for this item
                        Batch initialBatch = new Batch(qty);
                        db.collection("inventory").document(newItemId)
                                .collection("batches")
                                .add(initialBatch);
                        // ------------------------------------------

                        // SQLITE LOG
                        localDb.logAction("CREATE", name);

                        Toast.makeText(this, getString(R.string.msg_item_added), Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.msg_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show());
        }
    }
}
