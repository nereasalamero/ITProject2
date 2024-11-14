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
import com.movesense.samples.docsense.layout.CircularButton;
import com.movesense.samples.docsense.helpers.DataFetcher;
import com.movesense.samples.docsense.helpers.GraphPainter;
import com.movesense.samples.docsense.R;
import com.movesense.samples.docsense.services.MeasurementService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class HRActivity extends AppCompatActivity
{
    private static final int GRAPH_WINDOW_WIDTH = 100;
    @SuppressLint("StaticFieldLeak")
    public static HRActivity s_INSTANCE = null;
    private static final String LOG_TAG = HRActivity.class.getSimpleName();

    private LineGraphSeries<DataPoint> mSeriesHR;

    private TextView textNow, text1Day, text1Week, text1Month;
    private TextView textViewHRLabel, textViewHR, textViewIBILabel, textViewIBI;
    private GraphView graph;
    LineChart graphHistory;

    private int mDataPointsAppended = 0;

    private DataFetcher dataFetcher;
    private GraphPainter graphPainter;

    private BroadcastReceiver hrReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dataFetcher = new DataFetcher();
        graphPainter = new GraphPainter("HR: ", "bpm");

        textViewHRLabel = findViewById(R.id.textViewHRLabel);
        textViewIBILabel = findViewById(R.id.textViewIBILabel);
        textViewHR = findViewById(R.id.textViewHR);
        textViewIBI = findViewById(R.id.textViewIBI);

        getAndSetUpClickListeners();

        // Set HR graph
        graph = findViewById(R.id.graphHR);
        mSeriesHR = new LineGraphSeries<>();
        graph.addSeries(mSeriesHR);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(200);

        graphHistory = findViewById(R.id.graphHRHistory);

        createBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        HRActivity.s_INSTANCE = null;

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
            // Change all colors to gray
            resetTextColors();
            graphPainter.setCurrentPeriod(period);

            // Change selected text to blue
            selectedTextView.setTextColor(Color.BLUE);
            if (v.getId() == R.id.textNow) {
                textViewHR.setVisibility(View.VISIBLE);
                textViewIBI.setVisibility(View.VISIBLE);
                textViewHRLabel.setVisibility(View.VISIBLE);
                textViewIBILabel.setVisibility(View.VISIBLE);
                graph.setVisibility(View.VISIBLE);
                graphHistory.setVisibility(View.GONE);
            }
            else {
                textViewHR.setVisibility(View.GONE);
                textViewIBI.setVisibility(View.GONE);
                textViewIBILabel.setVisibility(View.GONE);
                textViewHRLabel.setVisibility(View.GONE);
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

        dataFetcher.authenticateAndFetchData(startTs, endTs,"heart_beat"
                , createHRCallback(startTs, endTs));
    }

    private Callback createHRCallback(long startTs, long endTs) {
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
                            JSONArray hrData = jsonResponse.getJSONArray("heart_beat");
                            graphPainter.updateGraph(s_INSTANCE, graphHistory, hrData,
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
        hrReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MeasurementService.ACTION_HR_UPDATE.equals(intent.getAction())) {
                    int hr = intent.getIntExtra(MeasurementService.EXTRA_HR_VALUE, 0);
                    int ibi = intent.getIntExtra(MeasurementService.EXTRA_IBI_VALUE, 0);
                    // Update graph with new values
                    updateHRGraph(hr, ibi);
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MeasurementService.ACTION_HR_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(hrReceiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(hrReceiver);
    }

    private void updateHRGraph(int hr, int ibi) {
        String hrText = "" + hr;
        String ibiText = "" + ibi;
        ((TextView)findViewById(R.id.textViewHR)).setText(hrText);
        ((TextView)findViewById(R.id.textViewIBI)).setText(ibiText);
        try {
            mSeriesHR.appendData(
                    new DataPoint(mDataPointsAppended, hr),
                    mDataPointsAppended >= GRAPH_WINDOW_WIDTH, GRAPH_WINDOW_WIDTH);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "GraphView error ", e);
        }
        mDataPointsAppended++;
    }
}
