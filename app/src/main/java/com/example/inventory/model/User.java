package com.example.inventory.model;

public class User {
    private String name;
    private String email;
    private String role; // "Admin" or "Staff"

    public User() {} // Required for Firestore

    public User(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}