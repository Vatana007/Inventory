package com.example.inventory.model;

public class InventoryItem {
    private String id;
    private String name;
    private String category; // NEW FIELD
    private int quantity;
    private double price;
    private double sale;     // NEW FIELD
    private int minStock;
    private String dateAdded;
    private String barcode;

    public InventoryItem() {} // Empty constructor for Firestore

    // Updated Constructor with Category and Sale included
    public InventoryItem(String name, int quantity, double price, double sale, String category, int minStock, String dateAdded, String barcode) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.sale = sale;
        this.category = category;
        this.minStock = minStock;
        this.dateAdded = dateAdded;
        this.barcode = barcode;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getSale() { return sale; }
    public void setSale(double sale) { this.sale = sale; }

    public int getMinStock() { return minStock; }
    public void setMinStock(int minStock) { this.minStock = minStock; }

    public String getDateAdded() { return dateAdded; }
    public void setDateAdded(String dateAdded) { this.dateAdded = dateAdded; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}