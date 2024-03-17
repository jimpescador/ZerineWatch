package com.example.myapplication;

import static android.app.Service.START_STICKY;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class MyBackgroundService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Your background task implementation here
        return START_STICKY; // Indicates that the service should be restarted if it's killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        // If your service does not support binding, return null
        return null;
    }
}
