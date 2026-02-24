package com.example.inventory;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ContentValues;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.adapter.TransactionAdapter;
import com.example.inventory.model.Transaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

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

        // EXTRA: Bind the new Export PDF Button
        View btnExportPdf = findViewById(R.id.btnExportPdf);
        if (btnExportPdf != null) {
            btnExportPdf.setOnClickListener(v -> generateHistoryPDF());
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
            bottomNav.setSelectedItemId(R.id.nav_profile);

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
                return id == R.id.nav_profile;
            });
        }
    }

    // EXTRA: Generate PDF from History Logs
    // EXTRA: Generate Multi-Page PDF from History Logs (CRASH-PROOF VERSION)
    private void generateHistoryPDF() {
        if (transactionList.isEmpty()) {
            Toast.makeText(this, "No history logs to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // Wrap EVERYTHING in a try-catch so the app NEVER crashes
        try {
            PdfDocument document = new PdfDocument();
            int pageNumber = 1;
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();

            // Draw header on the first page
            int y = drawPdfHeader(canvas, paint, pageNumber);

            int rowCount = 0;
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());

            for (Transaction t : transactionList) {
                // If we reach the bottom of the page, create a new one!
                if (y > 780) {
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = drawPdfHeader(canvas, paint, pageNumber);
                }

                // Draw Alternating Row Background
                if (rowCount % 2 != 0) {
                    paint.setColor(Color.rgb(250, 250, 250));
                    canvas.drawRect(50, y - 25, 545, y + 15, paint);
                }

                paint.setColor(Color.BLACK);
                paint.setTextSize(12);

                // NULL-SAFETY: Check Date
                String dateStr = (t.getTimestamp() != null) ? timeFormat.format(t.getTimestamp()) : "N/A";
                canvas.drawText(dateStr, 60, y, paint);

                // NULL-SAFETY: Check Item Name (THIS IS WHAT LIKELY CAUSED THE CRASH!)
                String itemName = (t.getItemName() != null && !t.getItemName().isEmpty()) ? t.getItemName() : "Unknown Item";
                canvas.drawText(itemName, 230, y, paint);

                // Conditional Coloring for IN / OUT
                if ("IN".equalsIgnoreCase(t.getType())) {
                    paint.setColor(Color.rgb(56, 142, 60)); // Green
                    paint.setFakeBoldText(true);
                    canvas.drawText("STOCK IN", 400, y, paint);
                    canvas.drawText("+" + t.getQuantityChanged(), 480, y, paint);
                } else {
                    paint.setColor(Color.rgb(211, 47, 47)); // Red
                    paint.setFakeBoldText(true);
                    canvas.drawText("STOCK OUT", 400, y, paint);
                    canvas.drawText("-" + t.getQuantityChanged(), 480, y, paint);
                }
                paint.setFakeBoldText(false);

                y += 40;
                rowCount++;
            }

            // Finish the final page
            document.finishPage(page);

            // Safely Save the File
            String safeName = (dateFrom != null) ? "Filtered_Log" : "Full_Log";
            String fileName = "Audit_" + safeName + "_" + System.currentTimeMillis() + ".pdf";

            java.io.OutputStream outputStream = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    outputStream = getContentResolver().openOutputStream(uri);
                }
            } else {
                java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs(); // Ensure directory exists to prevent crash
                java.io.File file = new java.io.File(dir, fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            if (outputStream != null) {
                document.writeTo(outputStream);
                outputStream.close();
                Toast.makeText(this, "Audit Log Saved to Downloads!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to create file.", Toast.LENGTH_SHORT).show();
            }

            document.close();

        } catch (Exception e) {
            // CATCH ALL ERRORS: App will no longer crash, it will show a Toast message instead.
            e.printStackTrace();
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // HELPER: Draws the top header and column titles on EVERY new page
    private int drawPdfHeader(Canvas canvas, Paint paint, int pageNumber) {
        int y; // The Y coordinate where our table columns will start

        if (pageNumber == 1) {
            // DRAW BIG PURPLE HEADER (ONLY ON PAGE 1)
            paint.setColor(Color.rgb(98, 0, 238));
            canvas.drawRect(0, 0, 595, 130, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            paint.setFakeBoldText(true);

            String title = "TRANSACTION HISTORY";
            if (dateFrom != null && dateTo != null) {
                title += " (" + df.format(dateFrom) + " - " + df.format(dateTo) + ")";
            }
            canvas.drawText(title, 50, 60, paint);

            paint.setTextSize(14);
            paint.setFakeBoldText(false);
            paint.setColor(Color.rgb(224, 224, 224));
            canvas.drawText("Inventify System Audit Log - Page 1", 50, 95, paint);

            y = 170; // Set column headers lower to make room for the purple box

        } else {
            // DRAW MINIMAL HEADER (FOR PAGE 2 AND BEYOND)
            paint.setColor(Color.GRAY);
            paint.setTextSize(12);
            paint.setFakeBoldText(false);
            canvas.drawText("Transaction History - Page " + pageNumber, 50, 40, paint);

            y = 80; // Set column headers much higher to fit more data!
        }

        // DRAW TABLE COLUMN HEADERS (ON EVERY PAGE SO THE TABLE MAKES SENSE)
        paint.setColor(Color.rgb(240, 240, 240)); // Light Gray Background for row
        canvas.drawRect(50, y - 25, 545, y + 15, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText("Date & Time", 60, y, paint);
        canvas.drawText("Item Name", 230, y, paint);
        canvas.drawText("Action", 400, y, paint);
        canvas.drawText("Qty", 480, y, paint);

        return y + 40; // Return the starting Y coordinate for the data rows
    }


}