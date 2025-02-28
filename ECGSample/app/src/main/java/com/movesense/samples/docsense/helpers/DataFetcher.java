package com.movesense.samples.docsense.helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.movesense.samples.docsense.movesense_data.SessionInfo;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class DataFetcher {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client;
    private static final String LOG_TAG = DataFetcher.class.getSimpleName();
    private String token;
    private final String baseUrl = "https://iot.ai.ky.local";
    private final SessionDataConnection sessionDataConnection;

    public DataFetcher() {
        client = getUnsafeOkHttpClient();
        sessionDataConnection = new SessionDataConnection();
    }

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Configurar un TrustManager que no valide los certificados
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Instalar el TrustManager "de confianza"
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Crear el cliente OkHttp y configurar el TrustManager personalizado
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);  // Desactivar validación de hostname

            // Configuración adicional
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void authenticateAndFetchData(long startTs, long endTs, String measurement, Callback callback) {
        // First request to obtain the token
        Request loginRequest = sessionDataConnection.getTokenRequest(SessionInfo.getUsername(), SessionInfo.getPassword());

        client.newCall(loginRequest).enqueue(new Callback() {
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
                            token = jsonResponse.getString("token");
                            fetchTelemetryData(startTs, endTs, measurement, callback); // Call the second function using the token
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failure: ", e);
                    }
                } else {
                    System.out.println("Error in authentication: " + response.code());
                }
            }
        });
    }

    private void fetchTelemetryData(long startTs, long endTs, String measurement, Callback callback) {
        // Second request to obtain telemetry data
        String telemetryUrl = baseUrl + "/api/plugins/telemetry/DEVICE/" + SessionInfo.getDeviceId() + "/values/timeseries?keys="
                + measurement + "&startTs=" + startTs + "&endTs=" + endTs + "&limit=10000";

        Request telemetryRequest = new Request.Builder()
                .url(telemetryUrl)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Authorization", "Bearer " + token)
                .build();

        client.newCall(telemetryRequest).enqueue(callback);
    }

    public void sendValueToThingsBoard(double value, String measurement) {
        String url = baseUrl + "/api/v1/" + SessionInfo.getDeviceToken() + "/telemetry";
        String json = "{\"" + measurement + "\":" + value + "}";

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        // Send the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Mostrar un Toast con el mensaje de error
                Log.e(LOG_TAG, "Failure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Mostrar un Toast de éxito
                    if (response.body() != null) {
                        Log.e(LOG_TAG, "Success: " + response.body().string());
                    }
                } else {
                    // Mostrar un Toast con el código de respuesta
                    Log.e(LOG_TAG, "Error: " + response.code());
                }
            }
        });

    }
}
