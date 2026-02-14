package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
import com.google.firebase.auth.FirebaseAuth;
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

    // Default to Staff for safety, but will be overwritten in onCreate
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

        // 1. GET ROLE IMMEDIATELY
        // This prevents the "Admin" to "Staff" reset when returning to Home
        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        // 2. INITIALIZE DATA & FIREBASE
        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        fullList = new ArrayList<>();

        tvTotalCount = findViewById(R.id.tvSummaryCount);
        tvTotalValue = findViewById(R.id.tvSummaryValue);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        etSearch = findViewById(R.id.etSearch);

        // 3. APPLY ROLE-BASED UI
        tvAppTitle.setText(userRole + " Dashboard");
        applyRolePermissions();

        // 4. SETUP ACTIONS & COMPONENTS
        setupLogoutActions();
        setupRecyclerView();

        // Search Logic
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        setupQuickActions();
        setupBottomNavigation();
        listenForRealTimeUpdates();
    }

    private void applyRolePermissions() {
        // Find views that only Admins should see
        View fabAdd = findViewById(R.id.fabAdd);
        View btnQuickAdd = findViewById(R.id.btnQuickAdd);
        View btnQuickReport = findViewById(R.id.btnQuickReport);
        View statsContainer = findViewById(R.id.statsContainer);

        if ("Staff".equalsIgnoreCase(userRole)) {
            if (fabAdd != null) fabAdd.setVisibility(View.GONE);
            if (btnQuickAdd != null) btnQuickAdd.setVisibility(View.GONE);
            if (btnQuickReport != null) btnQuickReport.setVisibility(View.GONE);
            // Optional: Hide stats container for staff if you want them to see ONLY the list
            // if (statsContainer != null) statsContainer.setVisibility(View.GONE);
        } else {
            // Ensure they are visible for Admins
            if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);
            if (btnQuickAdd != null) btnQuickAdd.setVisibility(View.VISIBLE);
            if (btnQuickReport != null) btnQuickReport.setVisibility(View.VISIBLE);
            if (statsContainer != null) statsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupLogoutActions() {
        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());

        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                popup.getMenu().add("Role: " + userRole).setEnabled(false);
                popup.getMenu().add("Logout");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().equals("Logout")) {
                        handleLogout();
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter(itemList, item -> {
            Intent intent = new Intent(MainActivity.this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("USER_ROLE", userRole); // Pass role to detail
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupQuickActions() {
        // Helper to add role to intents
        View.OnClickListener adminCheckAdd = v -> {
            Intent intent = new Intent(MainActivity.this, AddItemActivity.class);
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        };

        View btnAdd = findViewById(R.id.btnQuickAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(adminCheckAdd);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        if(fab != null) fab.setOnClickListener(adminCheckAdd);

        View btnReport = findViewById(R.id.btnQuickReport);
        if (btnReport != null) {
            btnReport.setOnClickListener(v -> {
                if ("Admin".equalsIgnoreCase(userRole)) {
                    Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                    intent.putExtra("USER_ROLE", userRole);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Access Denied: Admin Only", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View scanBtn = findViewById(R.id.btnQuickScan);
        if (scanBtn != null) {
            scanBtn.setOnClickListener(v -> {
                ScanOptions options = new ScanOptions();
                options.setCaptureActivity(PortraitCaptureActivity.class);
                barcodeLauncher.launch(options);
            });
        }

        View btnAlerts = findViewById(R.id.btnQuickAlerts);
        if (btnAlerts != null) {
            btnAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
            });
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.getMenu().getItem(2).setEnabled(false);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;

            if (id == R.id.nav_inventory) {
                Intent intent = new Intent(this, InventoryActivity.class);
                intent.putExtra("USER_ROLE", userRole); // Crucial: Pass role
                startActivity(intent);
                return true;
            }
            if (id == R.id.nav_report) {
                if ("Staff".equalsIgnoreCase(userRole)) {
                    Toast.makeText(this, "🚫 Admin Access Only", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Intent intent = new Intent(this, ReportActivity.class);
                intent.putExtra("USER_ROLE", userRole); // Crucial: Pass role
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void listenForRealTimeUpdates() {
        firestoreListener = db.collection("inventory").addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            fullList.clear();
            if (snapshots != null) {
                int totalItems = 0;
                double totalValue = 0.0;
                for (DocumentSnapshot doc : snapshots) {
                    InventoryItem item = doc.toObject(InventoryItem.class);
                    if (item != null) {
                        item.setId(doc.getId());
                        fullList.add(item);
                        totalItems += item.getQuantity();
                        totalValue += (item.getQuantity() * item.getPrice());
                    }
                }
                // Update stats only if user is Admin (optional logic)
                tvTotalCount.setText(String.valueOf(totalItems));
                tvTotalValue.setText("$" + String.format("%.2f", totalValue));
                filterList(etSearch.getText().toString());
            }
        });
    }

    private void filterList(String query) {
        itemList.clear();
        if (query.isEmpty()) { itemList.addAll(fullList); }
        else {
            String q = query.toLowerCase().trim();
            for (InventoryItem i : fullList) {
                if (i.getName().toLowerCase().contains(q)) itemList.add(i);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void handleLogout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) firestoreListener.remove();
    }
}