package com.movesense.samples.ecgsample;

/**
 * Created by lipponep on 22.11.2017.
 */

import com.google.gson.annotations.SerializedName;

public class HRInfoResponse {

    @SerializedName("Content")
    public final Content content;

    public HRInfoResponse(Content content) {
        this.content = content;
    }

    public static class Content {
        @SerializedName("Min")
        public final int min;

        @SerializedName("Max")
        public final int max;

        @SerializedName("Accuracy")
        public final float accuracy;

        public Content(int min, int max, float accuracy) {
            this.min = min;
            this.max = max;
            this.accuracy = accuracy;
        }
    }
}
