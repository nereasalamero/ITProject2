package com.movesense.sample.docsense;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    private TextView welcomeText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        welcomeText = findViewById(R.id.welcome_text);

        // Retrieve the username from the Intent
        String username = getIntent().getStringExtra("USERNAME");

        // Display the welcome message with the username
        if (username != null) {
            welcomeText.setText("Welcome " + username + "!");
        }
    }
}

