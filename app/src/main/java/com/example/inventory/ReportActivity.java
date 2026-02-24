package com.example.inventory;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
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
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

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

    private TextView tvStartDate, tvEndDate, tvReportTotal;
    private Button btnClearDate;

    private Date startDate = null;
    private Date endDate = null;
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    private String userRole = "Staff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        if (getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        db = FirebaseFirestore.getInstance();
        fullList = new ArrayList<>();
        filteredList = new ArrayList<>();

        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        tvReportTotal = findViewById(R.id.tvReportTotal);
        btnClearDate = findViewById(R.id.btnClearDate);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // THE FIX: We now handle the click right here and open the ItemDetailActivity
        adapter = new ReportAdapter(filteredList, item -> {
            Intent intent = new Intent(ReportActivity.this, ItemDetailActivity.class);
            intent.putExtra("itemId", item.getId());
            intent.putExtra("USER_ROLE", userRole);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        setupClickListeners();
        setupNavigation();
        loadReports();
    }

    private void setupClickListeners() {
        findViewById(R.id.btnStartDate).setOnClickListener(v -> showDatePicker(true));
        findViewById(R.id.btnEndDate).setOnClickListener(v -> showDatePicker(false));
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());

        btnClearDate.setOnClickListener(v -> {
            startDate = null;
            endDate = null;
            tvStartDate.setText("Select Date");
            tvEndDate.setText("Select Date");
            btnClearDate.setVisibility(View.GONE);
            applyFilter();
        });
    }

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

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();

        if (isStart && startDate != null) cal.setTime(startDate);
        else if (!isStart && endDate != null) cal.setTime(endDate);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selectedCal = Calendar.getInstance();

            if (isStart) {
                selectedCal.set(year, month, dayOfMonth, 0, 0, 0);
                startDate = selectedCal.getTime();
                tvStartDate.setText(displayFormat.format(startDate));
            } else {
                selectedCal.set(year, month, dayOfMonth, 23, 59, 59);
                endDate = selectedCal.getTime();
                tvEndDate.setText(displayFormat.format(endDate));
            }

            btnClearDate.setVisibility(View.VISIBLE);
            applyFilter();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        dialog.setTitle(isStart ? getString(R.string.title_select_start_date) : getString(R.string.title_select_end_date));
        dialog.show();
    }

    private void applyFilter() {
        filteredList.clear();
        double totalValue = 0;

        if (startDate == null && endDate == null) {
            filteredList.addAll(fullList);
        } else {
            for (InventoryItem item : fullList) {
                String dateStr = item.getDateAdded();
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        Date itemDate = displayFormat.parse(dateStr);
                        if (itemDate != null) {
                            boolean passes = true;
                            if (startDate != null && itemDate.before(startDate)) passes = false;
                            if (endDate != null && itemDate.after(endDate)) passes = false;

                            if (passes) {
                                filteredList.add(item);
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        }

        for (InventoryItem item : filteredList) {
            totalValue += (item.getQuantity() * item.getPrice());
        }
        tvReportTotal.setText("$" + String.format("%.2f", totalValue));

        adapter.notifyDataSetChanged();
    }

    private void showExportDialog() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_export_report))
                .setMessage(getString(R.string.dialog_choose_format))
                .setPositiveButton(getString(R.string.dialog_pdf), (dialog, which) -> generateBulkPDF())
                .setNegativeButton(getString(R.string.dialog_csv), (dialog, which) -> generateCSV())
                .setNeutralButton(getString(R.string.dialog_btn_cancel), null)
                .show();
    }

    private String getDynamicReportTitle() {
        if (startDate != null && endDate != null) {
            return "REPORT: " + displayFormat.format(startDate) + " TO " + displayFormat.format(endDate);
        } else if (startDate != null) {
            return "REPORT: FROM " + displayFormat.format(startDate);
        } else if (endDate != null) {
            return "REPORT: UNTIL " + displayFormat.format(endDate);
        }
        return "FULL INVENTORY REPORT";
    }

    private void generateBulkPDF() {
        PdfDocument document = new PdfDocument();
        int pageNumber = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        int y = drawReportPdfHeader(canvas, paint, pageNumber);

        double totalValue = 0;
        int rowCount = 0;

        for (InventoryItem item : filteredList) {

            // Check if we reached the bottom (Leaving room for the footer)
            if (y > 740) {
                document.finishPage(page);
                pageNumber++;
                pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = drawReportPdfHeader(canvas, paint, pageNumber);
            }

            if (rowCount % 2 != 0) {
                paint.setColor(Color.rgb(250, 250, 250));
                canvas.drawRect(50, y - 35, 545, y + 35, paint);
            }

            paint.setColor(Color.BLACK);
            paint.setTextSize(14);
            paint.setFakeBoldText(true);
            canvas.drawText(item.getName(), 60, y - 5, paint);

            paint.setTextSize(10);
            paint.setColor(Color.GRAY);
            paint.setFakeBoldText(false);
            String bText = (item.getBarcode() != null && !item.getBarcode().isEmpty()) ? item.getBarcode() : "N/A";
            canvas.drawText("SN: " + bText, 60, y + 12, paint);

            paint.setTextSize(14);
            paint.setColor(Color.BLACK);
            canvas.drawText(String.valueOf(item.getQuantity()), 230, y, paint);
            canvas.drawText("$" + String.format("%.2f", item.getPrice()), 280, y, paint);

            double lineTotal = item.getPrice() * item.getQuantity();
            paint.setColor(Color.rgb(56, 142, 60)); // Green
            paint.setFakeBoldText(true);
            canvas.drawText("$" + String.format("%.2f", lineTotal), 350, y, paint);
            paint.setFakeBoldText(false);

            if (!bText.equals("N/A")) {
                try {
                    BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                    Bitmap bitmap = barcodeEncoder.encodeBitmap(bText, BarcodeFormat.CODE_128, 90, 35);
                    canvas.drawBitmap(bitmap, 440, y - 20, null);
                } catch (Exception e) { e.printStackTrace(); }
            } else {
                paint.setColor(Color.LTGRAY);
                paint.setTextSize(10);
                canvas.drawText("NO BARCODE", 445, y, paint);
            }

            y += 70;
            totalValue += lineTotal;
            rowCount++;
        }

        // Draw Footer Total Block at the very end
        paint.setColor(Color.rgb(98, 0, 238));
        canvas.drawRect(50, y - 10, 545, y + 35, paint);

        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(16);
        canvas.drawText("TOTAL INVENTORY VALUE:", 150, y + 15, paint);
        canvas.drawText("$" + String.format("%.2f", totalValue), 350, y + 15, paint);

        document.finishPage(page);

        // Save Logic
        String safeName = (startDate != null) ? "Filtered_Range" : "Full_Report";
        String fileName = "Inventify_" + safeName + "_" + System.currentTimeMillis() + ".pdf";
        saveFile(document, fileName, "application/pdf");
    }

    private int drawReportPdfHeader(Canvas canvas, Paint paint, int pageNumber) {
        paint.setColor(Color.rgb(98, 0, 238));
        canvas.drawRect(0, 0, 595, 130, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        canvas.drawText(getDynamicReportTitle(), 50, 60, paint);

        paint.setTextSize(14);
        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(224, 224, 224));
        canvas.drawText("Inventify Full System Report - Page " + pageNumber, 50, 95, paint);

        int y = 170;
        paint.setColor(Color.rgb(240, 240, 240));
        canvas.drawRect(50, y - 25, 545, y + 15, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(12);
        paint.setFakeBoldText(true);
        canvas.drawText("Product", 60, y, paint);
        canvas.drawText("Qty", 230, y, paint);
        canvas.drawText("Price", 280, y, paint);
        canvas.drawText("Total Asset", 350, y, paint);
        canvas.drawText("Barcode", 450, y, paint);

        return y + 45; // Start Y for rows
    }

    private void generateCSV() {
        StringBuilder data = new StringBuilder();
        data.append("Name,Quantity,Price,Min Stock,Date Added,Total Value\n");
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

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_report);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_report) return true; // Already here

            Intent intent = null;
            if (id == R.id.nav_home) {
                intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (id == R.id.nav_inventory) {
                intent = new Intent(this, InventoryActivity.class);
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

        // Make sure FAB still works
        View fabAdd = findViewById(R.id.fabAdd);
        if(fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddItemActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
            });
        }
    }

}