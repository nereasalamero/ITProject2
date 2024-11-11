package com.movesense.samples.ecgsample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.gson.Gson;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public class TempActivity extends AppCompatActivity {
    private static final int GRAPH_WINDOW_WIDTH = 100;
    static TempActivity s_INSTANCE = null;
    private static final String LOG_TAG = TempActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    private LineGraphSeries<DataPoint> mSeriesTemp;
    private int mDataPointsAppended = 0;
    private MdsSubscription mTempSubscription;

    private TextView textNow, text1Day, text1Week, text1Month;
    private TextView textViewTempLabel, textViewTemp;
    private GraphView graph;
    LineChart graphHistory;

    private DataFetcher dataFetcher;
    private GraphPainter graphPainter;

    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String URI_MEAS_TEMP = "/Meas/Temp";
    public static final String URI_MEAS_TEMP_INFO = "/Meas/Temp/Info";

    private Mds getMDS() {
        return ConnectActivity.mMds;
    }

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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

        // Find serial in opening intent
        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);

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

        graphHistory = findViewById(R.id.graphTempHistory);

        // Start by getting Temp info
        fetchTempInfo();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");

        unsubscribeAll();
        TempActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    private void getAndSetUpClickListeners() {
        // Obtener las referencias de los TextViews
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
            // Cambiar el color de todos los TextViews a gris
            resetTextColors();
            graphPainter.setCurrentPeriod(period);

            // Poner el texto seleccionado en azul
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

    // Método para poner todos los TextViews en gris
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
        "e26e2190-7659-11ef-bb3c-c31935dd788d", createTemperatureCallback(startTs, endTs));
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
                    System.out.println("Error en consulta de datos: " + response.code());
                }
            }
        };
    }

    private void fetchTempInfo() {
        String uri = SCHEME_PREFIX + connectedSerial + URI_MEAS_TEMP_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "Temp info succesful: " + data);

                // Subscribe to Temp/IBI
                enableTempSubscription();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "Temp info returned error: " + e);
            }
        });

    }

    private void enableTempSubscription() {
        // Make sure there is no subscription
        unsubscribeTemp();

        // Build JSON doc that describes what resource and device to subscribe
        StringBuilder sb = new StringBuilder();
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(URI_MEAS_TEMP).append("\"}").toString();
        Log.d(LOG_TAG, strContract);

        // Clear graph
        mSeriesTemp.resetData(new DataPoint[0]);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);
        mDataPointsAppended = 0;
        List<Double> samples = new ArrayList<>();

        mTempSubscription = getMDS().builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        TempResponse tempResponse = new Gson().fromJson(data, TempResponse.class);

                        if (tempResponse != null && tempResponse.body != null) {
                            double temp = tempResponse.body.measurement - 273.15;
                            temp = Math.round(temp * 100.0) / 100.0;
                            samples.add(temp);
                            // Send the temperature to ThingsBoard
                            sendTempToThingsBoard(temp);
                            ((TextView) findViewById(R.id.textViewTemp)).setText("" + temp);

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

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "TempSubscription onError(): ", error);
                        unsubscribeTemp();
                    }
                });

    }

    private void sendTempToThingsBoard(double temp) {
        OkHttpClient client = new OkHttpClient();

        String url = "http://iot.ai.ky.local/api/v1/G2udVwz0pfW8388kOSjV/telemetry";
        String json = "{\"temperature\":" + temp + "}";

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        // Send the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Mostrar un Toast con el mensaje de error
                runOnUiThread(() -> {
                    if (graph.getVisibility() == (View.VISIBLE)) {
                        Toast.makeText(getApplicationContext(), "Error sending data to ThingsBoard: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Mostrar un Toast de éxito
                    runOnUiThread(() -> {
                        if (graph.getVisibility() == (View.VISIBLE)) {
                            Toast.makeText(getApplicationContext(), "Data sent successfully to ThingsBoard", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    // Mostrar un Toast con el código de respuesta
                    runOnUiThread(() -> {
                        if (graph.getVisibility() == (View.VISIBLE)) {
                            Toast.makeText(getApplicationContext(), "Error sending data to ThingsBoard. Error code: " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

    }

    private void unsubscribeTemp() {
        if (mTempSubscription != null) {
            mTempSubscription.unsubscribe();
            mTempSubscription = null;
        }
    }

    void unsubscribeAll() {
        Log.d(LOG_TAG, "unsubscribeAll()");
        unsubscribeTemp();
    }
}
