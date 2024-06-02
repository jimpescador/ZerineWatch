package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;


import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import android.content.pm.PackageManager;

public class SensorForegroundService extends Service implements SensorEventListener {
    private static final int NOTIFICATION_ID = 128;
    private static final String CHANNEL_ID = "SensorChannel";
    private SensorManager sensorManager;
    private Sensor sensor;
    private PowerManager.WakeLock wakeLock;
    private Sensor heartRateSensor;
    private Handler handler;
    private boolean isCooldown;
    private int warningValue;
    private boolean isSeizureAlertShown = false;

    private FirebaseFirestore db;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPref = getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        warningValue = sharedPref.getInt("warning", 100);

        handler = new Handler();
        isCooldown = false;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (heartRateSensor != null) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
                Log.e("Sensor Service", "Heart rate sensor registered");
            } else {
                Log.e("Sensor Service", "Heart rate sensor not available");
            }
        }

        db = FirebaseFirestore.getInstance();
        // Acquire a wake lock to keep the CPU running
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::SensorWakeLockTag");
        wakeLock.acquire();

        // Initialize the sensor manager and sensor
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }


        // Create a notification for the foreground service
        Notification notification = createNotification();


        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startCooldown() {
        isCooldown = true;
        handler.postDelayed(() -> isCooldown = false, 600000); // Cooldown for 10 minutes
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());

//For testing
//        new Thread(
//                new Runnable() {
//                    @Override
//                    public void run() {
//                        while (true) {
//                            Log.e("Sensor", "Service is running...");
//                            try {
//                                Thread.sleep(20000);
//                                showAlert();
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                }
//        ).start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Heart Rate Service", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Service")
                .setContentText("Monitoring sensor in the background")
                .setSmallIcon(R.drawable.logo)
                .build();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Handle the sensor change
        // Show an alert dialog when the sensor changes
        //float heartRate = event.values[0];
        //Log.e("Sensor","Sensor Change Service");
        //Log.e("Sensor","Heart Rate: " + heartRate);

        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            float heartRate = event.values[0];
            Log.e("Sensor Service", "Heart Rate: " + heartRate + " / " + warningValue);
            // You can update UI or store the value as needed
            db.collection("TriggerValues")
                    .document("sharedTriggerValues")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // Retrieve minTriggerValue
                                int Alert = document.getLong("Alert").intValue();

                                // Now you can use minTriggerValue in your sensor check

                                if (heartRate >= Alert) {
                                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                                    PowerManager.WakeLock screenWakeLock = powerManager.newWakeLock(
                                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                            "MyApp::ScreenWakeLockTag"
                                    );
                                    screenWakeLock.acquire(60 * 1000L); // 1 minute
                                    handler.postDelayed(screenWakeLock::release, 60 * 1000L);

                                    // Show seizure alert
                                    //showAlert();
                                }
                            }
                        }
                    });

            db.collection("TriggerValues")
                    .document("sharedTriggerValues")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // Retrieve minTriggerValue
                                int minTriggerValue = document.getLong("Warning").intValue();

                                // Now you can use minTriggerValue in your sensor check

                                if (heartRate >= minTriggerValue) {
                                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                                    PowerManager.WakeLock screenWakeLock = powerManager.newWakeLock(
                                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                            "MyApp::ScreenWakeLockTag"
                                    );
                                    screenWakeLock.acquire(60 * 1000L); // 1 minute
                                    handler.postDelayed(screenWakeLock::release, 60 * 1000L);

                                }

                            }

                        }
                    });

        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes here if needed
    }

}



