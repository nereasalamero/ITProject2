package com.movesense.samples.docsense.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.movesense.samples.docsense.R;
import com.movesense.samples.docsense.helpers.SessionDataConnection;
import com.movesense.samples.docsense.movesense_data.SessionInfo;


import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LogInActivity extends AppCompatActivity {
    private static final String LOG_TAG = LogInActivity.class.getSimpleName();
    private EditText usernameSignIn, passwordSignIn;
    private SessionDataConnection sessionDataConnection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameSignIn = findViewById(R.id.username_login);
        passwordSignIn = findViewById(R.id.password_login);
        Button btnSignIn = findViewById(R.id.btn_login);

        sessionDataConnection = new SessionDataConnection();

        btnSignIn.setOnClickListener(view -> logInUser());
    }

    private void logInUser() {
        String username = usernameSignIn.getText().toString().trim();
        String password = passwordSignIn.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please, fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        sessionDataConnection.login(username, password, createLoginCallback(username, password));
    }

    private Callback createLoginCallback(String username, String password)  {
        return new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(LOG_TAG, "Failure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    try {
                        Intent intent = new Intent(LogInActivity.this, ConnectActivity.class);
                        SessionInfo.setUsername(username);
                        SessionInfo.setPassword(password);
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Login successful", Toast.LENGTH_SHORT).show());
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failure: ", e);
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "The username or the password aren't correct", Toast.LENGTH_SHORT).show());
                    Log.e(LOG_TAG, "Failure in login");
                }
            }
        };
    }
}
