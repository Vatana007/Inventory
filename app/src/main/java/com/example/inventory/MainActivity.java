package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.InventoryAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private TextView tvTotalCount, tvTotalValue, tvAppTitle;
    private TextInputEditText etSearch;
    private List<InventoryItem> itemList;
    private List<InventoryItem> fullList;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;

    // SECURITY FIX: Default to "Staff" (Safe Mode) instead of Admin
    private String userRole = "Staff";

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    etSearch.setText(result.getContents());
                    Toast.makeText(MainActivity.this, "Found: " + result.getContents(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        fullList = new ArrayList<>();

        tvTotalCount = findViewById(R.id.tvSummaryCount);
        tvTotalValue = findViewById(R.id.tvSummaryValue);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        etSearch = findViewById(R.id.etSearch);

        // 1. GET ROLE
        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }
        tvAppTitle.setText(userRole + " Dashboard");

        // 2. APPLY RESTRICTIONS
        if ("Staff".equalsIgnoreCase(userRole)) {
            // Hide "Add" buttons
            findViewById(R.id.fabAdd).setVisibility(View.GONE);
            findViewById(R.id.btnQuickAdd).setVisibility(View.GONE);

            // Hide "Report" button (Quick Action)
            findViewById(R.id.btnQuickReport).setVisibility(View.GONE);
        }

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 3. PASS ROLE TO DETAIL SCREEN (CRITICAL FIX)
        adapter = new InventoryAdapter(itemList, item -> {
            Intent intent = new Intent(MainActivity.this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("itemName", item.getName());
            intent.putExtra("currentQty", item.getQuantity());
            intent.putExtra("USER_ROLE", userRole); // <--- THIS MUST BE HERE
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        setupQuickActions();
        setupBottomNavigation(); // <--- Check this method below!
        listenForRealTimeUpdates();
    }

    private void listenForRealTimeUpdates() {
        firestoreListener = db.collection("inventory").addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            fullList.clear();
            if (snapshots != null) {
                int totalItems = 0;
                double totalValue = 0.0;
                int lowStock = 0;
                for (DocumentSnapshot doc : snapshots) {
                    InventoryItem item = doc.toObject(InventoryItem.class);
                    if (item != null) {
                        item.setId(doc.getId());
                        fullList.add(item);
                        totalItems += item.getQuantity();
                        totalValue += (item.getQuantity() * item.getPrice());
                        if(item.getQuantity() < item.getMinStock()) lowStock++;
                    }
                }
                tvTotalCount.setText(String.valueOf(totalItems));
                tvTotalValue.setText("$" + String.format("%.2f", totalValue));
                filterList(etSearch.getText().toString());
                if(lowStock > 0) Toast.makeText(this, "⚠️ Low Stock: " + lowStock, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterList(String query) {
        itemList.clear();
        if (query.isEmpty()) { itemList.addAll(fullList); }
        else {
            String q = query.toLowerCase().trim();
            for (InventoryItem i : fullList) {
                if (i.getName().toLowerCase().contains(q) || (i.getBarcode() != null && i.getBarcode().contains(q))) {
                    itemList.add(i);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void setupQuickActions() {
        findViewById(R.id.btnQuickAdd).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddItemActivity.class)));

        // SECURITY CHECK ON CLICK
        findViewById(R.id.btnQuickReport).setOnClickListener(v -> {
            if ("Admin".equalsIgnoreCase(userRole)) {
                startActivity(new Intent(MainActivity.this, ReportActivity.class));
            } else {
                Toast.makeText(this, "Access Denied: Admin Only", Toast.LENGTH_SHORT).show();
            }
        });

        View scanBtn = findViewById(R.id.btnQuickScan);
        if (scanBtn != null) {
            scanBtn.setOnClickListener(v -> {
                ScanOptions options = new ScanOptions();
                options.setPrompt("Scan Barcode");
                options.setOrientationLocked(true);
                options.setCaptureActivity(PortraitCaptureActivity.class);
                barcodeLauncher.launch(options);
            });
        }

        // History Button
        try {
            LinearLayout grid = findViewById(R.id.quickActionsGrid);
            if (grid != null && grid.getChildCount() > 3) {
                grid.getChildAt(3).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
            }
        } catch (Exception e) {}

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        if(fab != null) fab.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddItemActivity.class)));
    }

    // --- SECURITY FIX FOR BOTTOM NAVIGATION ---
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;

            if (id == R.id.nav_inventory) {
                startActivity(new Intent(this, InventoryActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            if (id == R.id.nav_report) {
                // BLOCK STAFF HERE
                if ("Staff".equalsIgnoreCase(userRole)) {
                    Toast.makeText(this, "🚫 Reports are for Admin only", Toast.LENGTH_SHORT).show();
                    return false; // Don't switch tabs
                }

                startActivity(new Intent(this, ReportActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) firestoreListener.remove();
    }
}