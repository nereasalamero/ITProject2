package com.movesense.samples.ecgsample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.gson.Gson;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TempActivity extends AppCompatActivity
{
    private static final int GRAPH_WINDOW_WIDTH = 100;
    static TempActivity s_INSTANCE = null;
    private static final String LOG_TAG = TempActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    private LineGraphSeries<DataPoint> mSeriesTemp;
    private int mDataPointsAppended = 0;
    private MdsSubscription mTempSubscription;

    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String URI_MEAS_TEMP = "/Meas/Temp";
    public static final String URI_MEAS_TEMP_INFO = "/Meas/Temp/Info";

    private Mds getMDS() {return ConnectActivity.mMds;}

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temp);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Find serial in opening intent
        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);

        // Set Temp graph
        GraphView graph = (GraphView) findViewById(R.id.graphTemp);
        mSeriesTemp = new LineGraphSeries<DataPoint>();
        graph.addSeries(mSeriesTemp);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(50);

        // Start by getting Temp info
        fetchTempInfo(graph);
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        unsubscribeAll();
        TempActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    private void fetchTempInfo(GraphView graph) {
        String uri = SCHEME_PREFIX + connectedSerial + URI_MEAS_TEMP_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "Temp info succesful: " + data);

                //TempInfoResponse infoResponse = new Gson().fromJson(data, TempInfoResponse.class);

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
        final GraphView graph = (GraphView) findViewById(R.id.graphTemp);
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
                            ((TextView)findViewById(R.id.textViewTemp)).setText("" + temp);

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

        // Reemplaza con tu URL real y el token de ThingsBoard
        String url = "http://iot.ai.ky.local/api/v1/G2udVwz0pfW8388kOSjV/telemetry";

        // Crea el objeto JSON
        String json = "{\"temperature\":" + temp + "}";

        // Configurar el cuerpo de la solicitud
        RequestBody body = RequestBody.create(json, JSON);

        // Crear la solicitud
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        // Enviar la solicitud
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Mostrar un Toast con el mensaje de error
                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(), "Error sending data to ThingsBoard: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Mostrar un Toast de éxito
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), "Data sent successfully to ThingsBoard", Toast.LENGTH_LONG).show();
                    });
                } else {
                    // Mostrar un Toast con el código de respuesta
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), "Error sending data to ThingsBoard. Error code: " + response.code(), Toast.LENGTH_LONG).show();
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
        Log.d(LOG_TAG,"unsubscribeAll()");
        unsubscribeTemp();
    }
}
