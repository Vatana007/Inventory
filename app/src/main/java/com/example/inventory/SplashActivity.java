package com.example.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide the Action Bar for a clean look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Wait for 2 seconds (2000ms), then go to Login
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if user is already logged in? (Optional optimization)
            // For now, let's just go to LoginActivity
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Prevents user from pressing "Back" to return to Splash
        }, 2000);
    }
}