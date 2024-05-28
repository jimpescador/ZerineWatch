package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

public class AlertDialogActivityHeart extends Activity {
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mediaPlayer = MediaPlayer.create(this, R.raw.sensor_alert);
        playMusic();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Wake up the device
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        // Create and show an alert dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("                    WARNING");
        builder.setMessage("Your heart rate is high. Please seek for a safe place.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss(); // Dismiss the dialog when OK is clicked
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                finish();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // Schedule a handler to dismiss the dialog after 10 seconds
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (alertDialog.isShowing()) {
                    alertDialog.dismiss();
                }
            }
        }, 15000);

        long[] pattern = {0, 400, 200, 400, 200, 400};
        if (vibrator != null) {
            vibrator.vibrate(pattern, -1); // 200 milliseconds vibration duration
        }
    }
    private void playMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.start();
        }
    }

}
