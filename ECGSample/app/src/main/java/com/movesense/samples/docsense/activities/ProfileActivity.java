package com.movesense.samples.docsense.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.movesense.samples.docsense.R;
import com.movesense.samples.docsense.helpers.SessionDataConnection;
import com.movesense.samples.docsense.movesense_data.SessionInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ProfileActivity extends AppCompatActivity {
    private static final String LOG_TAG = ProfileActivity.class.getSimpleName();
    private TextView welcomeText, firstNameText, lastNameText, ageText;
    private SessionDataConnection sessionDataConnection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        welcomeText = findViewById(R.id.welcome_text);
        //firstNameText = findViewById(R.id.first_name_text);
        //lastNameText = findViewById(R.id.last_name_text);
        //ageText = findViewById(R.id.age_text);

        sessionDataConnection = new SessionDataConnection();

        welcomeText.setText("Welcome " + SessionInfo.getUsername() + "to your profile !");

        sessionDataConnection.getUserInfosToken(createUserDataCallback());
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

                                    firstNameText.setText(firstName);
                                    lastNameText.setText(lastName);

                                    // Obtain and extract `age` from `description`
                                    JSONObject additionalInfo = user.optJSONObject("additionalInfo");
                                    String age = "N/A";
                                    if (additionalInfo != null && additionalInfo.has("description")) {
                                        String description = additionalInfo.getString("description");
                                        if (description.contains("Age:")) {
                                            age = description.split("Age:")[1].trim();
                                            ageText.setText(age);
                                        }
                                    }

                                    // Log of found data
                                    Log.i(LOG_TAG, "User Info - First Name: " + firstName + ", Last Name: " + lastName + ", Age: " + age);
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

