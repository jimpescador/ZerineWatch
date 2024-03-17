package com.example.myapplication;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class FallDetectionService extends Service {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float acceleration = calculateAcceleration(event.values);
                if (isFallDetected(acceleration)) {
                    handleFallDetected();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAccelerometer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopAccelerometer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAccelerometer() {
        sensorManager.registerListener(
                sensorEventListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
        );
    }

    private void stopAccelerometer() {
        sensorManager.unregisterListener(sensorEventListener);
    }

    private float calculateAcceleration(float[] values) {
        double sumOfSquares = Math.pow(values[0], 2) + Math.pow(values[1], 2) + Math.pow(values[2], 2);
        return (float) Math.sqrt(sumOfSquares);
    }

    private boolean isFallDetected(float acceleration) {
        // Implement fall detection logic based on acceleration values
        // For simplicity, a threshold value is used here. In a real-world scenario, a more sophisticated algorithm would be needed.
        return acceleration > 50.0f;
    }

    private void handleFallDetected() {
        // Broadcast that fall is detected
        Intent intent = new Intent("FALL_DETECTED");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
