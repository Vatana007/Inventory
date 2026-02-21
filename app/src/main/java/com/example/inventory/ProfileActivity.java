package com.example.inventory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvUserName, tvUserEmail, tvUserRole;
    private LinearLayout btnEditName, btnChangePassword, btnHelp;
    private MaterialCardView cardLogout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userRole = "Staff";
    private String currentName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Map Views
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvUserRole = findViewById(R.id.tvUserRole);

        btnEditName = findViewById(R.id.btnEditName);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnHelp = findViewById(R.id.btnHelp);
        cardLogout = findViewById(R.id.cardLogout);

        // Fetch User Role
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userRole = prefs.getString("USER_ROLE", "Staff");
        tvUserRole.setText("Role: " + userRole);

        loadUserInfo();
        setupClickListeners();
        setupNavigation();
    }

    private void loadUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            tvUserEmail.setText(currentUser.getEmail());
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && documentSnapshot.getString("name") != null) {
                            currentName = documentSnapshot.getString("name");
                            tvUserName.setText(currentName);
                        }
                    });
        }
    }

    private void setupClickListeners() {
        // 1. EDIT PROFILE NAME
        btnEditName.setOnClickListener(v -> showEditNameDialog());

        // 2. CHANGE PASSWORD
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // 3. HELP & SUPPORT
        btnHelp.setOnClickListener(v -> showHelpDialog());

        // 4. LOGOUT
        cardLogout.setOnClickListener(v -> handleLogout());
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Profile Name");

        // Set up the input field
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(currentName);
        input.setHint("Enter new name");
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                updateNameInFirestore(newName);
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void updateNameInFirestore(String newName) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .update("name", newName)
                    .addOnSuccessListener(aVoid -> {
                        currentName = newName;
                        tvUserName.setText(newName);
                        Toast.makeText(this, "Name updated successfully!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show());
        }
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Change Password")
                    .setMessage("We will send a password reset link to:\n" + user.getEmail())
                    .setPositiveButton("Send Link", (dialog, which) -> {
                        mAuth.sendPasswordResetEmail(user.getEmail())
                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_LONG).show())
                                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Help & Support")
                .setMessage("Inventify v1.0\n\nFor support or bug reports, please contact the IT Administrator.\n\nThank you for using Inventify!")
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleLogout() {
        mAuth.signOut();
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav == null) return;

        bottomNav.setBackground(null);
        bottomNav.getMenu().getItem(2).setEnabled(false);
        bottomNav.setSelectedItemId(R.id.nav_profile);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) return true;

            Intent intent = null;
            if (id == R.id.nav_home) {
                intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (id == R.id.nav_inventory) {
                intent = new Intent(this, InventoryActivity.class);
            } else if (id == R.id.nav_report) {
                if ("Staff".equalsIgnoreCase(userRole)) {
                    Toast.makeText(this, getString(R.string.msg_access_denied), Toast.LENGTH_SHORT).show();
                    return false;
                }
                intent = new Intent(this, ReportActivity.class);
            }

            if (intent != null) {
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            if ("Admin".equalsIgnoreCase(userRole)) {
                fabAdd.setVisibility(View.VISIBLE);
                fabAdd.setOnClickListener(v -> {
                    Intent intent = new Intent(this, AddItemActivity.class);
                    intent.putExtra("USER_ROLE", userRole);
                    startActivity(intent);
                });
            } else {
                fabAdd.setVisibility(View.GONE);
            }
        }
    }
}
