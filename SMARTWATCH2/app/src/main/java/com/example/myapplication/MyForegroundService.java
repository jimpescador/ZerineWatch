package com.example.myapplication;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

    public class MyForegroundService extends Service implements SensorEventListener {
        private static final int NOTIFICATION_ID = 123;
        private static final String CHANNEL_ID = "HeartRateChannel";
        private SensorManager sensorManager;
        private Sensor heartRateSensor;

        @Override
        public void onCreate() {
            super.onCreate();
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heartRateSensor != null) {
                    sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(NOTIFICATION_ID, createNotification());
            return START_STICKY;
        }

        private Notification createNotification() {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Heart Rate Service", NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Heart Rate Service")
                    .setContentText("Monitoring heart rate in the background")
                    .setSmallIcon(R.drawable.logo)
                    .build();
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
                // Handle heart rate sensor data here
                float heartRate = event.values[0];
                // Process heart rate data as needed
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used in this example
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }


