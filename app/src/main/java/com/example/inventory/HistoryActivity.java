package com.example.inventory;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.TransactionAdapter;
import com.example.inventory.model.Transaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList;
    private FirebaseFirestore db;
    private TextView tvDateFrom, tvDateTo, tvResultCount;
    private View btnClear;
    private Date dateFrom = null, dateTo = null;
    private final SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private String userRole = "Staff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        } else {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            userRole = prefs.getString("USER_ROLE", "Staff");
        }

        db = FirebaseFirestore.getInstance();
        transactionList = new ArrayList<>();

        initViews();
        setupRecyclerView();
        setupNavigation();
        loadHistory();
    }

    private void initViews() {
        tvDateFrom = findViewById(R.id.tvDateFrom);
        tvDateTo = findViewById(R.id.tvDateTo);
        tvResultCount = findViewById(R.id.tvResultCount);
        btnClear = findViewById(R.id.btnClear);

        ImageView btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnDateFrom).setOnClickListener(v -> showDatePicker(true));
        findViewById(R.id.btnDateTo).setOnClickListener(v -> showDatePicker(false));
        findViewById(R.id.btnSearch).setOnClickListener(v -> filterHistory());

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> resetFilter());
        }
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(transactionList);
        recyclerView.setAdapter(adapter);
    }

    private void showDatePicker(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        if (isFrom && dateFrom != null) cal.setTime(dateFrom);
        else if (!isFrom && dateTo != null) cal.setTime(dateTo);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            if (isFrom) {
                selected.set(year, month, dayOfMonth, 0, 0, 0);
                dateFrom = selected.getTime();
                tvDateFrom.setText(df.format(dateFrom));
            } else {
                selected.set(year, month, dayOfMonth, 23, 59, 59);
                dateTo = selected.getTime();
                tvDateTo.setText(df.format(dateTo));
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        dialog.show();
    }

    private void loadHistory() {
        if (btnClear != null) btnClear.setVisibility(View.GONE);
        db.collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    transactionList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        transactionList.add(doc.toObject(Transaction.class));
                    }
                    tvResultCount.setText(getString(R.string.msg_showing_all_logs, transactionList.size()));
                    adapter.notifyDataSetChanged();
                });
    }

    private void filterHistory() {
        if (dateFrom == null || dateTo == null) {
            Toast.makeText(this, getString(R.string.msg_select_date_range), Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnClear != null) btnClear.setVisibility(View.VISIBLE);
        db.collection("transactions")
                .whereGreaterThanOrEqualTo("timestamp", dateFrom)
                .whereLessThanOrEqualTo("timestamp", dateTo)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    transactionList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        transactionList.add(doc.toObject(Transaction.class));
                    }
                    tvResultCount.setText(getString(R.string.msg_found_records, transactionList.size()));
                    adapter.notifyDataSetChanged();
                });
    }

    private void resetFilter() {
        dateFrom = null;
        dateTo = null;
        tvDateFrom.setText(getString(R.string.label_select_date));
        tvDateTo.setText(getString(R.string.label_select_date));
        loadHistory();
    }

    // THE FIX: Seamless Bottom Navigation
    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setBackground(null);
            bottomNav.getMenu().getItem(2).setEnabled(false);
            bottomNav.setSelectedItemId(R.id.nav_history);

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                Intent intent = null;

                if (id == R.id.nav_home) {
                    intent = new Intent(HistoryActivity.this, MainActivity.class);
                } else if (id == R.id.nav_inventory) {
                    intent = new Intent(HistoryActivity.this, InventoryActivity.class);
                } else if (id == R.id.nav_report) {
                    intent = new Intent(HistoryActivity.this, ReportActivity.class);
                }

                if (intent != null) {
                    intent.putExtra("USER_ROLE", userRole);
                    startActivity(intent);
                    overridePendingTransition(0, 0); // <--- THIS REMOVES THE SLIDE ANIMATION
                    return true;
                }
                return id == R.id.nav_history;
            });
        }
    }
}