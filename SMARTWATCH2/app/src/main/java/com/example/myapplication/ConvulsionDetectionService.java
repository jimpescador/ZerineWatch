package com.example.myapplication;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ConvulsionDetectionService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastUpdate = 0;
    private static final int SHAKE_THRESHOLD = 800; // Lower threshold for short, intense shakes
    private static final int CONVULSION_SHAKE_COUNT = 15; // Number of shakes to detect convulsion
    private static final long TIMEFRAME = 5000; // Timeframe in milliseconds to count shakes
    private static final long COOLDOWN_PERIOD = 120000; // Cooldown period in milliseconds (2 minutes)
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    private boolean isCooldown = false;

    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && !isCooldown) {
            detectConvulsion(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle changes in sensor accuracy if needed
    }

    private void detectConvulsion(SensorEvent event) {
        final float alpha = 0.8f;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        long curTime = System.currentTimeMillis();
        if ((curTime - lastUpdate) > 100) { // Only process every 100ms
            long diffTime = (curTime - lastUpdate);
            lastUpdate = curTime;

            float x = linear_acceleration[0];
            float y = linear_acceleration[1];
            float z = linear_acceleration[2];

            // Calculate speed
            float speed = Math.abs(x + y + z) / diffTime * 10000;

            // Log for debugging
            //Log.d("ConvulsionDetection", "Speed: " + speed);

            if (speed > SHAKE_THRESHOLD) {
                if (shakeCount == 0) {
                    firstShakeTime = curTime;
                }
                shakeCount++;
                Log.d("ConvulsionDetection", "Shake detected. Count: " + shakeCount);

                if (curTime - firstShakeTime <= TIMEFRAME) {
                    if (shakeCount >= CONVULSION_SHAKE_COUNT) {
                        Log.d("ConvulsionDetection", "Convulsion detected!");
                        Intent intent = new Intent("CONVULSION_DETECTED");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                        shakeCount = 0; // Reset shake count after detecting a convulsion
                        isCooldown = true; // Start cooldown period

                        // Broadcast an intent to notify the main activity
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isCooldown = false; // Reset cooldown flag after cooldown period
                                Log.d("ConvulsionDetection", "Cooldown" );
                            }
                        }, COOLDOWN_PERIOD);
                    }
                } else {
                    shakeCount = 1; // Reset shake count if the timeframe has passed
                    firstShakeTime = curTime;
                }
            }
        }
    }
}
