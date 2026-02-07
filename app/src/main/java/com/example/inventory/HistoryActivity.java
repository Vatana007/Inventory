package com.example.inventory;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.inventory.adapter.TransactionAdapter;
import com.example.inventory.model.Transaction;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We can reuse the Report layout as it has a RecyclerView!
        setContentView(R.layout.activity_report);

        // Hide elements we don't need from the Report layout (Optional polish)
        if(getSupportActionBar() != null) getSupportActionBar().setTitle("Transaction History");

        db = FirebaseFirestore.getInstance();
        transactionList = new ArrayList<>();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TransactionAdapter(transactionList);
        recyclerView.setAdapter(adapter);

        loadHistory();
    }

    private void loadHistory() {
        db.collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING) // Show newest first
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error loading history", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    transactionList.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            Transaction t = doc.toObject(Transaction.class);
                            transactionList.add(t);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}