package com.example.inventory.adapter;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.R;
import com.example.inventory.model.InventoryItem;

// NEW ZXING IMPORTS FOR BARCODE GENERATION
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    private List<InventoryItem> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(InventoryItem item);
    }

    public ReportAdapter(List<InventoryItem> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = list.get(position);

        // 1. Bind Basic Data
        holder.tvName.setText(item.getName());
        holder.tvPrice.setText("$" + String.format("%.2f", item.getPrice()));

        if(item.getDateAdded() != null) {
            holder.tvDate.setText("Added: " + item.getDateAdded());
        } else {
            holder.tvDate.setText("Date: N/A");
        }

        // Open details on click
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));

        // 2. SHARE BUTTON LOGIC (Now includes Barcode)
        holder.btnPrint.setOnClickListener(v -> {
            String barcodeStr = (item.getBarcode() != null && !item.getBarcode().isEmpty()) ? item.getBarcode() : "N/A";

            String shareBody = "INVENTORY REPORT\n" +
                    "----------------\n" +
                    "Product: " + item.getName() + "\n" +
                    "Price: $" + String.format("%.2f", item.getPrice()) + "\n" +
                    "Quantity: " + item.getQuantity() + "\n" +
                    "Date: " + (item.getDateAdded() != null ? item.getDateAdded() : "N/A") + "\n" +
                    "Barcode: " + barcodeStr;

            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Report: " + item.getName());
            sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            v.getContext().startActivity(Intent.createChooser(sharingIntent, "Share via"));
        });

        // 3. SAVE PDF BUTTON LOGIC
        holder.btnSave.setOnClickListener(v -> generatePDF(v.getContext(), item));
    }

    @Override
    public int getItemCount() { return list.size(); }

    private void generatePDF(Context context, InventoryItem item) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        // 1. Draw Professional Purple Header
        paint.setColor(Color.rgb(98, 0, 238)); // Purple 500
        canvas.drawRect(0, 0, 595, 140, paint);

        // 2. Header Text
        paint.setColor(Color.WHITE);
        paint.setTextSize(32);
        paint.setFakeBoldText(true);
        canvas.drawText("PRODUCT REPORT", 50, 70, paint);

        paint.setTextSize(16);
        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(224, 224, 224)); // Light Gray
        canvas.drawText("Generated securely by Inventify", 50, 100, paint);

        // 3. Product Title
        paint.setColor(Color.BLACK);
        paint.setTextSize(26);
        paint.setFakeBoldText(true);
        canvas.drawText(item.getName(), 50, 200, paint);

        // 4. Draw a Clean Info Card / Box
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(200, 200, 200));
        paint.setStrokeWidth(2);
        // Made the box taller (ending at 500) to fit the barcode row
        canvas.drawRoundRect(50, 230, 545, 500, 16, 16, paint);

        // 5. Draw Card Data
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(18);
        int startY = 280;
        int leftMargin = 80;
        int valueMargin = 280;

        // Row 1: Price
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Unit Price:", leftMargin, startY, paint);
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText("$" + String.format("%.2f", item.getPrice()), valueMargin, startY, paint);

        // Row 2: Quantity
        startY += 50;
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Quantity in Stock:", leftMargin, startY, paint);
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText(String.valueOf(item.getQuantity()), valueMargin, startY, paint);

        // Row 3: Total Asset Value (Green Text)
        startY += 50;
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Total Asset Value:", leftMargin, startY, paint);
        paint.setColor(Color.rgb(56, 142, 60)); // Green
        paint.setFakeBoldText(true);
        canvas.drawText("$" + String.format("%.2f", item.getPrice() * item.getQuantity()), valueMargin, startY, paint);

        // Row 4: Date
        startY += 50;
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Date Added:", leftMargin, startY, paint);
        paint.setColor(Color.BLACK);
        canvas.drawText((item.getDateAdded() != null) ? item.getDateAdded() : "Unknown", valueMargin, startY, paint);

        // Row 5: Barcode Number
        startY += 50;
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Barcode Number:", leftMargin, startY, paint);
        paint.setColor(Color.BLACK);
        String barcodeStr = (item.getBarcode() != null && !item.getBarcode().isEmpty()) ? item.getBarcode() : "N/A";
        canvas.drawText(barcodeStr, valueMargin, startY, paint);

        // 6. Generate and Draw Barcode Image onto the PDF
        if (item.getBarcode() != null && !item.getBarcode().isEmpty()) {
            try {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                // Generate a 400x120 Bitmap image of the barcode
                Bitmap bitmap = barcodeEncoder.encodeBitmap(item.getBarcode(), BarcodeFormat.CODE_128, 400, 120);

                // Draw it horizontally centered below the info card
                canvas.drawBitmap(bitmap, 97, 540, null);

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Fallback text if no barcode exists
            paint.setColor(Color.LTGRAY);
            paint.setTextSize(14);
            paint.setFakeBoldText(true);
            canvas.drawText("NO BARCODE ATTACHED", 200, 600, paint);
        }

        // 7. Draw Footer
        paint.setTextSize(12);
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("This document is an official system record. Confidential.", 50, 800, paint);

        document.finishPage(page);

        // 8. Save File Logic
        String fileName = "Report_" + item.getName() + "_" + System.currentTimeMillis() + ".pdf";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                    document.writeTo(outputStream);
                    if (outputStream != null) outputStream.close();
                    Toast.makeText(context, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
                }
            } else {
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString();
                java.io.File file = new java.io.File(path, fileName);
                document.writeTo(new java.io.FileOutputStream(file));
                Toast.makeText(context, "Saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        document.close();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvPrice;
        ImageView btnPrint, btnSave;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvReportName);
            tvDate = itemView.findViewById(R.id.tvReportDate);
            tvPrice = itemView.findViewById(R.id.tvReportPrice);
            btnPrint = itemView.findViewById(R.id.btnPrint);
            btnSave = itemView.findViewById(R.id.btnSave);
        }
    }
}
