package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView; // Required for the fix
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory.adapter.InventoryAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private List<InventoryItem> itemList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Reusing Main Layout

        // Hide Dashboard Stats
        findViewById(R.id.statsContainer).setVisibility(android.view.View.GONE);
        findViewById(R.id.tvQuickActionsHeader).setVisibility(android.view.View.GONE);
        findViewById(R.id.quickActionsGrid).setVisibility(android.view.View.GONE);

        // --- FIX: Cast to TextView ---
        ((TextView) findViewById(R.id.tvAppTitle)).setText("Full Inventory");

        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new InventoryAdapter(itemList, item -> {
            Intent intent = new Intent(InventoryActivity.this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("itemName", item.getName());
            intent.putExtra("currentQty", item.getQuantity());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        // Setup Navbar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_inventory);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return id == R.id.nav_inventory;
        });

        loadInventory();
    }

    private void loadInventory() {
        db.collection("inventory").addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            itemList.clear();
            for (DocumentSnapshot doc : snapshots) {
                InventoryItem item = doc.toObject(InventoryItem.class);
                item.setId(doc.getId());
                itemList.add(item);
            }
            adapter.notifyDataSetChanged();
        });
    }
}