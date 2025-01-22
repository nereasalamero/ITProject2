package com.movesense.samples.docsense.helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.movesense.samples.docsense.movesense_data.SessionInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SessionDataConnection {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client;
    private static final String LOG_TAG = SessionDataConnection.class.getSimpleName();
    private String token;
    private final String baseUrl = "https://iot.ai.ky.local";

    public SessionDataConnection() {
        client = getUnsafeOkHttpClient();
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

    public void login(String username, String password, Callback callback) {
        Request loginRequest = getTokenRequest(username, password);
        client.newCall(loginRequest).enqueue(callback);
    }

    public Request getTokenRequest(String username, String password) {
        String loginUrl = baseUrl + "/api/auth/login";
        String loginJson = "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}";
        RequestBody body = RequestBody.create(loginJson, JSON);
        return new Request.Builder()
                .url(loginUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
    }

    public void getBearerTokenDevice(String mac) {
        Request loginRequest = getTokenRequest(SessionInfo.getUsername(), SessionInfo.getPassword());
        client.newCall(loginRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(LOG_TAG, "Failure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject jsonResponse = new JSONObject(response.body().string());
                            token = jsonResponse.getString("token");
                            getDevices(mac);
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

    public void getDevices(String mac) {
        String url = baseUrl + "/api/tenant/devices?pageSize=10&page=0";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
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
                            JSONArray devices = jsonResponse.getJSONArray("data");

                            SessionInfo.setDeviceId(null);

                            for (int i = 0; i < devices.length(); i++) {
                                JSONObject device = devices.getJSONObject(i);
                                JSONObject additionalInfo = device.getJSONObject("additionalInfo");

                                if (additionalInfo.has("description")) {
                                    String description = additionalInfo.getString("description");
                                    if (description.contains("MAC: " + mac)) {
                                        SessionInfo.setDeviceId(device.getJSONObject("id").getString("id"));
                                        break;
                                    }
                                }
                            }

                            if (SessionInfo.getDeviceId() != null) {
                                Log.i(LOG_TAG, "Device ID found for MAC " + mac + ": " + SessionInfo.getDeviceId());
                                getDeviceAccessToken();
                            } else {
                                Log.e(LOG_TAG, "Device ID NOT found for MAC " + mac);
                            }
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

    public void getDeviceAccessToken() {
        String url = baseUrl + "/api/device/" + SessionInfo.getDeviceId() + "/credentials";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
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
                            SessionInfo.setDeviceToken(jsonResponse.getString("credentialsId"));
                            Log.i(LOG_TAG, "Device Access Token: " + SessionInfo.getDeviceToken());
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

    public void getUserInfosToken(Callback callback) {
        Request tokenRequest = getTokenRequest(SessionInfo.getUsername(), SessionInfo.getPassword());
        client.newCall(tokenRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(LOG_TAG, "Failure: ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject jsonResponse = new JSONObject(response.body().string());
                            token = jsonResponse.getString("token");
                            getUsersInfo(callback);
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

    public void getUsersInfo(Callback callback) {
        String url = baseUrl + "/api/user/users?pageSize=10&page=0";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(callback);
    }

}
