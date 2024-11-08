package com.movesense.samples.ecgsample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.movesense.mds.internal.connectivity.MovesenseConnectedDevices;

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

public class ECGActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener
{
    private static final int DEFAULT_SAMPLE_RATE = 125;
    static ECGActivity s_INSTANCE = null;
    private static final String LOG_TAG = ECGActivity.class.getSimpleName();

    public static final String SERIAL = "serial";
    String connectedSerial;

    private LineGraphSeries<DataPoint> mSeriesECG;
    private int mDataPointsAppended = 0;
    private MdsSubscription mECGSubscription;

    private TextView textNow, text1Day, text1Week, text1Month;

    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String URI_ECG_INFO = "/Meas/ECG/Info";
    public static final String URI_ECG_ROOT = "/Meas/ECG/";

    Switch mSwitchECGEnabled;
    Spinner mSpinnerSampleRates;

    private ArrayAdapter<String> mSpinnerAdapter;
    private final List<String> mSpinnerRates = new ArrayList<>();

    private Mds getMDS() {return ConnectActivity.mMds;}

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        mSwitchECGEnabled = (Switch)findViewById(R.id.switchECGEnabled);
        mSwitchECGEnabled.setOnCheckedChangeListener(this);

        mSpinnerSampleRates = (Spinner)findViewById(R.id.spinnerSampleRates);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getAndSetUpClickListeners();

        // Find serial in opening intent
        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);


        // Set SampleRate mSpinnerSampleRates
        mSpinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, mSpinnerRates);
        mSpinnerSampleRates.setAdapter(mSpinnerAdapter);

        // Set ECG graph
        GraphView graph = (GraphView) findViewById(R.id.graphECG);
        mSeriesECG = new LineGraphSeries<DataPoint>();
        graph.addSeries(mSeriesECG);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(500);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2000);
        graph.getViewport().setMaxY(2000);

        // Start by getting ECG info
        fetchECGInfo();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG,"onDestroy()");

        unsubscribeAll();
        ECGActivity.s_INSTANCE = null;

        super.onDestroy();
    }

    private void getAndSetUpClickListeners() {
        // Obtener las referencias de los TextViews
        textNow = findViewById(R.id.textNow);
        text1Day = findViewById(R.id.text1Day);
        text1Week = findViewById(R.id.text1Week);
        text1Month = findViewById(R.id.text1Month);

        textNow.setTextColor(Color.BLUE);

        setUpClickListener(textNow);
        setUpClickListener(text1Day);
        setUpClickListener(text1Week);
        setUpClickListener(text1Month);
    }

    private void setUpClickListener(final TextView selectedTextView) {
        selectedTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Cambiar el color de todos los TextViews a gris
                resetTextColors();

                // Poner el texto seleccionado en azul
                selectedTextView.setTextColor(Color.BLUE);
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

    private void fetchECGInfo() {
        String uri = SCHEME_PREFIX + connectedSerial + URI_ECG_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "ECG info succesful: " + data);


                ECGInfoResponse infoResponse = new Gson().fromJson(data, ECGInfoResponse.class);

                // Fill sample rates to the spinner
                mSpinnerRates.clear();
                for (int sampleRate : infoResponse.content.availableSampleRates) {
                    mSpinnerRates.add(""+sampleRate);
                }

                mSpinnerAdapter.notifyDataSetChanged();

                mSpinnerSampleRates.setSelection(mSpinnerAdapter.getPosition(""+DEFAULT_SAMPLE_RATE));

                // Enable Subscription switch
                mSwitchECGEnabled.setEnabled(true);
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "ECG info returned error: " + e);
            }
        });

    }

    private void enableECGSubscription() {
        // Make sure there is no subscription
        unsubscribeECG();

        // Build JSON doc that describes what resource and device to subscribe
        StringBuilder sb = new StringBuilder();
        int sampleRate = Integer.parseInt(""+mSpinnerSampleRates.getSelectedItem());
        final int GRAPH_WINDOW_WIDTH = sampleRate*3;
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(URI_ECG_ROOT).append(sampleRate).append("\"}").toString();
        Log.d(LOG_TAG, strContract);
        // Clear graph
        mSeriesECG.resetData(new DataPoint[0]);
        final GraphView graph = (GraphView) findViewById(R.id.graphECG);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(GRAPH_WINDOW_WIDTH);
        mDataPointsAppended = 0;

        mECGSubscription = getMDS().builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        ECGResponse ecgResponse = new Gson().fromJson(
                                data, ECGResponse.class);

                        if (ecgResponse != null) {
                            if (ecgResponse.body.samples.length > 0) {
                                int ecg = (int)ecgResponse.body.samples[ecgResponse.body.samples.length-1];
                                // Send the ecg to ThingsBoard
                                sendECGToThingsBoard(ecg);
                                ((TextView)findViewById(R.id.textViewECG)).setText("" + ecg);
                            }
                            else {
                                ((TextView)findViewById(R.id.textViewECG)).setText("");
                            }
                            for (int sample : ecgResponse.body.samples) {
                                try {
                                    mSeriesECG.appendData(
                                            new DataPoint(mDataPointsAppended, sample),
                                            mDataPointsAppended >= GRAPH_WINDOW_WIDTH, GRAPH_WINDOW_WIDTH);
                                } catch (IllegalArgumentException e) {
                                    Log.e(LOG_TAG, "GraphView error ", e);
                                }
                                mDataPointsAppended++;
                            }
                        }
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "onError(): ", error);
                        unsubscribeECG();
                    }
                });
    }

    private void sendECGToThingsBoard(int ecg) {
        OkHttpClient client = new OkHttpClient();

        // Reemplaza con tu URL real y el token de ThingsBoard
        String url = "http://iot.ai.ky.local/api/v1/G2udVwz0pfW8388kOSjV/telemetry";

        // Crea el objeto JSON
        String json = "{\"ecg\":" + ecg + "}";

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
                    // Show a successful Toast
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


    private void unsubscribeECG() {
        if (mECGSubscription != null) {
            mECGSubscription.unsubscribe();
            mECGSubscription = null;
        }
    }

    void unsubscribeAll() {
        Log.d(LOG_TAG,"unsubscribeAll()");
        unsubscribeECG();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            enableECGSubscription();
        }
        else {
            unsubscribeECG();
        }

    }
}
