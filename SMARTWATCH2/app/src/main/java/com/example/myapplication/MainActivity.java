package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.database.sqlite.SQLiteDatabase;
import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import static com.google.android.gms.wearable.DataMap.TAG;
import static java.lang.Math.round;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import android.content.Intent;



public class MainActivity extends AppCompatActivity {
    private SensorManager mSensorManager; private Sensor mHeartSensor;
    private Sensor offBodySensor; private TextView mTextView; private TextView mTextViewSpo2;



    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startFallDetectionService();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mHeartSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mTextView = (TextView) findViewById(R.id.BPM_Value);
        mTextViewSpo2 = (TextView) findViewById(R.id.SPO2_Value);





    }


    private void startFallDetectionService() {
        Intent serviceIntent = new Intent(this, FallDetectionService.class);
        startService(serviceIntent);
    }
    private void date(){

        Date c = Calendar.getInstance().getTime();
        System.out.println("Current time => " + c);

        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String formattedDate = df.format(c);

        TextView DateTextView = findViewById(R.id.date);
        DateTextView.setText(formattedDate);
    }


    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorEventListener, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mSensorEventListener, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);

    }
    private SensorEventListener mSensorEventListener = new SensorEventListener() {


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            final SensorEvent event1=event;
            mTextView = (TextView) findViewById(R.id.BPM_Value);
            mTextView.setText(Float.toString(event1.values[0]));
            if(event1.values[0] >= 40 && event1.values[0] <= 100) {
                mTextViewSpo2.setText("96%");
            }
            if(event1.values[0] >= 101 && event1.values[0] <= 109) {
                mTextViewSpo2.setText("95%");
            }
            if(event1.values[0] >= 131 ) {
                mTextViewSpo2.setText("93%");
            }
        }


    };


}








