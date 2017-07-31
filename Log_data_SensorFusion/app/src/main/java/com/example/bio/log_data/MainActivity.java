package com.example.bio.log_data;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.Toast;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.module.Settings;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;
import com.mbientlab.metawear.module.SensorFusionBosch.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private Chronometer chrono;
    // Mbientlab dependencies
    private BtleService.LocalBinder serviceBinder;
    private SensorFusionBosch sensorFusion;
    private Led led;
    private final String MW_MAC_ADDRESS= "C5:AD:BC:E0:F1:50";
    //private final String MW_MAC_ADDRESS= "F5:30:7F:69:BC:46";
    private MetaWearBoard board;

    // Variables that are used during the program
    private String name_file = "mbientlab.csv";
    private ArrayList<String> input_Accx  = new ArrayList<>();
    private ArrayList<String> input_Accy  = new ArrayList<>();
    private ArrayList<String> input_Accz  = new ArrayList<>();
    private ArrayList<String> input_Gyrx  = new ArrayList<>();
    private ArrayList<String> input_Gyry  = new ArrayList<>();
    private ArrayList<String> input_Gyrz  = new ArrayList<>();

    private File file;
    private FileOutputStream stream;
    private final Handler handler = new Handler();

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public void retrieveBoard(final String macAddr) {
        Toast.makeText(MainActivity.this, "Connection Initialized",Toast.LENGTH_SHORT).show();
        final BluetoothManager btManager=(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(macAddr);
        // Create a MetaWear board object for the Bluetooth Device
        board = serviceBinder.getMetaWearBoard(remoteDevice);

        board.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {
                Log.i("Tensorflow","Connection to "+macAddr);
                sensorFusion = board.getModule(SensorFusionBosch.class);
                sensorFusion.configure()
                        .mode(Mode.IMU_PLUS) // Acc and Gyro 100 hz
                        .accRange(AccRange.AR_4G)
                        .gyroRange(GyroRange.GR_250DPS)
                        .commit();
                sensorFusion.correctedAcceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                input_Accx.add(String.valueOf(data.value(CorrectedAcceleration.class).x()));
                                input_Accy.add(String.valueOf(data.value(CorrectedAcceleration.class).y()));
                                input_Accz.add(String.valueOf(data.value(CorrectedAcceleration.class).z()));
                            }
                        });
                    }
                });
                sensorFusion.correctedAngularVelocity().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                input_Gyrx.add(String.valueOf(data.value(CorrectedAngularVelocity.class).x()));
                                input_Gyry.add(String.valueOf(data.value(CorrectedAngularVelocity.class).y()));
                                input_Gyrz.add(String.valueOf(data.value(CorrectedAngularVelocity.class).z()));
                            }
                        });
                    }
                });
                led = board.getModule(Led.class);
                led.editPattern(Led.Color.BLUE,Led.PatternPreset.SOLID)
                        .repeatCount(Led.PATTERN_REPEAT_INDEFINITELY)
                        .commit();
                return null;
            }
        }).continueWith(new Continuation<Route, Void>() {
            @Override
            public Void then(Task<Route> task) throws Exception {
                if (task.isFaulted()) {
                    Log.e("Tensorflow", board.isConnected() ? "Error setting up route" : "Error connecting", task.getError());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Connection Fail",Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Log.i("Tensorflow", "Connected");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Connection OK",Toast.LENGTH_SHORT).show();
                        }

                    });
                }
                return null;
            }
        });
    }

    private void InitializeVariables() {
        try {
            file = new File(Environment.getExternalStorageDirectory(), name_file);
            stream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void write(String accX, String accY, String accZ, String gyrX, String gyrY, String gyrZ) {
        String line_space = "\n";
        try {
            stream.write(accX.getBytes());stream.write(line_space.getBytes());
            stream.write(accY.getBytes());stream.write(line_space.getBytes());
            stream.write(accZ.getBytes());stream.write(line_space.getBytes());
            stream.write(gyrX.getBytes());stream.write(line_space.getBytes());
            stream.write(gyrY.getBytes());stream.write(line_space.getBytes());
            stream.write(gyrZ.getBytes());stream.write(line_space.getBytes());
            Log.i("Tensorflow","Stream saving data");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getApplicationContext().bindService(new Intent(MainActivity.this, BtleService.class),MainActivity.this, Context.BIND_AUTO_CREATE);
        verifyStoragePermissions(MainActivity.this);
        chrono = (Chronometer) findViewById(R.id.lbl_chronometer);

        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InitializeVariables();
                Log.i("Tensorflow", "Button Start");
                sensorFusion.correctedAcceleration().start();
                sensorFusion.correctedAngularVelocity().start();
                sensorFusion.start();
                led.play();
                chrono.setBase(SystemClock.elapsedRealtime());
                chrono.start();
            }
        });

        findViewById(R.id.btn_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Tensorflow","Button Stop");
                chrono.stop();
                led.stop(true);
                sensorFusion.stop();
                sensorFusion.correctedAngularVelocity().stop();
                sensorFusion.correctedAcceleration().stop();

                Log.i("Tensorflow", "Data size Acc X "+ input_Accx.size());
                Log.i("Tensorflow", "Data size Acc Y "+ input_Accy.size());
                Log.i("Tensorflow", "Data size Acc Z "+ input_Accz.size());
                Log.i("Tensorflow", "Data size Gyr X "+ input_Gyrx.size());
                Log.i("Tensorflow", "Data size Gyr Y "+ input_Gyry.size());
                Log.i("Tensorflow", "Data size Gyr Z "+ input_Gyrz.size());

                write(input_Accx.toString(), input_Accy.toString(), input_Accz.toString(),
                        input_Gyrx.toString(), input_Gyry.toString(),input_Gyrz.toString());

                input_Accx.clear();
                input_Accy.clear();
                input_Accz.clear();
                input_Gyrx.clear();
                input_Gyry.clear();
                input_Gyrz.clear();

                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Tensorflow","Button Reset");
                board.tearDown();
            }
        });

        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        getApplicationContext().unbindService(this);

    }
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        // Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        Log.i("Tensorflow","Service calling complete");
        retrieveBoard(MW_MAC_ADDRESS);
    }
    @Override
    public void onServiceDisconnected(ComponentName componentName) {
    }
}
