package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import static com.google.android.gms.wearable.DataMap.TAG;

import static java.lang.Math.round;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.content.Intent;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;



public class MainActivity extends AppCompatActivity {
    private SensorManager mSensorManager;
    private Sensor mHeartSensor;
    private Sensor offBodySensor;
    private TextView mTextView;
    private TextView mTextViewSpo2;
    private float sensorValue;

    private DatabaseReference myRef;
    private DatabaseReference myRef2;


    private static final int REQUEST_CODE_PERMISSION = 100;
    private static final String TODO = "";
    private static final String TAG = "SmartwatchActivity";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static String DEVICE_ADDRESS = "00:00:00:00:00:00"; // Replace with your mobile device's Bluetooth MAC address
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private FirebaseFirestore db;



    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);

        startFallDetectionService();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mHeartSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mTextView = (TextView) findViewById(R.id.BPM_Value);
        mTextViewSpo2 = (TextView) findViewById(R.id.SPO2_Value);

        // Check for permissions and request if not granted
        checkAndRequestPermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        DEVICE_ADDRESS = getCurrentConnectedDeviceMacAddress();

        db = FirebaseFirestore.getInstance();



        FirebaseDatabase database = FirebaseDatabase.getInstance("https://database-ea0bd-default-rtdb.asia-southeast1.firebasedatabase.app");

        myRef = database.getReference("sensorValues/currentValue");
        myRef2 = database.getReference("sensorValues/spo2");


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
            if(sensorValue >= 40 && sensorValue <= 100) {
                mTextViewSpo2.setText("96%");
                updateSpo2ValueInDatabase("96%");

            }
            if(sensorValue >= 101 && sensorValue <= 109) {
                mTextViewSpo2.setText("95%");
                updateSpo2ValueInDatabase("95%");
            }
            if(sensorValue >= 131 ) {
                mTextViewSpo2.setText("93%");
                updateSpo2ValueInDatabase("93%");
            }

            //condition
            if (sensorValue >= 120) {

                // Calculate acceleration based on sensor data
                float acceleration = calculateAcceleration(event1);

                // Fall detection logic (example: check for sudden change in acceleration)
                if (isFallDetected(acceleration)) {
                    // Perform actions when fall is detected
                    showSeizureAndFallAlert();
                    sendData("1");
                }
                else{
                    sendData("1");
                    showSeizure();
                }
            }

            if (sensorValue >= 105 && sensorValue <= 115) {

                showHeartRateWarning();
                storeSensorValueInFirestore(sensorValue);

            }

        }
    };

        private void showSeizureAndFallAlert() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("          Seizure and Fall Alert")
                    .setMessage("A seizure and fall are detected! Please dismiss this alert.")
                    .setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Handle dismissal, if needed
                            dialog.dismiss();
                        }
                    })
                    .setCancelable(false) // This prevents the user from dismissing the alert by tapping outside of it
                    .show();
        }
        private void showSeizure() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("          Seizure")
                    .setMessage("A seizure is detected! Please dismiss this alert.")
                    .setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Handle dismissal, if needed
                            dialog.dismiss();
                        }
                    })
                    .setCancelable(false) // This prevents the user from dismissing the alert by tapping outside of it
                    .show();
        }

        private void  showHeartRateWarning() {


            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("          Warning")
                    .setMessage("Warning: Your heart rate is high. Please sit down or look for a safe place.")
                    .setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Handle dismissal, if needed
                            dialog.dismiss();
                        }
                    })
                    .setCancelable(false) // This prevents the user from dismissing the alert by tapping outside of it
                    .show();
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
                            Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());

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







    private float calculateAcceleration(SensorEvent event) {
        // Calculate acceleration based on sensor data
        // Replace this with your own calculation based on accelerometer values
        return 60.0f;
    }

    private boolean isFallDetected(float acceleration) {
        // Implement your fall detection logic here
        // This is a placeholder, replace it with your own fall detection algorithm
        return acceleration > 10.0f;
    }




}








