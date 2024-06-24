package com.example.myapplication;

import static android.content.ContentValues.TAG;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

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
        return acceleration > 70.0f;
    }

    private void handleFallDetected() {
        // Write "1" to a specific document in Firebase Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("fallDetection").document("fallDetected");

        Map<String, Object> data = new HashMap<>();
        data.put("fallDetected", 1);
        data.put("timestamp", FieldValue.serverTimestamp());

        docRef.set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Fall detected data written to Firestore");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing fall detected data to Firestore", e);
                    }
                });

        // Broadcast that fall is detected
        Intent intent = new Intent("FALL_DETECTED");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
