package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.InventoryAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private List<InventoryItem> itemList;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private TextView tvTotalCount, tvTotalValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Firebase & UI Setup
        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();

        tvTotalCount = findViewById(R.id.tvSummaryCount);
        tvTotalValue = findViewById(R.id.tvSummaryValue);

        // 2. Setup Recycler View (List)
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new InventoryAdapter(itemList, item -> {
            Intent intent = new Intent(MainActivity.this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("itemName", item.getName());
            intent.putExtra("currentQty", item.getQuantity());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        // 3. Quick Action Buttons (NEW)
        findViewById(R.id.btnQuickAdd).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddItemActivity.class));
        });

        findViewById(R.id.btnQuickReport).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ReportActivity.class));
        });

        // 4. Main FAB (The big purple one)
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddItemActivity.class)));

        // 5. Bottom Navigation Setup
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false); // Disable placeholder

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_inventory) {
                startActivity(new Intent(this, InventoryActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                overridePendingTransition(0,0);
                return true;
            }
            return false;
        });

        // 6. Start Sync
        listenForRealTimeUpdates();
    }

    private void listenForRealTimeUpdates() {
        firestoreListener = db.collection("inventory")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    if (snapshots != null) {
                        itemList.clear();
                        int totalItems = 0;
                        double totalValue = 0.0;

                        for (DocumentSnapshot doc : snapshots) {
                            InventoryItem item = doc.toObject(InventoryItem.class);
                            if (item != null) {
                                item.setId(doc.getId());
                                itemList.add(item);
                                totalItems += item.getQuantity();
                                totalValue += (item.getQuantity() * item.getPrice());
                            }
                        }
                        adapter.notifyDataSetChanged();
                        tvTotalCount.setText(String.valueOf(totalItems));
                        tvTotalValue.setText("$" + String.format("%.2f", totalValue));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) firestoreListener.remove();
    }
}