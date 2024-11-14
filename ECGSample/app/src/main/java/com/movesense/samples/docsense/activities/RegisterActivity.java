package com.movesense.samples.docsense.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.movesense.samples.docsense.R;

public class RegisterActivity extends AppCompatActivity {
    private EditText emailRegister, passwordRegister, confirmPasswordRegister;
    private Button btnRegister;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        emailRegister=findViewById(R.id.username_register);
        passwordRegister=findViewById(R.id.password_register);
        confirmPasswordRegister=findViewById(R.id.confirm_password_register);

        btnRegister.setOnClickListener(view -> registerUser());
    }

    private void registerUser() {
        String username = emailRegister.getText().toString().trim();
        String password = passwordRegister.getText().toString().trim();
        String confirmPassword = confirmPasswordRegister.getText().toString().trim();


        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Please, fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }


    }
}
