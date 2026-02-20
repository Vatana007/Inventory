package com.example.inventory;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            // FIX: Uses string resource
            etEmail.setError(getString(R.string.err_email_required));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            // FIX: Uses string resource
            etPassword.setError(getString(R.string.err_password_required));
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Success: Fetch the role from Firestore
                        if (mAuth.getCurrentUser() != null) {
                            checkUserRole(mAuth.getCurrentUser().getUid());
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        // FIX: Uses string resource with formatting for the error message
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown Error";
                        Toast.makeText(LoginActivity.this, getString(R.string.msg_error_prefix, errorMsg), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserRole(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String role = "Staff"; // Default to safest role

                    if (documentSnapshot.exists()) {
                        role = documentSnapshot.getString("role");
                        if (role == null) role = "Staff";
                    }

                    // --- THE REINFORCEMENT FIX ---
                    // 1. Save to SharedPreferences (Permanent storage on phone)
                    SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("USER_ROLE", role).apply();

                    // 2. Pass to Intent (Immediate delivery)
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("USER_ROLE", role);

                    // Clear activity stack so user can't go "back" into login
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    progressBar.setVisibility(View.GONE);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    // FIX: Uses string resource
                    Toast.makeText(this, getString(R.string.err_verify_role, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If the user opens the login screen but Firebase says they are active
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            String role = prefs.getString("USER_ROLE", "Staff");

            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("USER_ROLE", role);
            startActivity(intent);
            finish();
        }
    }
}
