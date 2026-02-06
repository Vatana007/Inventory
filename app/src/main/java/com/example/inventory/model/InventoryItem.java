package com.example.inventory.model;

public class InventoryItem {
    private String id;
    private String name;
    private int quantity;
    private double price;
    private int minStock;
    private String dateAdded; // <--- NEW FIELD

    public InventoryItem() {} // Empty constructor needed for Firebase

    // Updated Constructor
    public InventoryItem(String name, int quantity, double price, int minStock, String dateAdded) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.minStock = minStock;
        this.dateAdded = dateAdded;
    }

    // Getters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public int getMinStock() { return minStock; }

    // New Getter
    public String getDateAdded() { return dateAdded; }
}