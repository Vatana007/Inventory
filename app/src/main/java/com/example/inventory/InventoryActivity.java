package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.InventoryAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private List<InventoryItem> itemList;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private String userRole = "Staff"; // Security default
    private TextView tvTotalCount, tvTotalValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Using activity_main.xml to maintain visual consistency
        setContentView(R.layout.activity_main);

        // 1. Initialize Views & Data
        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        tvTotalCount = findViewById(R.id.tvSummaryCount);
        tvTotalValue = findViewById(R.id.tvSummaryValue);

        // 2. FIX: Retrieve role and ensure it's not null
        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        // 3. Setup Components
        applyRoleSecurity();
        setupUserActions();
        setupRecyclerView();
        setupNavigation();
        loadInventoryRealTime();
    }

    private void applyRoleSecurity() {
        // Find UI elements
        View quickHeader = findViewById(R.id.tvQuickActionsHeader);
        View quickGrid = findViewById(R.id.quickActionsGrid);
        View fab = findViewById(R.id.fabAdd);
        View stats = findViewById(R.id.statsContainer);
        TextView tvTitle = findViewById(R.id.tvAppTitle);

        // Hide specific "Home" sections that aren't needed in List view
        if (quickHeader != null) quickHeader.setVisibility(View.GONE);
        if (quickGrid != null) quickGrid.setVisibility(View.GONE);

        // STAFF & ADMIN: Both can now see the stats (Total Items/Value) for consistency
        if (stats != null) stats.setVisibility(View.VISIBLE);

        // Header Title based on role
        if (tvTitle != null) {
            tvTitle.setText(userRole.equalsIgnoreCase("Admin") ? "Admin: All Inventory" : "Staff: Inventory List");
        }

        // FAB visibility: Only Admin can add items
        if (fab != null) {
            fab.setVisibility(userRole.equalsIgnoreCase("Admin") ? View.VISIBLE : View.GONE);
            fab.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddItemActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
            });
        }
    }

    private void setupUserActions() {
        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(this, v);
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

        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter(itemList, item -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("USER_ROLE", userRole); // Pass role to detail for stock-in/out logic
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_inventory);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("USER_ROLE", userRole); // Crucial: Pass role back home
                    startActivity(intent);
                    finish();
                    return true;
                } else if (id == R.id.nav_report) {
                    if (userRole.equalsIgnoreCase("Staff")) {
                        Toast.makeText(this, "Access Denied: Admins Only", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    Intent intent = new Intent(this, ReportActivity.class);
                    intent.putExtra("USER_ROLE", userRole);
                    startActivity(intent);
                    return true;
                }
                return id == R.id.nav_inventory;
            });
        }
    }

    private void loadInventoryRealTime() {
        firestoreListener = db.collection("inventory").addSnapshotListener((snapshots, e) -> {
            if (e != null || snapshots == null) return;

            itemList.clear();
            int totalItems = 0;
            double totalValue = 0.0;

            for (DocumentSnapshot doc : snapshots) {
                InventoryItem item = doc.toObject(InventoryItem.class);
                if (item != null) {
                    item.setId(doc.getId());
                    itemList.add(item);

                    // Calculate totals for the summary cards
                    totalItems += item.getQuantity();
                    totalValue += (item.getQuantity() * item.getPrice());
                }
            }

            // Update UI Cards
            if (tvTotalCount != null) tvTotalCount.setText(String.valueOf(totalItems));
            if (tvTotalValue != null) tvTotalValue.setText("$" + String.format("%.2f", totalValue));

            adapter.notifyDataSetChanged();
        });
    }

    private void handleLogout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
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