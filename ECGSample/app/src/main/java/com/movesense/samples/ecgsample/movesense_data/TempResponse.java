package com.movesense.samples.ecgsample.movesense_data;

import com.google.gson.annotations.SerializedName;

public class TempResponse {

    @SerializedName("Body")
    public final Body body;

    public TempResponse(Body body) {
        this.body = body;
    }

    public static class Body {
        @SerializedName("Timestamp")
        public final long timestamp;

        @SerializedName("Measurement")
        public final double measurement;

        public Body(long timestamp, double measurement) {
            this.measurement = measurement;
            this.timestamp = timestamp;
        }
    }
}
