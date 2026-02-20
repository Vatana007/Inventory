package com.example.inventory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // 1. CHECK FIREBASE SESSION
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            if (currentUser != null) {
                // USER IS ALREADY LOGGED IN

                // 2. RETRIEVE SAVED ROLE
                // We get the role we saved in SharedPreferences during the first login
                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                String savedRole = prefs.getString("USER_ROLE", "Staff");

                // 3. GO STRAIGHT TO MAIN
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                intent.putExtra("USER_ROLE", savedRole);
                startActivity(intent);
            } else {
                // NO SESSION FOUND - GO TO LOGIN
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }

            finish();
        }, 2000);
    }
}