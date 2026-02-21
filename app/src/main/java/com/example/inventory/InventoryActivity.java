package com.example.inventory;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.InventoryAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InventoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private List<InventoryItem> fullItemList;
    private List<InventoryItem> displayList;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private String userRole = "Staff";
    private TextView tvAppTitle;
    private TabLayout tabLayout;

    private EditText etSearch;
    private String searchQuery = "";

    private BottomAppBar bottomAppBar;
    private FloatingActionButton fabAdd;

    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        db = FirebaseFirestore.getInstance();
        fullItemList = new ArrayList<>();
        displayList = new ArrayList<>();

        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        tvAppTitle = findViewById(R.id.tvAppTitle);
        tabLayout = findViewById(R.id.tabLayout);
        etSearch = findViewById(R.id.etSearch);
        bottomAppBar = findViewById(R.id.bottomAppBar);
        fabAdd = findViewById(R.id.fabAdd);

        applyRoleSecurity();
        setupRecyclerView();
        setupTabs();
        setupSearch();
        setupNavigation();
        loadInventoryRealTime();
        setupKeyboardAutoHide();
    }

    private void applyRoleSecurity() {
        if (tvAppTitle != null) {
            // FIX: Use strings.xml
            if (userRole.equalsIgnoreCase("Admin")) {
                tvAppTitle.setText(getString(R.string.title_admin_inventory));
            } else {
                tvAppTitle.setText(getString(R.string.title_staff_inventory));
            }
        }

        if (fabAdd != null) {
            fabAdd.setVisibility(userRole.equalsIgnoreCase("Admin") ? View.VISIBLE : View.GONE);
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddItemActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
            });
        }
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_all_items)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_in_stock)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_out_of_stock)));

        TabLayout.Tab allItemsTab = tabLayout.getTabAt(0);
        if (allItemsTab != null) allItemsTab.select();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                applyFilter();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilter();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupKeyboardAutoHide() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                if (bottomAppBar != null) bottomAppBar.setVisibility(View.GONE);
                if (fabAdd != null) fabAdd.hide();
            } else {
                if (bottomAppBar != null) bottomAppBar.setVisibility(View.VISIBLE);
                if (fabAdd != null && userRole.equalsIgnoreCase("Admin")) {
                    fabAdd.show();
                }
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new InventoryAdapter(displayList, item -> {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_inventory);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inventory) return true; // Already here

            Intent intent = null;
            if (id == R.id.nav_home) {
                intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (id == R.id.nav_report) {
                if ("Staff".equalsIgnoreCase(userRole)) {
                    Toast.makeText(this, getString(R.string.msg_access_denied), Toast.LENGTH_SHORT).show();
                    return false;
                }
                intent = new Intent(this, ReportActivity.class);
            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }

            if (intent != null) {
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish(); // Closes the old tab cleanly
                return true;
            }
            return false;
        });
    }


    private void loadInventoryRealTime() {
        firestoreListener = db.collection("inventory").addSnapshotListener((snapshots, e) -> {
            if (e != null || snapshots == null) return;
            fullItemList.clear();
            for (DocumentSnapshot doc : snapshots) {
                InventoryItem item = doc.toObject(InventoryItem.class);
                if (item != null) {
                    item.setId(doc.getId());
                    fullItemList.add(item);
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            Collections.sort(fullItemList, (item1, item2) -> {
                try {
                    String d1Str = (item1.getDateAdded() != null) ? item1.getDateAdded() : "Jan 01, 1970";
                    String d2Str = (item2.getDateAdded() != null) ? item2.getDateAdded() : "Jan 01, 1970";
                    Date d1 = sdf.parse(d1Str);
                    Date d2 = sdf.parse(d2Str);
                    return d2.compareTo(d1);
                } catch (Exception ex) { return 0; }
            });
            applyFilter();
        });
    }

    private void applyFilter() {
        displayList.clear();
        for (InventoryItem item : fullItemList) {
            boolean matchesTab = false;

            if (currentTab == 0) matchesTab = true;
            else if (currentTab == 1) matchesTab = (item.getQuantity() > 0);
            else if (currentTab == 2) matchesTab = (item.getQuantity() <= 0);

            boolean matchesSearch = true;
            if (!searchQuery.isEmpty()) {
                String name = item.getName().toLowerCase();
                String barcode = (item.getBarcode() != null) ? item.getBarcode().toLowerCase() : "";
                if (!name.contains(searchQuery) && !barcode.contains(searchQuery)) {
                    matchesSearch = false;
                }
            }

            if (matchesTab && matchesSearch) {
                displayList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) firestoreListener.remove();
    }
}
