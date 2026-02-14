package com.example.inventory;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.ReportAdapter;
import com.example.inventory.model.InventoryItem;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<InventoryItem> fullList;
    private List<InventoryItem> filteredList;
    private FirebaseFirestore db;
    private TextView tvCurrentMonth;

    private String selectedMonthFilter = null;

    // THE FIX: Create a variable to hold the role
    private String userRole = "Staff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // 1. DATA PERSISTENCE FIX: Catch the role from the Intent
        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        db = FirebaseFirestore.getInstance();
        fullList = new ArrayList<>();
        filteredList = new ArrayList<>();

        tvCurrentMonth = findViewById(R.id.tvCurrentMonth);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ReportAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnFilterDate).setOnClickListener(v -> showMonthPicker());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());

        setupNavigation();
        loadReports();
    }

    // --- DATA LOADING & FILTERING (Logic remains the same) ---
    private void loadReports() {
        db.collection("inventory")
                .orderBy("dateAdded")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    fullList.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            InventoryItem item = doc.toObject(InventoryItem.class);
                            if (item != null) {
                                item.setId(doc.getId());
                                fullList.add(item);
                            }
                        }
                    }
                    applyFilter();
                });
    }

    private void showMonthPicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
            selectedMonthFilter = sdf.format(cal.getTime());
            tvCurrentMonth.setText("Showing: " + selectedMonthFilter);
            applyFilter();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle("Select Month");
        dialog.show();
    }

    private void applyFilter() {
        filteredList.clear();
        if (selectedMonthFilter == null) {
            filteredList.addAll(fullList);
            tvCurrentMonth.setText("Showing: All Time");
        } else {
            SimpleDateFormat originalFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            SimpleDateFormat compareFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
            for (InventoryItem item : fullList) {
                String dateStr = item.getDateAdded();
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        Date itemDate = originalFormat.parse(dateStr);
                        if (itemDate != null && compareFormat.format(itemDate).equals(selectedMonthFilter)) {
                            filteredList.add(item);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    // --- EXPORT LOGIC (Logic remains same) ---
    private void showExportDialog() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Export Report")
                .setMessage("Choose a format for your report:")
                .setPositiveButton("PDF", (dialog, which) -> generateBulkPDF())
                .setNegativeButton("CSV (Excel)", (dialog, which) -> generateCSV())
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void generateBulkPDF() {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        String reportTitle = (selectedMonthFilter != null) ? "REPORT: " + selectedMonthFilter.toUpperCase() : "FULL INVENTORY REPORT";
        canvas.drawText(reportTitle, 50, 60, paint);
        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("Generated by Inventify App", 50, 85, paint);
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(50, 100, 545, 100, paint);
        paint.setColor(Color.BLACK);
        paint.setTextSize(14);
        int y = 140;
        double totalValue = 0;
        for (InventoryItem item : filteredList) {
            String line = item.getName() + "  |  Qty: " + item.getQuantity() + "  |  $" + item.getPrice();
            canvas.drawText(line, 50, y, paint);
            y += 40;
            totalValue += (item.getPrice() * item.getQuantity());
            if (y > 780) break;
        }
        paint.setFakeBoldText(true);
        canvas.drawLine(50, y + 10, 545, y + 10, paint);
        canvas.drawText("TOTAL VALUE: $" + String.format("%.2f", totalValue), 50, y + 40, paint);
        document.finishPage(page);
        String safeName = (selectedMonthFilter != null) ? selectedMonthFilter.replace(" ", "_") : "Full_Report";
        String fileName = "Inventify_" + safeName + "_" + System.currentTimeMillis() + ".pdf";
        saveFile(document, fileName, "application/pdf");
    }

    private void generateCSV() {
        StringBuilder data = new StringBuilder();
        data.append("Name,Quantity,Price,Min Stock,Date Added,Value\n");
        for (InventoryItem item : filteredList) {
            double totalVal = item.getQuantity() * item.getPrice();
            data.append(item.getName()).append(",");
            data.append(item.getQuantity()).append(",");
            data.append(item.getPrice()).append(",");
            data.append(item.getMinStock()).append(",");
            data.append(item.getDateAdded() != null ? item.getDateAdded().replace(",", " ") : "N/A").append(",");
            data.append(totalVal).append("\n");
        }
        String fileName = "Inventory_Data_" + System.currentTimeMillis() + ".csv";
        saveFile(data.toString(), fileName, "text/csv");
    }

    private void saveFile(Object content, String fileName, String mimeType) {
        try {
            OutputStream outputStream = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) outputStream = getContentResolver().openOutputStream(uri);
            } else {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                outputStream = new FileOutputStream(file);
            }
            if (outputStream != null) {
                if (content instanceof PdfDocument) {
                    ((PdfDocument) content).writeTo(outputStream);
                    ((PdfDocument) content).close();
                } else if (content instanceof String) {
                    outputStream.write(((String) content).getBytes());
                }
                outputStream.close();
                Toast.makeText(this, "File Saved Successfully!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error saving file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // --- NAVIGATION FIX ---
    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_report);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent = null;

            if (id == R.id.nav_home) {
                intent = new Intent(this, MainActivity.class);
            } else if (id == R.id.nav_inventory) {
                intent = new Intent(this, InventoryActivity.class);
            }

            if (intent != null) {
                // THE FIX: Re-attach the USER_ROLE so MainActivity stays Admin
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0,0);
                return true;
            }
            return id == R.id.nav_report;
        });

        // ALSO FIX: Passing role to AddItemActivity
        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddItemActivity.class);
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        });
    }
}