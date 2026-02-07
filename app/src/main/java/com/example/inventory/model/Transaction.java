package com.example.inventory.model;

import java.util.Date;

public class Transaction {
    private String itemName;
    private String type; // "IN" or "OUT"
    private int quantityChanged;
    private Date timestamp;

    public Transaction() {}

    public Transaction(String itemName, String type, int quantityChanged) {
        this.itemName = itemName;
        this.type = type;
        this.quantityChanged = quantityChanged;
        this.timestamp = new Date();
    }
    // Add Getters
    public String getItemName() { return itemName; }
    public String getType() { return type; }
    public int getQuantityChanged() { return quantityChanged; }
    public Date getTimestamp() { return timestamp; }
}