package com.example.inventory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
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
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private TextView tvTotalCount, tvTotalValue, tvAppTitle;
    private TextInputEditText etSearch;
    private List<InventoryItem> itemList;
    private List<InventoryItem> fullList;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;

    // UI References for visibility toggling
    private BottomAppBar bottomAppBar;
    private FloatingActionButton fabAdd;

    private String userRole = "Staff";

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    etSearch.setText(result.getContents());
                    Toast.makeText(MainActivity.this, getString(R.string.msg_found_scan, result.getContents()), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        db = FirebaseFirestore.getInstance();
        itemList = new ArrayList<>();
        fullList = new ArrayList<>();

        tvTotalCount = findViewById(R.id.tvSummaryCount);
        tvTotalValue = findViewById(R.id.tvSummaryValue);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        etSearch = findViewById(R.id.etSearch);
        bottomAppBar = findViewById(R.id.bottomAppBar);
        fabAdd = findViewById(R.id.fabAdd);

        tvAppTitle.setText(userRole.equals("Admin") ? getString(R.string.title_admin_dashboard) : getString(R.string.title_staff_dashboard));

        applyRolePermissions();
        setupLogoutActions();
        setupRecyclerView();
        setupSearch();
        setupQuickActions();
        setupBottomNavigation();
        listenForRealTimeUpdates();

        // THE FIX: Keyboard Listener
        setupKeyboardAutoHide();
    }

    private void applyRolePermissions() {
        View btnQuickAdd = findViewById(R.id.btnQuickAdd);
        View btnQuickReport = findViewById(R.id.btnQuickReport);
        View statsContainer = findViewById(R.id.statsContainer);

        if ("Staff".equalsIgnoreCase(userRole)) {
            if (fabAdd != null) fabAdd.setVisibility(View.GONE);
            if (btnQuickAdd != null) btnQuickAdd.setVisibility(View.GONE);
            if (btnQuickReport != null) btnQuickReport.setVisibility(View.GONE);
        } else {
            if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);
            if (btnQuickAdd != null) btnQuickAdd.setVisibility(View.VISIBLE);
            if (btnQuickReport != null) btnQuickReport.setVisibility(View.VISIBLE);
            if (statsContainer != null) statsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupKeyboardAutoHide() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard Open
                if (bottomAppBar != null) bottomAppBar.setVisibility(View.GONE);
                if (fabAdd != null) fabAdd.hide();
            } else {
                // Keyboard Closed
                if (bottomAppBar != null) bottomAppBar.setVisibility(View.VISIBLE);
                if (fabAdd != null && "Admin".equalsIgnoreCase(userRole)) {
                    fabAdd.show();
                }
            }
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupLogoutActions() {
        View btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());

        ImageView ivProfile = findViewById(R.id.ivProfile);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                popup.getMenu().add(getString(R.string.msg_role, userRole)).setEnabled(false);
                popup.getMenu().add(getString(R.string.msg_logout));
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().toString().equals(getString(R.string.msg_logout))) {
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
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupQuickActions() {
        View.OnClickListener adminCheckAdd = v -> {
            Intent intent = new Intent(MainActivity.this, AddItemActivity.class);
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        };

        View btnAdd = findViewById(R.id.btnQuickAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(adminCheckAdd);

        if(fabAdd != null) fabAdd.setOnClickListener(adminCheckAdd);

        View btnReport = findViewById(R.id.btnQuickReport);
        if (btnReport != null) {
            btnReport.setOnClickListener(v -> {
                if ("Admin".equalsIgnoreCase(userRole)) {
                    Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                    intent.putExtra("USER_ROLE", userRole);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, getString(R.string.msg_access_denied), Toast.LENGTH_SHORT).show();
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
        if (bottomNav == null) return;

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.getMenu().getItem(2).setEnabled(false);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;

            if (id == R.id.nav_inventory) {
                Intent intent = new Intent(this, InventoryActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0,0);
                return true;
            }
            if (id == R.id.nav_report) {
                if ("Staff".equalsIgnoreCase(userRole)) {
                    Toast.makeText(this, "🚫 Admin Access Only", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Intent intent = new Intent(this, ReportActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0,0);
                return true;
            }
            if (id == R.id.nav_history) {
                Intent intent = new Intent(this, HistoryActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0,0);
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
                if(tvTotalCount != null) tvTotalCount.setText(String.valueOf(totalItems));
                if(tvTotalValue != null) tvTotalValue.setText("$" + String.format("%.2f", totalValue));

                filterList(etSearch.getText() != null ? etSearch.getText().toString() : "");
            }
        });
    }

    private void filterList(String query) {
        itemList.clear();

        if (query.isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            List<InventoryItem> sortedList = new ArrayList<>(fullList);

            Collections.sort(sortedList, (item1, item2) -> {
                try {
                    String d1Str = (item1.getDateAdded() != null) ? item1.getDateAdded() : "Jan 01, 1970";
                    String d2Str = (item2.getDateAdded() != null) ? item2.getDateAdded() : "Jan 01, 1970";
                    Date d1 = sdf.parse(d1Str);
                    Date d2 = sdf.parse(d2Str);
                    return d2.compareTo(d1);
                } catch (Exception e) {
                    return 0;
                }
            });

            for (int i = 0; i < Math.min(2, sortedList.size()); i++) {
                itemList.add(sortedList.get(i));
            }
        }
        else {
            String q = query.toLowerCase().trim();
            for (InventoryItem i : fullList) {
                if (i.getName().toLowerCase().contains(q) ||
                        (i.getBarcode() != null && i.getBarcode().toLowerCase().contains(q))) {
                    itemList.add(i);
                }
            }
        }

        if(adapter != null) adapter.notifyDataSetChanged();
    }

    private void handleLogout() {
        FirebaseAuth.getInstance().signOut();
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

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
