package com.movesense.samples.ecgsample.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageButton;

import com.movesense.samples.ecgsample.layout.CircularButton;
import com.movesense.samples.ecgsample.R;
import com.movesense.samples.ecgsample.services.MeasurementService;

public class MainActivity extends AppCompatActivity  {
    public static final String SERIAL = "serial";
    static MainActivity s_INSTANCE = null;
    String connectedSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        s_INSTANCE = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton circularButton = findViewById(R.id.circularButton);
        Bitmap bitmap = ((BitmapDrawable) getResources().getDrawable(R.drawable.logo)).getBitmap();
        circularButton.setImageBitmap(CircularButton.getCircularBitmap(bitmap));

        Intent intent = getIntent();
        connectedSerial = intent.getStringExtra(SERIAL);

        Intent serviceIntent = new Intent(this, MeasurementService.class);
        serviceIntent.putExtra("connectedSerial", connectedSerial);
        startService(serviceIntent);
    }

    public void onHRClicked(View view) {
        final Activity me = this;

        // Open the ECGActivity
        Intent intent = new Intent(me, HRActivity.class);
        startActivity(intent);
    }

    public void onECGClicked(View view) {
        final Activity me = this;

        // Open the ECGActivity
        Intent intent = new Intent(me, ECGActivity.class);
        startActivity(intent);
    }

    public void onTempClicked(View view) {
        final Activity me = this;

        // Open the TempActivity
        Intent intent = new Intent(me, TempActivity.class);
        startActivity(intent);
    }
}
