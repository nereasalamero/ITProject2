package com.movesense.samples.docsense.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.movesense.samples.docsense.R;
import com.movesense.samples.docsense.helpers.SessionDataConnection;
import com.movesense.samples.docsense.layout.CircularButton;
import com.movesense.samples.docsense.movesense_data.SessionInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ProfileActivity extends AppCompatActivity {
    private static final String LOG_TAG = ProfileActivity.class.getSimpleName();
    private TextView nameText, lastNameText, emailText, birthDateText, telephoneText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        nameText = findViewById(R.id.value_name);
        lastNameText = findViewById(R.id.value_last_name);
        emailText = findViewById(R.id.value_email);
        birthDateText = findViewById(R.id.value_birth_date);
        telephoneText = findViewById(R.id.value_telephone);

        SessionDataConnection sessionDataConnection = new SessionDataConnection();

        configureButtons();

        sessionDataConnection.getUserInfosToken(createUserDataCallback());
    }

    public void configureButtons() {
        ImageButton circularButton = findViewById(R.id.circularButton);
        ImageButton userIconButton = findViewById(R.id.userIcon);

        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        // Navigate to MainActivity when circularButton is clicked
        circularButton.setOnClickListener(v -> {
            Intent mainIntent;
            if (SessionInfo.getDeviceId() == null) {
                mainIntent = new Intent(this, ConnectActivity.class);
            }
            else {
                mainIntent = new Intent(this, MainActivity.class);
            }

            startActivity(mainIntent);
        });
    }

    private Callback createUserDataCallback() {
        return new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(LOG_TAG, "Failure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject jsonResponse = new JSONObject(response.body().string());
                            JSONArray users = jsonResponse.getJSONArray("data");

                            for (int i = 0; i < users.length(); i++) {
                                JSONObject user = users.getJSONObject(i);

                                // Verify if the name matches the username
                                String name = user.optString("name", "");
                                if (name.equals(SessionInfo.getUsername())) {
                                    // Obtain `firstName` and `lastName`
                                    String firstName = user.optString("firstName", "N/A");
                                    String lastName = user.optString("lastName", "N/A");
                                    String email = user.optString("email", "N/A");
                                    String phone = user.optString("phone", "N/A");

                                    if (!firstName.isEmpty()) nameText.setText(firstName);
                                    if (!firstName.isEmpty()) lastNameText.setText(lastName);
                                    if (!firstName.isEmpty()) emailText.setText(email);
                                    if (!firstName.isEmpty()) telephoneText.setText(phone);

                                    // Obtain and extract `age` from `description`
                                    JSONObject additionalInfo = user.optJSONObject("additionalInfo");
                                    String birthDate = "N/A";
                                    if (additionalInfo != null && additionalInfo.has("description")) {
                                        String description = additionalInfo.getString("description");
                                        if (description.contains("Birth date:")) {
                                            birthDate = description.split("Birth date: ")[1].trim();
                                            birthDateText.setText(birthDate);
                                        }
                                    }

                                    // Log of found data
                                    Log.i(LOG_TAG, "User Info - First Name: " + firstName + ", Last Name: " + lastName);
                                    return; // Break the loop after finding the user
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failure: ", e);
                    }
                } else {
                    Log.e(LOG_TAG, "Error in response: " + response.code());
                }
            }
        };
    }
}

