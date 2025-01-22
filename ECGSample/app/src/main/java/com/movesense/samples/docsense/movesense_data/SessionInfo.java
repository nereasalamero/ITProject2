package com.movesense.samples.docsense.movesense_data;

public class SessionInfo {
    public static String deviceId;
    public static String deviceToken;
    public static String username;
    public static String password;

    public static void setDeviceId(String id) {
        deviceId = id;
    }

    public static void setDeviceToken(String token) {
        deviceToken = token;
    }

    public static void setUsername(String newUsername) {
        username = newUsername;
    }

    public static void setPassword(String newPassword) {
        password = newPassword;
    }

    public static String getDeviceId() {
        return deviceId;
    }

    public static String getDeviceToken() {
        return deviceToken;
    }

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }
}
