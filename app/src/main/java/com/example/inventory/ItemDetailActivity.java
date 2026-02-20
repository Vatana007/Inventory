package com.example.inventory;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventory.model.Transaction;
import com.example.inventory.model.Batch; // IMPORT BATCH
import com.example.inventory.db.LocalDatabaseHelper; // IMPORT SQLITE HELPER
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class ItemDetailActivity extends AppCompatActivity {

    private TextView tvName, tvPrice;
    private EditText etDetailQty;
    private Button btnStockIn, btnStockOut, btnEdit, btnDelete;
    private ImageView btnBack;
    private LinearLayout layoutAdminActions;

    private FirebaseFirestore db;
    private LocalDatabaseHelper localDb; // SQLITE
    private ListenerRegistration itemListener;

    private String itemId, itemName;
    private int currentQty;
    private String userRole = "Staff";

    // Item Details for Edit
    private String currentCategory = "";
    private double currentSale = 0.0;
    private int currentMinStock = 5;
    private String currentDateAdded = "";
    private String currentBarcode = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        db = FirebaseFirestore.getInstance();
        localDb = new LocalDatabaseHelper(this); // INIT SQLITE

        if (getIntent() != null) {
            itemId = getIntent().getStringExtra("itemId");
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        if (userRole == null) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            userRole = prefs.getString("USER_ROLE", "Staff");
        }

        initViews();
        checkPermissions();
        setupClickListeners();
        setupKeyboardAutoSave();
        startItemUpdates();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvDetailName);
        tvPrice = findViewById(R.id.tvDetailPrice);
        etDetailQty = findViewById(R.id.etDetailQty);

        btnStockIn = findViewById(R.id.btnStockIn);
        btnStockOut = findViewById(R.id.btnStockOut);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBack);
        layoutAdminActions = findViewById(R.id.layoutAdminActions);
    }

    private void checkPermissions() {
        if ("Admin".equalsIgnoreCase(userRole)) {
            layoutAdminActions.setVisibility(View.VISIBLE);
        } else {
            layoutAdminActions.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Stock In (+1)
        btnStockIn.setOnClickListener(v -> updateStockByIncrement(1));

        // Stock Out (-1)
        btnStockOut.setOnClickListener(v -> {
            if (currentQty > 0) {
                updateStockByIncrement(-1);
            } else {
                Toast.makeText(this, getString(R.string.msg_stock_zero), Toast.LENGTH_SHORT).show();
            }
        });

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddItemActivity.class);
            intent.putExtra("itemId", itemId);
            intent.putExtra("USER_ROLE", userRole);
            intent.putExtra("name", itemName);
            intent.putExtra("qty", currentQty);
            intent.putExtra("category", currentCategory);
            intent.putExtra("sale", currentSale);
            intent.putExtra("minStock", currentMinStock);
            intent.putExtra("dateAdded", currentDateAdded);
            intent.putExtra("barcode", currentBarcode);

            String priceStr = tvPrice.getText().toString().replace("$", "");
            try {
                intent.putExtra("price", Double.parseDouble(priceStr));
            } catch (Exception e) {
                intent.putExtra("price", 0.0);
            }
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void setupKeyboardAutoSave() {
        etDetailQty.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                etDetailQty.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etDetailQty.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        etDetailQty.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveTypedCustomQuantity();
        });
    }

    private void startItemUpdates() {
        if (itemId == null) return;

        itemListener = db.collection("inventory").document(itemId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    itemName = snapshot.getString("name");
                    Long qtyLong = snapshot.getLong("quantity");
                    currentQty = (qtyLong != null) ? qtyLong.intValue() : 0;

                    Double priceDouble = snapshot.getDouble("price");
                    double price = (priceDouble != null) ? priceDouble : 0.0;

                    currentCategory = snapshot.getString("category");
                    if (currentCategory == null) currentCategory = "";

                    Double saleDouble = snapshot.getDouble("sale");
                    currentSale = (saleDouble != null) ? saleDouble : 0.0;

                    Long minStockLong = snapshot.getLong("minStock");
                    currentMinStock = (minStockLong != null) ? minStockLong.intValue() : 5;

                    currentDateAdded = snapshot.getString("dateAdded");
                    currentBarcode = snapshot.getString("barcode");

                    if (itemName != null) tvName.setText(itemName);
                    tvPrice.setText("$" + String.format("%.2f", price));

                    if (!etDetailQty.hasFocus()) {
                        etDetailQty.setText(String.valueOf(currentQty));
                    }
                });
    }

    /**
     * MAIN STOCK UPDATE METHOD
     */
    private void updateStockByIncrement(int change) {
        db.collection("inventory").document(itemId)
                .update("quantity", FieldValue.increment(change))
                .addOnSuccessListener(aVoid -> {
                    logTransactionToHistory(change);

                    // SQLITE LOG
                    String type = (change > 0) ? "STOCK_IN" : "STOCK_OUT";
                    localDb.logAction(type, itemName);

                    // FIFO BATCH LOGIC
                    if (change > 0) {
                        addBatch(change);
                    } else {
                        processFifoStockOut(Math.abs(change));
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.msg_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show());
    }

    private void saveTypedCustomQuantity() {
        String input = etDetailQty.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            etDetailQty.setText(String.valueOf(currentQty));
            return;
        }

        try {
            int newQty = Integer.parseInt(input);
            if (newQty == currentQty) return;

            int difference = newQty - currentQty;
            currentQty = newQty;

            db.collection("inventory").document(itemId)
                    .update("quantity", newQty)
                    .addOnSuccessListener(aVoid -> {
                        logTransactionToHistory(difference);
                        localDb.logAction("MANUAL_ADJUST", itemName);

                        Toast.makeText(this, getString(R.string.msg_stock_saved), Toast.LENGTH_SHORT).show();

                        // FIFO Logic for Manual Edit
                        if (difference > 0) {
                            addBatch(difference);
                        } else {
                            processFifoStockOut(Math.abs(difference));
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.msg_failed_save), Toast.LENGTH_SHORT).show());

        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.msg_invalid_number), Toast.LENGTH_SHORT).show();
            etDetailQty.setText(String.valueOf(currentQty));
        }
    }

    // --- FIFO HELPER: Add new batch ---
    private void addBatch(int qty) {
        Batch newBatch = new Batch(qty);
        db.collection("inventory").document(itemId).collection("batches").add(newBatch);
    }

    // --- FIFO HELPER: FIX APPLIED HERE ---
    private void processFifoStockOut(int qtyNeededInput) {
        if (qtyNeededInput <= 0) return;

        // FIX: Wrap the integer in an array so it can be modified inside the listener
        final int[] neededWrapper = {qtyNeededInput};

        db.collection("inventory").document(itemId).collection("batches")
                .orderBy("dateReceived", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        Long rQty = doc.getLong("remainingQty");
                        int remaining = (rQty != null) ? rQty.intValue() : 0;

                        if (remaining > 0) {
                            // Use array index 0 to access the variable
                            if (remaining >= neededWrapper[0]) {
                                // Satisfied fully by this batch
                                doc.getReference().update("remainingQty", remaining - neededWrapper[0]);
                                neededWrapper[0] = 0;
                                return;
                            } else {
                                // Use all of this batch and continue
                                doc.getReference().update("remainingQty", 0);
                                neededWrapper[0] -= remaining;
                            }
                        }
                        // Stop loop if we found enough stock
                        if (neededWrapper[0] <= 0) break;
                    }
                });
    }

    private void logTransactionToHistory(int change) {
        if (change == 0) return;
        String type = (change > 0) ? "IN" : "OUT";
        Transaction transaction = new Transaction(itemName, type, Math.abs(change));
        db.collection("transactions").add(transaction);
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_title))
                .setMessage(getString(R.string.dialog_delete_msg, itemName))
                .setPositiveButton(getString(R.string.dialog_btn_delete), (dialog, which) -> {
                    db.collection("inventory").document(itemId).delete()
                            .addOnSuccessListener(aVoid -> {
                                localDb.logAction("DELETE", itemName);
                                Toast.makeText(this, getString(R.string.msg_product_deleted), Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemListener != null) itemListener.remove();
        if (localDb != null) localDb.close();
    }
}
