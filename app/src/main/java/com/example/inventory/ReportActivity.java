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

    // UI & Data
    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<InventoryItem> fullList;       // Master list from Database
    private List<InventoryItem> filteredList;   // List shown on screen (The missing variable!)
    private FirebaseFirestore db;
    private TextView tvCurrentMonth;

    // Filter State
    private String selectedMonthFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // 1. Initialize Firebase & Lists
        db = FirebaseFirestore.getInstance();
        fullList = new ArrayList<>();
        filteredList = new ArrayList<>(); // Initialize it here!

        tvCurrentMonth = findViewById(R.id.tvCurrentMonth);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 2. Setup Adapter
        adapter = new ReportAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        // 3. Filter Button Logic
        findViewById(R.id.btnFilterDate).setOnClickListener(v -> showMonthPicker());

        // 4. Export Button Logic (PDF or CSV)
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());

        // 5. Navigation & Data Load
        setupNavigation();
        loadReports();
    }

    // --- DATA LOADING & FILTERING ---
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    // --- EXPORT LOGIC ---

    private void showExportDialog() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ask user to choose format
        new AlertDialog.Builder(this)
                .setTitle("Export Report")
                .setMessage("Choose a format for your report:")
                .setPositiveButton("PDF", (dialog, which) -> generateBulkPDF())
                .setNegativeButton("CSV (Excel)", (dialog, which) -> generateCSV())
                .setNeutralButton("Cancel", null)
                .show();
    }

    // 1. Generate PDF
    private void generateBulkPDF() {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        // Title
        paint.setColor(Color.BLACK);
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        String reportTitle = (selectedMonthFilter != null) ? "REPORT: " + selectedMonthFilter.toUpperCase() : "FULL INVENTORY REPORT";
        canvas.drawText(reportTitle, 50, 60, paint);

        // Subtitle
        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("Generated by Inventify App", 50, 85, paint);

        // Line
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(50, 100, 545, 100, paint);

        // Items
        paint.setColor(Color.BLACK);
        paint.setTextSize(14);
        int y = 140;
        double totalValue = 0;

        for (InventoryItem item : filteredList) {
            String line = item.getName() + "  |  Qty: " + item.getQuantity() + "  |  $" + item.getPrice();
            canvas.drawText(line, 50, y, paint);

            paint.setTextSize(10);
            paint.setColor(Color.GRAY);
            String d = (item.getDateAdded() != null) ? item.getDateAdded() : "N/A";
            canvas.drawText(d, 50, y + 15, paint);

            paint.setColor(Color.BLACK);
            paint.setTextSize(14);

            y += 40;
            totalValue += (item.getPrice() * item.getQuantity());

            if (y > 780) break; // Simple page break prevention
        }

        // Total
        paint.setFakeBoldText(true);
        canvas.drawLine(50, y + 10, 545, y + 10, paint);
        canvas.drawText("TOTAL VALUE: $" + String.format("%.2f", totalValue), 50, y + 40, paint);

        document.finishPage(page);

        String safeName = (selectedMonthFilter != null) ? selectedMonthFilter.replace(" ", "_") : "Full_Report";
        String fileName = "Inventify_" + safeName + "_" + System.currentTimeMillis() + ".pdf";

        saveFile(document, fileName, "application/pdf");
    }

    // 2. Generate CSV
    private void generateCSV() {
        StringBuilder data = new StringBuilder();
        data.append("Name,Quantity,Price,Min Stock,Date Added,Value\n"); // Header

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

    // Generic File Saver (Handles both PDF and CSV)
    private void saveFile(Object content, String fileName, String mimeType) {
        try {
            OutputStream outputStream = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    outputStream = getContentResolver().openOutputStream(uri);
                }
            } else {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                outputStream = new FileOutputStream(file);
                Toast.makeText(this, "Saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
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

    private void setupNavigation() {
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

        findViewById(R.id.fabAdd).setOnClickListener(v -> startActivity(new Intent(this, AddItemActivity.class)));
    }
}