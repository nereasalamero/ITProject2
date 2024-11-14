package com.movesense.samples.docsense.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.movesense.samples.docsense.R;

public class SignInActivity extends AppCompatActivity {
    private EditText usernameSignIn, passwordSignIn;
    private Button btnSignIn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        usernameSignIn = findViewById(R.id.username_signin);
        passwordSignIn = findViewById(R.id.password_signin);
        btnSignIn = findViewById(R.id.btn_signin);

        btnSignIn.setOnClickListener(view -> signInUser());
    }

    private void signInUser() {
        String username = usernameSignIn.getText().toString().trim();
        String password = passwordSignIn.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please, fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(SignInActivity.this, ProfileActivity.class);
        intent.putExtra("USERNAME", username); // Pass the username as an extra
        startActivity(intent);
    }
}
