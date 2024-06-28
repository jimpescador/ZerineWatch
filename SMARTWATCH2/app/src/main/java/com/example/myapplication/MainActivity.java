package com.example.myapplication;

import static androidx.core.location.LocationManagerCompat.getCurrentLocation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.Manifest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.annotation.SuppressLint;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.widget.TextView;

import static java.lang.Math.round;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import android.content.Intent;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;


public class MainActivity extends AppCompatActivity {
    private SensorManager mSensorManager;
    private Sensor mHeartSensor;
    private Sensor offBodySensor;
    private TextView mTextView;

    private TextView mTextViewSpo2;
    private float sensorValue;

    private DatabaseReference myRef;
    private DatabaseReference myRef2;

    private MediaPlayer mediaPlayer;

    private MediaPlayer lowplayer;
    private MediaPlayer alertplayer;


    private static final int REQUEST_CODE_PERMISSION = 100;
    private static final String TODO = "";
    private static final String TAG = "SmartwatchActivity";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static String DEVICE_ADDRESS = "00:00:00:00:00:00"; // Replace with your mobile device's Bluetooth MAC address
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private FirebaseFirestore db;

    private Map<String, List<Float>> dailyBPMMap = new HashMap<>();
    private FirebaseFirestore firestore;
    private CollectionReference collectionRef;

    private boolean isSeizureAndFallAlertShown = false;

    private boolean isSeizureAlertShown = false;

    private boolean isWarningShown = false;
    private AlertDialog seizureAndFallAlertDialog;
    private AlertDialog seizureAlertDialog;
    private AlertDialog heartRateAlertDialog;
    private Vibrator vibrator;

    private static final long RESET_TIME_INTERVAL = 180000; // 3 minutes in milliseconds

    private static final long RESET_TIME_INTERVAL2 = 200000; // 3 minutes in milliseconds
    private long lastWarningTime = 0; // Timestamp of the last warning

    private long lastWarningTime2 = 0; // Timestamp of the last warning

    private Handler handler = new Handler();
    private PowerManager.WakeLock wakeLock;
    private SensorManager sensorManager;

    private static final int PERMISSION_REQUEST_LOCATION = 101;

    private float highestBPM;

    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;


   private BroadcastReceiver fallDetectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // A alert dialog here
            showAlert();
        }
    };





    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);

        Intent serviceIntent = new Intent(this, FallDetectionService.class);
        startService(serviceIntent);

        Intent serviceIntentSensor= new Intent(this, SensorForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntentSensor);


        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeartSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mTextView = (TextView) findViewById(R.id.BPM_Value);
        mTextViewSpo2 = (TextView) findViewById(R.id.SPO2_Value);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mediaPlayer = MediaPlayer.create(this, R.raw.sensor_alert);
        lowplayer = MediaPlayer.create(this, R.raw.low_alert);
        alertplayer = MediaPlayer.create(this, R.raw.seizure_detected_remain_calm);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakeLockTag");
        wakeLock.acquire();

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE)) {
            // Heart rate sensor is available
            mHeartSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (mHeartSensor == null) {
                // Handle case when heart rate sensor is not available
                Toast.makeText(this, "Heart rate sensor not available", Toast.LENGTH_SHORT).show();
            } else {
                // Register the sensor listener
                mSensorManager.registerListener(mSensorEventListener, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            // Heart rate sensor is not available on this device
            Toast.makeText(this, "Heart rate sensor not supported on this device", Toast.LENGTH_SHORT).show();
        }



        // Check for permissions and request if not granted
        checkAndRequestPermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        DEVICE_ADDRESS = getCurrentConnectedDeviceMacAddress();

        db = FirebaseFirestore.getInstance();


        FirebaseDatabase database = FirebaseDatabase.getInstance("https://database-ea0bd-default-rtdb.asia-southeast1.firebasedatabase.app");

        myRef = database.getReference("sensorValues/currentValue");
        myRef2 = database.getReference("sensorValues/spo2");
        firestore = FirebaseFirestore.getInstance();
        collectionRef = firestore.collection("BPM");

        getWarningValue();

    }

    public void getWarningValue(){

        db.collection("TriggerValues")
                .document("sharedTriggerValues")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Retrieve minTriggerValue
                            int minTriggerValue = document.getLong("Warning").intValue();;

                            // Storing data into SharedPreferences
                            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref",MODE_PRIVATE);
                            SharedPreferences.Editor sharedPref = sharedPreferences.edit();
                            sharedPref.putInt("warning", minTriggerValue);
                            sharedPref.commit();

                        } else {
                            Log.d(TAG, "No such document");
                        }
                    } else {
                        Log.d(TAG, "get failed with ", task.getException());
                    }
                });

    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("FALL_DETECTED");
        LocalBroadcastManager.getInstance(this).registerReceiver(fallDetectionReceiver, filter);

    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(fallDetectionReceiver);
    }

    private void showAlert() {
        // Write fall detection data to Firestore
        sendData("2");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference fallDetectionRef = db.collection("fallDetection");

        Map<String, Object> data = new HashMap<>();
        data.put("fallDetected", true);
        data.put("timestamp", FieldValue.serverTimestamp());

        // Add a new document with a random document ID
        fallDetectionRef
                .add(data)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "Fall detected data written to Firestore with ID: " + documentReference.getId());

                        // Show alert dialog after writing to Firestore
                        showAlertDialog();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing fall detected data to Firestore", e);

                        // Show alert dialog even if Firestore write fails
                        showAlertDialog();
                    }
                });
    }

    private void showAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Fall Detected")
                .setMessage("A fall has been detected!")
                .setPositiveButton("OK", null)
                .show();
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

    private void checkAndRequestPermissions() {
        String[] requiredPermissions = new String[]{android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN
                , android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_ADVERTISE, android.Manifest.permission.BLUETOOTH_CONNECT
                , android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.WAKE_LOCK
                , android.Manifest.permission.BODY_SENSORS};
        boolean allPermissionsGranted = true;

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSION) {
            // Check if all permissions are granted
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                //Toast.makeText(this, "All permissions are granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "All permissions are required for this app.", Toast.LENGTH_SHORT).show();
                this.finish();
                System.exit(0);
            }
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }

                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.e(TAG, "Bluetooth Name and Mac Address: " + deviceName + " : " + deviceHardwareAddress);
            }
        }
    };

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


    private void startFallDetectionService() {
        Intent serviceIntent = new Intent(this, FallDetectionService.class);
        startService(serviceIntent);
    }

    private void date() {

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

    private void updateSpo2ValueInDatabase(String spo2Value) {
        // Write the SPO2 value to the Firebase Realtime Database
        myRef2.setValue(spo2Value)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "SPO2 value set successfully");
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Failed to set SPO2 value. Error: " + e.getLocalizedMessage());
                            }
                        });
                    }
                });
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {




            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {


                FallDetectionService detect = new FallDetectionService();
                final SensorEvent event1 = event;
                mTextView = findViewById(R.id.BPM_Value);
                final float sensorValue = event1.values[0];
                addSensorValueToDailyMap(sensorValue);





                myRef.setValue(sensorValue).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Value set successfully");
                            }
                        });
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Failed to set value. Error: " + e.getLocalizedMessage());
                            }
                        });
                    }
                });





                mTextView.setText(Float.toString(sensorValue));
                if (sensorValue >= 40 && sensorValue <= 100) {
                    mTextViewSpo2.setText("96%");
                    updateSpo2ValueInDatabase("96%");
                }
                if (sensorValue >= 101 && sensorValue <= 109) {
                    mTextViewSpo2.setText("95%");
                    updateSpo2ValueInDatabase("95%");
                }
                if (sensorValue >= 131) {
                    mTextViewSpo2.setText("93%");
                    updateSpo2ValueInDatabase("93%");
                }

                fetchTriggerValuesAndHandleAlerts();

                //condition & fetch data for alert trigger

                /*db.collection("TriggerValues")
                        .document("sharedTriggerValues")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if (document.exists()) {
                                    // Retrieve minTriggerValue
                                    int Alert = document.getLong("Alert").intValue();
                                    int lowAlert =document.getLong("Alert_Low").intValue();

                                    // Now you can use minTriggerValue in your sensor check

                                    if ((sensorValue >= Alert && !isSeizureAlertShown) || (sensorValue <= lowAlert && !isSeizureAlertShown)) {
                                        sendData("1");
                                        // Show seizure alert
                                        showSeizure();
                                        isSeizureAlertShown = true;

                                        lastWarningTime2 = System.currentTimeMillis();
                                        Map<String, Object> seizureData = new HashMap<>();
                                        seizureData.put("timestamp", FieldValue.serverTimestamp());
                                        seizureData.put("highestBPM", sensorValue);

                                        // Add the document to Firestore
                                        db.collection("SeizureRecords")
                                                .add(seizureData)
                                                .addOnSuccessListener(documentReference -> {
                                                    Log.d(TAG, "Seizure record added with ID: " + documentReference.getId());
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.w(TAG, "Error adding seizure record", e);
                                                });
                                        // Add the document to Firestore




                                        highestBPM = Alert;

                                        if (sensorValue > highestBPM) {
                                            highestBPM = sensorValue;
                                        }


                                    }

                                    if (System.currentTimeMillis() - lastWarningTime2 >= RESET_TIME_INTERVAL2) {
                                        isSeizureAlertShown = false; // Reset the flag if 10 minutes have passed since the last warning
                                    }


                                } else {
                                    Log.d(TAG, "No such document");

                                }
                            } else {
                                Log.d(TAG, "get failed with ", task.getException());
                            }
                        });


                //get warning trigger values

                db.collection("TriggerValues")
                        .document("sharedTriggerValues")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if (document.exists()) {
                                    // Retrieve minTriggerValue
                                    int minTriggerValue = document.getLong("Warning").intValue();
                                    int lowTriggerValue = document.getLong("Warning_Low").intValue();

                                    // Now you can use minTriggerValue in your sensor check

                                    if (sensorValue >= minTriggerValue && !isWarningShown) {
                                        showHeartRateWarning();
                                        storeSensorValueInFirestore(sensorValue);
                                        isWarningShown = true; // Set the flag to true to indicate that the warning has been shown
                                        lastWarningTime = System.currentTimeMillis(); // Record the current time
                                    }

                                    if (sensorValue <= lowTriggerValue && !isWarningShown) {
                                        showLowHeartRateWarning();
                                        storeSensorValueInFirestore(sensorValue);
                                        isWarningShown = true; // Set the flag to true to indicate that the warning has been shown
                                        lastWarningTime = System.currentTimeMillis(); // Record the current time
                                    }

// Check if it's time to reset the flag
                                    if (System.currentTimeMillis() - lastWarningTime >= RESET_TIME_INTERVAL) {
                                        isWarningShown = false; // Reset the flag if 10 minutes have passed since the last warning
                                    }

                                } else {
                                    Log.d(TAG, "No such document");
                                }
                            } else {
                                Log.d(TAG, "get failed with ", task.getException());
                            }
                        });*/

            }
        };



    private void fetchTriggerValuesAndHandleAlerts() {
        db.collection("TriggerValues")
                .document("sharedTriggerValues")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            int alertValue = document.getLong("Alert").intValue();
                            int alertLowValue = document.getLong("Alert_Low").intValue();
                            int warningValue = document.getLong("Warning").intValue();
                            int warningLowValue = document.getLong("Warning_Low").intValue();

                            handleSeizureAlert(alertValue, alertLowValue);
                            handleWarningAlert(warningValue, warningLowValue);
                        } else {
                            Log.d(TAG, "No such document");
                        }
                    } else {
                        Log.d(TAG, "get failed with ", task.getException());
                    }
                });
    }

    // Function to handle seizure alerts
    private void handleSeizureAlert(int alertValue, int alertLowValue) {
        if ((sensorValue >= alertValue || sensorValue >= alertLowValue) && !isSeizureAlertShown) {
            sendData("1");
            showSeizure();
            isSeizureAlertShown = true;
            lastWarningTime2 = System.currentTimeMillis();

            Map<String, Object> seizureData = new HashMap<>();
            seizureData.put("timestamp", FieldValue.serverTimestamp());
            seizureData.put("highestBPM", sensorValue);

            db.collection("SeizureRecords")
                    .add(seizureData)
                    .addOnSuccessListener(documentReference -> Log.d(TAG, "Seizure record added with ID: " + documentReference.getId()))
                    .addOnFailureListener(e -> Log.w(TAG, "Error adding seizure record", e));

            highestBPM = Math.max(highestBPM, sensorValue);
        }

        if (System.currentTimeMillis() - lastWarningTime2 >= RESET_TIME_INTERVAL2) {
            isSeizureAlertShown = false;
        }
    }

    // Function to handle warning alerts
    private void handleWarningAlert(int warningValue, int warningLowValue) {
        if (sensorValue >= warningValue  && !isWarningShown) {
            showHeartRateWarning();
            storeSensorValueInFirestore(sensorValue);
            isWarningShown = true;
            lastWarningTime = System.currentTimeMillis();
        } else if (sensorValue <= warningLowValue && !isWarningShown) {
            showLowHeartRateWarning();
            storeSensorValueInFirestore(sensorValue);
            isWarningShown = true;
            lastWarningTime = System.currentTimeMillis();
        }

        if (System.currentTimeMillis() - lastWarningTime >= RESET_TIME_INTERVAL) {
            isWarningShown = false;
        }
    }


        private void showSeizureAndFallAlert() {
            if (!isSeizureAndFallAlertShown && MainActivity.this != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("                  Seizure and Fall Alert");
                builder.setMessage("A possible seizure and fall are detected!");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // Dismiss the dialog when OK is clicked
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                // Set the flag to true to indicate that the warning has been shown
                isSeizureAndFallAlertShown = true;

                // Schedule a handler to dismiss the dialog after 10 seconds
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (alertDialog.isShowing()) {
                            alertDialog.dismiss();
                        }
                    }
                }, 15000); // 10 seconds in milliseconds

            }
        }

        private void showSeizure() {
            if (!isSeizureAlertShown && MainActivity.this != null){
                playMusic3();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("                    Seizure");
                builder.setMessage("A possible seizure is detected! Remain calm, Take a rest\n");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss(); // Dismiss the dialog when OK is clicked
                        if (alertplayer != null)
                        {
                            alertplayer.release();
                            alertplayer = null;
                        }

                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                // Set the flag to true to indicate that the warning has been shown
                isSeizureAlertShown = true;

                // Schedule a handler to dismiss the dialog after 10 seconds
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (alertDialog.isShowing()) {
                            alertDialog.dismiss();
                        }
                    }
                }, 60000); // 60 seconds in milliseconds

        }
            long[] pattern = {0, 200, 200, 200, 200, 200, 200, 200};
            if (vibrator != null) {
                vibrator.vibrate(pattern, -1); // 200 milliseconds vibration duration
            }
        }


    private void showHeartRateWarning() {
        if (!isWarningShown && MainActivity.this != null) {
            playMusic();
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
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

                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            // Set the flag to true to indicate that the warning has been shown
            isWarningShown = true;

            // Schedule a handler to dismiss the dialog after 10 seconds
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (alertDialog.isShowing()) {
                        alertDialog.dismiss();
                    }
                }
            }, 30000); // 10 seconds in milliseconds
        }

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
    private void playMusic2() {
        if (lowplayer != null) {
            lowplayer.setVolume(1.0f, 1.0f);
            lowplayer.start();
        }
    }

    private void playMusic3(){
            if (alertplayer != null) {
                float maxVolume = 15.0f;
                float volume = maxVolume / 15.0f; // Adjusted volume ratio

                alertplayer.setVolume(volume, volume);
                alertplayer.start();
            }
    }

    private void showLowHeartRateWarning() {
        if (!isWarningShown && MainActivity.this != null) {
            playMusic2();
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("                    WARNING");
            builder.setMessage("Your heart rate is low. Please seek for a safe place");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss(); // Dismiss the dialog when OK is clicked
                    if (lowplayer != null) {
                        lowplayer.release();
                        lowplayer = null;
                    }

                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            // Set the flag to true to indicate that the warning has been shown
            isWarningShown = true;

            // Schedule a handler to dismiss the dialog after 10 seconds
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (alertDialog.isShowing()) {
                        alertDialog.dismiss();
                    }
                }
            }, 30000); // 10 seconds in milliseconds
        }

        long[] pattern = {0, 400, 200, 400, 200, 400};
        if (vibrator != null) {
            vibrator.vibrate(pattern, -1); // 200 milliseconds vibration duration
        }
    }






    private void storeSensorValueInFirestore(float sensorValue) {
            // Create a new data object with sensorValue
            Map<String, Object> data = new HashMap<>();
            data.put("sensorValue", sensorValue);

            // Add data to Firestore collection (replace "sensorData" with your desired collection name)
            db.collection("sensorData")
                    .add(data)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            Log.d(TAG, "DocumentSnapshot" + documentReference.getId());

                            // Stop the execution after successful write
                            // This will prevent any further code from being executed after the write
                            return;
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(TAG, "Error adding document", e);
                            // Handle failure, if needed
                        }
                    });

            // The execution will continue here, but will be stopped if onSuccess is triggered.
            // Any code written here will not be executed after the successful write.
            // If you have additional code that needs to be executed after the write, you can place it within onSuccess.
        }

        /*private float calculateAcceleration(SensorEvent event) {
            // Calculate acceleration based on sensor data
            // Replace this with your own calculation based on accelerometer values
            return 30.0f;
        }

        private boolean isFallDetected(float acceleration) {
            // Implement your fall detection logic here
            // This is a placeholder, replace it with your own fall detection algorithm
            return acceleration > 1.0f;
        }*/

    private void addSensorValueToDailyMap(float sensorValue) {
        // Get current date
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(cal.getTime());

        // Add sensor value to daily BPM map
        if (dailyBPMMap.containsKey(currentDate)) {
            dailyBPMMap.get(currentDate).add(sensorValue);
        } else {
            List<Float> bpmList = new ArrayList<>();
            bpmList.add(sensorValue);
            dailyBPMMap.put(currentDate, bpmList);
        }
    }

    private void calculateAndStoreDailyAverageBPM() {
        // Calculate average BPM for each day and store in Firestore
        for (Map.Entry<String, List<Float>> entry : dailyBPMMap.entrySet()) {
            String date = entry.getKey();
            List<Float> bpmList = entry.getValue();

            float sum = 0;
            for (float bpm : bpmList) {
                sum += bpm;
            }
            final float avgBPM = sum / bpmList.size();

            // Round the average BPM to two decimal points
            final float roundedAvgBPM = Math.round(avgBPM * 100.0f) / 100.0f;


            // Create a timestamp for the day
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            String timestamp = sdf.format(new Date());

            // Update the Firestore document with the average BPM and timestamp
            Map<String, Object> sensorData = new HashMap<>();
            sensorData.put("AvgBPM", roundedAvgBPM);
            sensorData.put("Timestamp", timestamp);

            // Store the daily average BPM in Firestore
            collectionRef.add(sensorData)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            Log.d(TAG, "Daily average BPM added to Firestore with ID: " + documentReference.getId());
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(TAG, "Error adding daily average BPM to Firestore", e);
                        }
                    });
        }

    }

    private void getLocation() {
        Log.d(TAG, "Requesting location updates...");
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Update latitude and longitude when location changes
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                Log.d(TAG, "Location changed: Lat=" + latitude + " Lon=" + longitude);
                // Now you can use latitude and longitude to record the location to Firestore
                recordLocationToFirestore(latitude, longitude);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "Location provider status changed: Provider=" + provider + " Status=" + status);
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d(TAG, "Location provider enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.d(TAG, "Location provider disabled: " + provider);
            }
        };

        // Request location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        } else {
            Log.e(TAG, "Location permission not granted");
        }
    }

    // Function to record location data to Firestore
    private void recordLocationToFirestore(double latitude, double longitude) {
        Log.d(TAG, "Recording location to Firestore: Lat=" + latitude + " Lon=" + longitude);

        double formattedHighestBPM = Math.round(highestBPM * 10.0) / 10.0;
        Log.d(TAG, "Formatted highest BPM: " + formattedHighestBPM);

        // Create a new document in the "SeizureRec" collection
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", FieldValue.serverTimestamp()); // Server timestamp
        record.put("highest_bpm", formattedHighestBPM);
        record.put("latitude", latitude);
        record.put("longitude", longitude);

        // Add the document to Firestore
        db.collection("SeizureRec")
                .add(record)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Seizure record added with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding seizure record to Firestore", e);
                });
    }





    @Override
    protected void onDestroy() {
        super.onDestroy();
        calculateAndStoreDailyAverageBPM();
        Log.d(TAG, "AVGBPM success");
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Unregister sensor listener
        //sensorManager.unregisterListener(mSensorEventListener);

    }


}












