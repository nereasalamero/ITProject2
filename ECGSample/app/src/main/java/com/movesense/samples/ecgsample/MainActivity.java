package com.movesense.samples.ecgsample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageButton;

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
    }

    public void onHRClicked(View view) {
        final Activity me = this;

        // Open the ECGActivity
        Intent intent = new Intent(me, HRActivity.class);
        intent.putExtra(HRActivity.SERIAL, connectedSerial);
        startActivity(intent);
    }

    public void onECGClicked(View view) {
        final Activity me = this;

        // Open the ECGActivity
        Intent intent = new Intent(me, ECGActivity.class);
        intent.putExtra(ECGActivity.SERIAL, connectedSerial);
        startActivity(intent);
    }

    public void onTempClicked(View view) {
        final Activity me = this;

        // Open the TempActivity
        Intent intent = new Intent(me, TempActivity.class);
        intent.putExtra(TempActivity.SERIAL, connectedSerial);
        startActivity(intent);
    }
}
