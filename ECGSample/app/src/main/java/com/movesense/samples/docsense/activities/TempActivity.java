package com.movesense.samples.docsense.activities;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import com.movesense.samples.docsense.layout.*;
import com.movesense.samples.docsense.helpers.DataFetcher;
import com.movesense.samples.docsense.helpers.GraphPainter;
import com.movesense.samples.docsense.R;
import com.movesense.samples.docsense.services.MeasurementService;

public class TempActivity extends AppCompatActivity {
    private static final int GRAPH_WINDOW_WIDTH = 100;
    @SuppressLint("StaticFieldLeak")
    public static TempActivity s_INSTANCE = null;
    private static final String LOG_TAG = TempActivity.class.getSimpleName();

    private LineGraphSeries<DataPoint> mSeriesTemp;
    private int mDataPointsAppended = 0;

    private TextView textNow, text1Day, text1Week, text1Month;
    private TextView textViewTempLabel, textViewTemp;
    private GraphView graph;
    LineChart graphHistory;

    private DataFetcher dataFetcher;
    private GraphPainter graphPainter;

    private BroadcastReceiver tempReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temp);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        Toolbar toolbar =  findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dataFetcher = new DataFetcher();
        graphPainter = new GraphPainter("Temp: ", "°C");

        textViewTempLabel = findViewById(R.id.textViewTempLabel);
        textViewTemp = findViewById(R.id.textViewTemp);

        getAndSetUpClickListeners();

        // Set Temp graph
        graph = findViewById(R.id.graphTemp);
        mSeriesTemp = new LineGraphSeries<>();
        graph.addSeries(mSeriesTemp);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(50);

        mSeriesTemp.resetData(new DataPoint[0]);
        mDataPointsAppended = 0;

        graphHistory = findViewById(R.id.graphTempHistory);

        createBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");

        TempActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    private void getAndSetUpClickListeners() {
        // Get TextViews references
        textNow = findViewById(R.id.textNow);
        text1Day = findViewById(R.id.text1Day);
        text1Week = findViewById(R.id.text1Week);
        text1Month = findViewById(R.id.text1Month);

        textNow.setTextColor(Color.BLUE);

        setUpClickListener(textNow, null);
        setUpClickListener(text1Day, GraphPainter.Period.DAY);
        setUpClickListener(text1Week, GraphPainter.Period.WEEK);
        setUpClickListener(text1Month, GraphPainter.Period.MONTH);
    }

    private void setUpClickListener(final TextView selectedTextView, GraphPainter.Period period) {
        selectedTextView.setOnClickListener(v -> {
            // Put every text in gray
            resetTextColors();
            graphPainter.setCurrentPeriod(period);

            // Put selected text in blue
            selectedTextView.setTextColor(Color.BLUE);
            if (v.getId() == R.id.textNow) {
                textViewTemp.setVisibility(View.VISIBLE);
                textViewTempLabel.setVisibility(View.VISIBLE);
                graph.setVisibility(View.VISIBLE);
                graphHistory.setVisibility(View.GONE);
            } else {
                textViewTemp.setVisibility(View.GONE);
                textViewTempLabel.setVisibility(View.GONE);
                graph.setVisibility(View.GONE);
                graphHistory.setVisibility(View.VISIBLE);
                fetchAndDisplayData(period);
            }
        });
    }

    private void resetTextColors() {
        textNow.setTextColor(Color.GRAY);
        text1Day.setTextColor(Color.GRAY);
        text1Week.setTextColor(Color.GRAY);
        text1Month.setTextColor(Color.GRAY);
    }

    private void fetchAndDisplayData(GraphPainter.Period period) {
        long[] timestamps = graphPainter.getTimestampsForPeriod(period);
        long startTs = timestamps[0];
        long endTs = timestamps[1];

        dataFetcher.authenticateAndFetchData(startTs, endTs,"temperature",
                createTemperatureCallback(startTs, endTs));
    }

    private Callback createTemperatureCallback(long startTs, long endTs) {
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
                            JSONArray temperatureData = jsonResponse.getJSONArray("temperature");
                            graphPainter.updateGraph(s_INSTANCE, graphHistory, temperatureData,
                                    startTs, endTs);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failure: ", e);
                    }
                } else {
                    System.out.println("Error in data query: " + response.code());
                }
            }
        };
    }

    private void createBroadcastReceiver() {
        tempReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MeasurementService.ACTION_TEMP_UPDATE.equals(intent.getAction())) {
                    double temp = intent.getDoubleExtra(MeasurementService.EXTRA_TEMP_VALUE, 0.0);
                    // Update temperature graph with new value
                    updateTemperatureGraph(temp);
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MeasurementService.ACTION_TEMP_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(tempReceiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(tempReceiver);
    }

    private void updateTemperatureGraph(double temp) {
        String text = "" + temp;
        ((TextView) findViewById(R.id.textViewTemp)).setText(text);
        try {
            mSeriesTemp.appendData(
                    new DataPoint(mDataPointsAppended, temp),
                    mDataPointsAppended >= GRAPH_WINDOW_WIDTH, GRAPH_WINDOW_WIDTH);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "GraphView error ", e);
        }
        mDataPointsAppended++;
    }
}
