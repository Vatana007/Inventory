package com.example.inventory.model;

public class User {
    private String name;
    private String email;
    private String role; // "Admin" or "Staff"
    private String status; // NEW: "pending" or "approved"

    public User() {} // Required for Firestore

    public User(String name, String email, String role, String status) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
}
