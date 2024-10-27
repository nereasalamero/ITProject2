package com.movesense.samples.ecgsample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
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

public class HRActivity extends AppCompatActivity
{
    private static final int GRAPH_WINDOW_WIDTH = 100;
    static HRActivity s_INSTANCE = null;
    private static final String LOG_TAG = HRActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    private LineGraphSeries<DataPoint> mSeriesHR;
    private int mDataPointsAppended = 0;
    private MdsSubscription mHRSubscription;

    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String URI_MEAS_HR = "/Meas/HR";
    public static final String URI_MEAS_HR_INFO = "/Meas/HR/Info";

    private Mds getMDS() {return ConnectActivity.mMds;}

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Find serial in opening intent
        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);

        // Set HR graph
        GraphView graph = (GraphView) findViewById(R.id.graphHR);
        mSeriesHR = new LineGraphSeries<DataPoint>();
        graph.addSeries(mSeriesHR);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(200);

        // Start by getting HR info
        fetchHRInfo(graph);
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        unsubscribeAll();
        HRActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    private void fetchHRInfo(GraphView graph) {
        String uri = SCHEME_PREFIX + connectedSerial + URI_MEAS_HR_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "HR info succesful: " + data);

                HRInfoResponse infoResponse = new Gson().fromJson(data, HRInfoResponse.class);

                // Subscribe to HR/IBI
                enableHRSubscription();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "HR info returned error: " + e);
            }
        });

    }

    private void enableHRSubscription() {
        // Make sure there is no subscription
        unsubscribeHR();

        // Build JSON doc that describes what resource and device to subscribe
        StringBuilder sb = new StringBuilder();
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(URI_MEAS_HR).append("\"}").toString();
        Log.d(LOG_TAG, strContract);

        // Clear graph
        mSeriesHR.resetData(new DataPoint[0]);
        final GraphView graph = (GraphView) findViewById(R.id.graphHR);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);
        mDataPointsAppended = 0;
        List<Integer> samples = new ArrayList<>();

        mHRSubscription = getMDS().builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        HRResponse hrResponse = new Gson().fromJson(data, HRResponse.class);

                        if (hrResponse != null) {

                            int hr = (int)hrResponse.body.average;
                            samples.add(hr);
                            // Send the heart rate to ThingsBoard
                            sendHeartRateToThingsBoard(hr);
                            ((TextView)findViewById(R.id.textViewHR)).setText("" + hr);
                            ((TextView)findViewById(R.id.textViewIBI)).setText(hrResponse.body.rrData.length > 0 ? "" + hrResponse.body.rrData[hrResponse.body.rrData.length-1] : "--");

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

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "HRSubscription onError(): ", error);
                        unsubscribeHR();
                    }
                });
        
    }

    private void sendHeartRateToThingsBoard(int heartRate) {
        OkHttpClient client = new OkHttpClient();

        // Reemplaza con tu URL real y el token de ThingsBoard
        String url = "http://iot.ai.ky.local/api/v1/G2udVwz0pfW8388kOSjV/telemetry";

        // Crea el objeto JSON
        String json = "{\"heart_beat\":" + heartRate + "}";

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

    private void unsubscribeHR() {
        if (mHRSubscription != null) {
            mHRSubscription.unsubscribe();
            mHRSubscription = null;
        }
    }

    void unsubscribeAll() {
        Log.d(LOG_TAG,"unsubscribeAll()");
        unsubscribeHR();
    }
}