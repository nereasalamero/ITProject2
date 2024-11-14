package com.movesense.samples.ecgsample.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.samples.ecgsample.activities.ConnectActivity;
import com.movesense.samples.ecgsample.helpers.DataFetcher;
import com.movesense.samples.ecgsample.movesense_data.ECGResponse;
import com.movesense.samples.ecgsample.movesense_data.HRResponse;
import com.movesense.samples.ecgsample.movesense_data.TempResponse;

public class MeasurementService extends Service {

    public static final int DEFAULT_SAMPLE_RATE = 125;

    private DataFetcher dataFetcher;

    String connectedSerial;
    private Mds getMDS() {
        return ConnectActivity.mMds;
    }

    private static final String LOG_TAG = MeasurementService.class.getSimpleName();
    public static final String URI_EVENT_LISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String URI_MEAS_TEMP = "/Meas/Temp";
    public static final String URI_MEAS_TEMP_INFO = "/Meas/Temp/Info";

    public static final String URI_ECG_INFO = "/Meas/ECG/Info";
    public static final String URI_ECG_ROOT = "/Meas/ECG/";

    public static final String URI_MEAS_HR = "/Meas/HR";
    public static final String URI_MEAS_HR_INFO = "/Meas/HR/Info";

    private MdsSubscription mTempSubscription, mECGSubscription, mHRSubscription;

    public static final String ACTION_TEMP_UPDATE = "com.docsense.TEMPERATURE_UPDATE";
    public static final String EXTRA_TEMP_VALUE = "temperature";
    public static final String ACTION_ECG_UPDATE = "com.docsense.ECG_UPDATE";
    public static final String EXTRA_ECG_VALUE = "ecg";
    public static final String ACTION_HR_UPDATE = "com.docsense.HR_UPDATE";
    public static final String EXTRA_HR_VALUE = "hr";
    public static final String EXTRA_IBI_VALUE = "ibi";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectedSerial = intent.getStringExtra("connectedSerial");
        dataFetcher = new DataFetcher();

        // Initialize subscriptions
        fetchTempInfo();
        fetchECGInfo();
        fetchHRInfo();
        return START_STICKY;
    }

    private void fetchTempInfo() {
        String uri = SCHEME_PREFIX + connectedSerial + URI_MEAS_TEMP_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "Temp info successful: " + data);

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
        // Get sure there is no active subscription
        unsubscribeTemp();

        String strContract = "{\"Uri\": \"" + connectedSerial + URI_MEAS_TEMP + "\"}";
        Log.d(LOG_TAG, strContract);

        mTempSubscription = getMDS().builder().build(this).subscribe(URI_EVENT_LISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        TempResponse tempResponse = new Gson().fromJson(data, TempResponse.class);

                        if (tempResponse != null && tempResponse.body != null) {
                            double temp = tempResponse.body.measurement - 273.15;
                            temp = Math.round(temp * 100.0) / 100.0;

                            // Send data to ThingsBoard
                            dataFetcher.sendValueToThingsBoard(temp, "temperature");

                            // Send broadcast with new value
                            Intent tempIntent = new Intent(ACTION_TEMP_UPDATE);
                            tempIntent.putExtra(EXTRA_TEMP_VALUE, temp);
                            sendBroadcast(tempIntent);
                        }
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "TempSubscription onError(): ", error);
                        unsubscribeTemp();
                    }
                });
    }

    private void unsubscribeTemp() {
        Log.d(LOG_TAG, "unsubscribeAll()");
        if (mTempSubscription != null) {
            mTempSubscription.unsubscribe();
            mTempSubscription = null;
        }
    }

    private void fetchECGInfo() {
        String uri = SCHEME_PREFIX + connectedSerial + URI_ECG_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "ECG info successful: " + data);

                enableECGSubscription();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "ECG info returned error: " + e);
            }
        });
    }

    private void enableECGSubscription() {
        // Get sure there is no active subscription
        unsubscribeECG();

        // Build the JSON contract to get the data
        String strContract = "{\"Uri\": \"" + connectedSerial + URI_ECG_ROOT + DEFAULT_SAMPLE_RATE + "\"}";
        Log.d(LOG_TAG, strContract);

        // Subscription to ECG event
        mECGSubscription = getMDS().builder().build(this).subscribe(URI_EVENT_LISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        ECGResponse ecgResponse = new Gson().fromJson(data, ECGResponse.class);

                        if (ecgResponse != null) {
                            if (ecgResponse.body.samples.length > 0) {
                                int ecg = ecgResponse.body.samples[ecgResponse.body.samples.length - 1];
                                // Send ECG to ThingsBoard
                                dataFetcher.sendValueToThingsBoard(ecg, "ecg");

                                Intent intent = new Intent(ACTION_ECG_UPDATE);
                                intent.putExtra(EXTRA_ECG_VALUE, ecgResponse.body.samples);
                                sendBroadcast(intent);
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

    private void unsubscribeECG() {
        if (mECGSubscription != null) {
            mECGSubscription.unsubscribe();
            mECGSubscription = null;
        }
    }

    private void fetchHRInfo() {
        String uri = SCHEME_PREFIX + connectedSerial + URI_MEAS_HR_INFO;

        getMDS().get(uri, null, new MdsResponseListener() {
            @Override
            public void onSuccess(String data) {
                Log.i(LOG_TAG, "HR info successful: " + data);

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
        // Get sure there is no active subscription
        unsubscribeHR();

        String strContract = "{\"Uri\": \"" + connectedSerial + URI_MEAS_HR + "\"}";
        Log.d(LOG_TAG, strContract);

        mHRSubscription = getMDS().builder().build(this).subscribe(URI_EVENT_LISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        HRResponse hrResponse = new Gson().fromJson(data, HRResponse.class);

                        if (hrResponse != null) {

                            int hr = (int)hrResponse.body.average;
                            int ibi = hrResponse.body.rrData[hrResponse.body.rrData.length - 1];

                            // Send the heart rate to ThingsBoard
                            dataFetcher.sendValueToThingsBoard(hr, "heart_beat");

                            // Send the broadcast with updated value
                            Intent hrIntent = new Intent(ACTION_HR_UPDATE);
                            hrIntent.putExtra(EXTRA_HR_VALUE, hr);
                            hrIntent.putExtra(EXTRA_IBI_VALUE, ibi);
                            sendBroadcast(hrIntent);
                        }
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "TempSubscription onError(): ", error);
                        unsubscribeHR();
                    }
                });
    }

    private void unsubscribeHR() {
        if (mHRSubscription != null) {
            mHRSubscription.unsubscribe();
            mHRSubscription = null;
        }
    }
}
