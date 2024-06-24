package com.example.myapplication;

import static android.content.ContentValues.TAG;



import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class AlertDialogActivityHeart extends Activity {
    private Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private MediaPlayer lowplayer;
    private static final String TODO = "";

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static String DEVICE_ADDRESS = "00:00:00:00:00:00"; // Replace with your mobile device's Bluetooth MAC address

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        DEVICE_ADDRESS = getCurrentConnectedDeviceMacAddress();

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

    private void sendData(String data) {


        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();

            // Check if Bluetooth is connected
            boolean isConnected = bluetoothSocket.isConnected();
            if (isConnected) {
                Log.d("MainActivity", "Bluetooth is connected to the device.");
            } else {
                Log.d("MainActivity", "Bluetooth is not connected to the device.");
            }
            outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(data.getBytes());


        } catch (IOException e) {
            Log.e(TAG, "Error sending data: " + e.getMessage());
        } finally {
            /*try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
                Toast.makeText(this, "Closing", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Error closing connection: " + e.getMessage());
            }*/
        }
    }

    public String getCurrentConnectedDeviceMacAddress() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Bluetooth is not available or not enabled
            return null;
        }

        // Get a set of currently connected devices
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return TODO;
        }
        Set<BluetoothDevice> connectedDevices = bluetoothAdapter.getBondedDevices();

        // Check if there are connected devices
        if (!connectedDevices.isEmpty()) {
            // Get the first connected device from the set
            BluetoothDevice connectedDevice = connectedDevices.iterator().next();

            // Return the MAC address of the connected device
            return connectedDevice.getAddress();
        }

        // No connected devices found
        return null;
    }

}
