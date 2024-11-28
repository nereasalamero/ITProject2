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

import com.github.mikephil.charting.charts.LineChart;
import com.jjoe64.graphview.DefaultLabelFormatter;
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

public class ECGActivity extends AppCompatActivity
{
    @SuppressLint("StaticFieldLeak")
    public static ECGActivity s_INSTANCE = null;
    private static final String LOG_TAG = ECGActivity.class.getSimpleName();

    private LineGraphSeries<DataPoint> mSeriesECG;
    private int mDataPointsAppended = 0;

    private TextView textNow, text1Day, text1Week, text1Month;
    private TextView textViewECGLabel, textViewECG;
    private GraphView graph;
    LineChart graphHistory;

    private DataFetcher dataFetcher;
    private GraphPainter graphPainter;

    private BroadcastReceiver ecgReceiver;

    final int DEFAULT_GRAPH_WINDOW_WIDTH = MeasurementService.DEFAULT_SAMPLE_RATE * 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);

        configureButtons();

        dataFetcher = new DataFetcher();
        graphPainter = new GraphPainter("ECG: ", "mV");

        textViewECGLabel = findViewById(R.id.textViewECGLabel);
        textViewECG = findViewById(R.id.textViewECG);

        getAndSetUpClickListeners();

        // Set ECG graph
        graph = findViewById(R.id.graphECG);
        mSeriesECG = new LineGraphSeries<>();
        graph.addSeries(mSeriesECG);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(DEFAULT_GRAPH_WINDOW_WIDTH);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2000);
        graph.getViewport().setMaxY(2000);

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter());
        graph.getGridLabelRenderer().setHumanRounding(false);
        graph.getGridLabelRenderer().setNumHorizontalLabels(4);

        mSeriesECG.resetData(new DataPoint[0]);
        mDataPointsAppended = 0;

        graphHistory = findViewById(R.id.graphECGHistory);

        createBroadcastReceiver();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        ECGActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    public void configureButtons() {
        ImageButton circularButton = findViewById(R.id.circularButton);
        ImageButton userIconButton = findViewById(R.id.userIcon);

        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        // Navigate to MainActivity when circularButton is clicked
        circularButton.setOnClickListener(v -> {
            Intent mainIntent = new Intent(this, MainActivity.class);
            startActivity(mainIntent);
        });

        // Navigate to ProfileActivity when userIconButton is clicked
        userIconButton.setOnClickListener(v -> {
            Intent profileIntent = new Intent(this, ProfileActivity.class);
            startActivity(profileIntent);
        });
    }

    private void getAndSetUpClickListeners() {
        // Obtain TextViews references
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
            // Change all TextViews colors to gray
            resetTextColors();
            graphPainter.setCurrentPeriod(period);

            // Put selected text in blue
            selectedTextView.setTextColor(Color.BLUE);

            if (v.getId() == R.id.textNow) {
                textViewECG.setVisibility(View.VISIBLE);
                textViewECGLabel.setVisibility(View.VISIBLE);
                graph.setVisibility(View.VISIBLE);
                graphHistory.setVisibility(View.GONE);
            }
            else {
                textViewECG.setVisibility(View.GONE);
                textViewECGLabel.setVisibility(View.GONE);
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

        dataFetcher.authenticateAndFetchData(startTs, endTs,"ecg",
                createECGCallback(startTs, endTs));
    }

    private Callback createECGCallback(long startTs, long endTs) {
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
                            JSONArray ecgData = jsonResponse.getJSONArray("ecg");
                            graphPainter.updateGraph(s_INSTANCE, graphHistory, ecgData, "ECG",
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

    public void createBroadcastReceiver() {
        ecgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MeasurementService.ACTION_ECG_UPDATE.equals(intent.getAction())) {
                    int[] samples = intent.getIntArrayExtra(MeasurementService.EXTRA_ECG_VALUE);
                    // Update the graph with new values
                    if (samples != null) {
                        updateECGGraph(samples);
                    }
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MeasurementService.ACTION_ECG_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(ecgReceiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(ecgReceiver);
    }

    private void updateECGGraph(int[] samples) {
        if (samples.length > 0) {
            int ecg = samples[samples.length - 1];
            // Send the ecg to ThingsBoard
            String text = "" + ecg;
            ((TextView)findViewById(R.id.textViewECG)).setText(text);
        }
        else {
            ((TextView)findViewById(R.id.textViewECG)).setText("");
        }

        for (int sample : samples) {
            try {
                mSeriesECG.appendData(
                        new DataPoint(mDataPointsAppended, sample),
                        mDataPointsAppended >= DEFAULT_GRAPH_WINDOW_WIDTH, DEFAULT_GRAPH_WINDOW_WIDTH);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "GraphView error ", e);
            }
            mDataPointsAppended++;
        }
    }
}
