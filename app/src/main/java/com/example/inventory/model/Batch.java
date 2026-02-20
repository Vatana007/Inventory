package com.example.inventory.model;

import java.util.Date;

public class Batch {
    private String id;
    private int originalQty;
    private int remainingQty;
    private Date dateReceived;

    public Batch() {} // Firestore requires empty constructor

    public Batch(int qty) {
        this.originalQty = qty;
        this.remainingQty = qty;
        this.dateReceived = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getOriginalQty() { return originalQty; }
    public int getRemainingQty() { return remainingQty; }
    public void setRemainingQty(int remainingQty) { this.remainingQty = remainingQty; }
    public Date getDateReceived() { return dateReceived; }
}
