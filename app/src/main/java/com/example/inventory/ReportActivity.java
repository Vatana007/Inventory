package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory.adapter.ReportAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class ReportActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<InventoryItem> itemList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. SET THE LAYOUT
        // We will reuse activity_main.xml structure but hide the dashboard parts
        // This keeps the navigation bar consistent.
        setContentView(R.layout.activity_main);

        // 2. Customize Header for Reports
        ((TextView) findViewById(R.id.tvAppTitle)).setText("Audit Reports");

        // Hide Main Dashboard elements
        findViewById(R.id.statsContainer).setVisibility(android.view.View.GONE);
        findViewById(R.id.tvQuickActionsHeader).setVisibility(android.view.View.GONE);
        findViewById(R.id.quickActionsGrid).setVisibility(android.view.View.GONE);

        // Change List Header
        ((TextView) findViewById(R.id.tvListHeader)).setText("All Transactions");

        // 3. Setup RecyclerView
        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Use the NEW Report Adapter
        adapter = new ReportAdapter(itemList);
        recyclerView.setAdapter(adapter);

        // 4. Load Data
        loadReports();

        // 5. Setup Navigation (Standard Boilerplate)
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_report);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.nav_inventory) {
                startActivity(new Intent(this, InventoryActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return id == R.id.nav_report;
        });
    }

    private void loadReports() {
        db.collection("inventory")
                .orderBy("name") // You can change this to sort by date if you store timestamps
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    itemList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        InventoryItem item = doc.toObject(InventoryItem.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            itemList.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}