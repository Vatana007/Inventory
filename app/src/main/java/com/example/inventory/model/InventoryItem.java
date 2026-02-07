package com.example.inventory.model;

public class InventoryItem {
    private String id;
    private String name;
    private int quantity;
    private double price;
    private int minStock;
    private String dateAdded;

    // NEW FIELD
    private String barcode;

    public InventoryItem() {} // Empty constructor for Firestore

    public InventoryItem(String name, int quantity, double price, int minStock, String barcode) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.minStock = minStock;
        this.barcode = barcode;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public int getMinStock() { return minStock; }
    public String getDateAdded() { return dateAdded; }

    // NEW GETTER/SETTER
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}