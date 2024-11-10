package com.movesense.samples.ecgsample;

import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DataFetcher {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client;
    private static final String LOG_TAG = DataFetcher.class.getSimpleName();
    private String token;

    public DataFetcher() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }


    public void authenticateAndFetchData(long startTs, long endTs, String deviceId, String measurement, Callback callback) {
        // Primer request para obtener el token
        //String loginUrl = "https://iot.ai.ky.local/api/auth/login";
        String loginUrl = "http://192.168.27.163:8080/api/auth/login";
        String loginJson = "{\"username\":\"s2417022@edu.savonia.fi\", \"password\":\"DocSense123.\"}";
        RequestBody body = RequestBody.create(loginJson, JSON);
        Request loginRequest = new Request.Builder()
                .url(loginUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(loginRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        token = jsonResponse.getString("token");
                        fetchTelemetryData(startTs, endTs, deviceId, measurement, callback); // Llama a la segunda función usando el token
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Error en autenticación: " + response.code());
                }
            }
        });
    }

    private void fetchTelemetryData(long startTs, long endTs, String deviceId, String measurement, Callback callback) {
        // Segundo request para obtener los datos de telemetría
        //String telemetryUrl = "https://iot.ai.ky.local/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?keys="
        String telemetryUrl = "http://192.168.27.163:8080/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?keys="
                + measurement + "&startTs=" + startTs + "&endTs=" + endTs + "&limit=10000";

        Request telemetryRequest = new Request.Builder()
                .url(telemetryUrl)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Authorization", "Bearer " + token)
                .build();

        client.newCall(telemetryRequest).enqueue(callback);
    }


    public JSONArray authenticateAndFetchData(long startTs, long endTs, String deviceId, String measurement) throws JSONException {
        OkHttpClient client = new OkHttpClient();
        JSONArray temperatureData = null;

        // Autenticación
        //String authUrl = "https://iot.ai.ky.local/api/auth/login";
        String authUrl = "http://192.168.27.163:8080/api/auth/login";
        //JSONObject credentials = new JSONObject();
        //credentials.put("username", "s2417022@edu.savonia.fi");
        //credentials.put("password", "DocSense123.");

        String json = "{\"username\":\"s2417022@edu.savonia.fi\", \"password\":\"DocSense123.\"}";

        // Request body
        RequestBody body = RequestBody.create(json, JSON);

        // Build the request
        Request authRequest = new Request.Builder()
                .url(authUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        /*Request authRequest = new Request.Builder()
                .url(authUrl)
                .post(RequestBody.create(credentials.toString(), MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();*/
        try {
            try (Response authResponse = client.newCall(authRequest).execute()) {
                if (authResponse.isSuccessful() && authResponse.body() != null) {
                    JSONObject authResult = new JSONObject(authResponse.body().string());
                    String token = authResult.getString("token");

                    //String url = "https://iot.ai.ky.local/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?keys="
                    String url = "http://192.168.27.163:8080/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?keys="
                            + measurement + "&startTs=" + startTs + "&endTs=" + endTs + "&limit=10000";

                    Request dataRequest = new Request.Builder()
                            .url(url)
                            .addHeader("Accept", "application/json")
                            .addHeader("X-Authorization", "Bearer " + token)
                            .get()
                            .build();

                    try (Response dataResponse = client.newCall(dataRequest).execute()) {
                        if (dataResponse.isSuccessful() && dataResponse.body() != null) {
                            // Convertimos la respuesta a un JSONArray
                            temperatureData = new JSONArray(dataResponse.body().string());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error during authentication or data fetch", e);
        }
        return temperatureData;
    }

    private void visualizeData(JSONArray temperatureData) throws JSONException {
        // Aquí procesa y visualiza los datos de temperatura para el período solicitado
        for (int i = 0; i < temperatureData.length(); i++) {
            JSONObject dataPoint = temperatureData.getJSONObject(i);
            long timestamp = dataPoint.getLong("ts");
            String value = dataPoint.getString("value");
            System.out.println("Timestamp: " + timestamp + ", Temperature: " + value);
            // Aquí puedes agregar código para mostrar los datos en el gráfico
        }
    }
}
