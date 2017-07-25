package com.example.bio.basictf;

import android.graphics.Color;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.content.*;
import android.os.IBinder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.os.Handler;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
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

    // Tensorflow dependencies and Configuration
    private static final int num_channel = 6;
    private static final int num_data    = 150;
    private static final int[] INPUT_SIZE2 = {1,num_data*num_channel};

    // Application View
    private ToggleButton toggle_m;
    private ToggleButton toggle_mode;
    private Chronometer crono;

    // Mbientlab dependencies
    private BtleService.LocalBinder serviceBinder;
    private SensorFusionBosch sensorFusion;
    private Led led;
    private final String MW_MAC_ADDRESS= "C5:AD:BC:E0:F1:50";
    //private final String MW_MAC_ADDRESS= "F5:30:7F:69:BC:46";
    private MetaWearBoard board;

    // Variables that are used during the program
    private String name_file = "mbientlab.csv";
    private ArrayList<Float> input_Accx  = new ArrayList<>(num_data);
    private ArrayList<Float> input_Accy  = new ArrayList<>(num_data);
    private ArrayList<Float> input_Accz  = new ArrayList<>(num_data);
    private ArrayList<Float> input_Gyrx  = new ArrayList<>(num_data);
    private ArrayList<Float> input_Gyry  = new ArrayList<>(num_data);
    private ArrayList<Float> input_Gyrz  = new ArrayList<>(num_data);

    private static float[] relations = new float[num_data*num_channel];
    final float relations_val[] = new float[num_data*num_channel];
    private File file;
    private FileOutputStream stream;

    // Values for segmentation
    private int sensing_time = 500; // data in milliseconds
    private int wait_time_before = 500; // data in milliseconds
    private int wait_time_after  = 1000; // data in milliseconds

    // Functions used
    private final Handler handler = new Handler();

    public void retrieveBoard(final String macAddr) {
        Toast.makeText(MainActivity.this, "Connection Initialized",Toast.LENGTH_SHORT).show();
        final BluetoothManager btManager=(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(macAddr);
        // Create a MetaWear board object for the Bluetooth Device
        board= serviceBinder.getMetaWearBoard(remoteDevice);
        board.connectAsync().onSuccessTask(new Continuation<Void, Task<Route>>() {
            @Override
            public Task<Route> then(Task<Void> task) throws Exception {
                Log.i("Tensorflow","Connection to "+macAddr);
                sensorFusion = board.getModule(SensorFusionBosch.class);
                sensorFusion.configure()
                        .mode(Mode.NDOF) // Acc and Gyro 100 hz
                        .accRange(AccRange.AR_4G)
                        .gyroRange(GyroRange.GR_250DPS)
                        .commit();
                sensorFusion.correctedAcceleration().addRouteAsync(new RouteBuilder() {
                    @Override
                    public void configure(RouteComponent source) {
                        source.stream(new Subscriber() {
                            @Override
                            public void apply(Data data, Object... env) {
                                //Log.i("Tensorflow", "Acc " + data.value(CorrectedAcceleration.class));
                                input_Accx.add(data.value(CorrectedAcceleration.class).x());
                                input_Accy.add(data.value(CorrectedAcceleration.class).y());
                                input_Accz.add(data.value(CorrectedAcceleration.class).z());
                                if(input_Accx.size()>num_data){
                                    input_Accx.remove(0);
                                    input_Accy.remove(0);
                                    input_Accz.remove(0);
                                }
                                //Log.i("Tensorflow","Size is " + input_Accx.size() + "first values is" + input_Accx.get(1));
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
                                //Log.i("Tensorflow","Gyro " + data.value(CorrectedAngularVelocity.class));
                                input_Gyrx.add(data.value(CorrectedAngularVelocity.class).x());
                                input_Gyry.add(data.value(CorrectedAngularVelocity.class).y());
                                input_Gyrz.add(data.value(CorrectedAngularVelocity.class).z());
                                if(input_Gyrx.size()>num_data){
                                    input_Gyrx.remove(0);
                                    input_Gyry.remove(0);
                                    input_Gyrz.remove(0);
                                }
                                //Log.i("Tensorflow","Size is " + input_Gyrz.size() + "first values is" + input_Gyrz.get(1));
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

    public void show_results(float[] inference){
        final TextView textViewR2 = (TextView) findViewById(R.id.txtViewResult2);
        String newline = System.lineSeparator();
        String newsep = "       ";
        String mostrar =  " Hor : " + String.valueOf(inference[0]) + newsep
                        + " Ver : " + String.valueOf(inference[1]) + newline
                        + " Phor: " + String.valueOf(inference[2]) + newsep
                        + " PVer: " + String.valueOf(inference[3]) + newline
                        + " V_S : " + String.valueOf(inference[4]) + newsep
                        + " X_S : " + String.valueOf(inference[5]) + newline
                        + " C_V : " + String.valueOf(inference[6]) + newsep
                        + " C_H : " + String.valueOf(inference[7]);
        textViewR2.setText(mostrar);

        final TextView textView_soft = (TextView) findViewById(R.id.txtViewResult);
        int index = 0;
        String classes[] = {"Mov Horizontal","Mov Vertical",
                            "Poke Horizontal","Poke Vertical",
                            "V Shape","X Shape",
                            "Circle Vertical","Circle Horizontal"};
        for(int j=0 ; j<inference.length-1 ; j++){
            if (inference[j] > inference[index]){
                index = j;
            }
        }
        textView_soft.setText(classes[index]);
    }

    private void InitializeVariables(){
        try{
            file = new File(Environment.getExternalStorageDirectory(), name_file);
            stream = new FileOutputStream(file);
        }catch(Exception e){
            e.printStackTrace();
        }

        for (int i = 0; i < relations.length; i++) {
            relations[i] = (float) Math.random();
            if (i%2 ==0){relations[i] = (float) Math.random()*11;}
        }
        for (int i = 0; i < relations_val.length; i++) {
            relations_val[i] = (float) Math.random();
            if (i%2 ==0){relations_val[i] = (float) Math.random()*11;}
        }

        for(int i = 0; i < num_data; i++) input_Accx.add((float) (i*0.04));
        for(int i = 0; i < num_data; i++) input_Accy.add((float) (i*0.04));
        for(int i = 0; i < num_data; i++) input_Accz.add((float) (i*0.04));
        for(int i = 0; i < num_data; i++) input_Gyrx.add((float) (i*0.04));
        for(int i = 0; i < num_data; i++) input_Gyry.add((float) (i*0.04));
        for(int i = 0; i < num_data; i++) input_Gyrz.add((float) (i*0.04));

        for(int i=0;i<input_Accx.size();i++){
            relations_val[i+0*input_Accx.size()] = input_Accx.get(i);
            relations_val[i+1*input_Accx.size()] = input_Accy.get(i);
            relations_val[i+2*input_Accx.size()] = input_Accz.get(i);
            relations_val[i+3*input_Accx.size()] = input_Gyrx.get(i);
            relations_val[i+4*input_Accx.size()] = input_Gyry.get(i);
            relations_val[i+5*input_Accx.size()] = input_Gyrz.get(i);
        }
    }

    private void write(final String data) {
        try {
            stream.write(data.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        crono = (Chronometer) findViewById(R.id.lbl_crono);
        toggle_m = (ToggleButton) findViewById(R.id.btn_toogle);
        toggle_mode = (ToggleButton) findViewById(R.id.btn_mode);
        final Model_Functions model = new Model_Functions();
        final Model_Tensorflow tensorflow = new Model_Tensorflow();
        tensorflow.Load_Tensorflow(this.getApplicationContext());

        InitializeVariables();
        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.i("Tensorflow", "Button Start");
                sensorFusion.correctedAcceleration().start();
                sensorFusion.correctedAngularVelocity().start();
                sensorFusion.start();
                led.play();
                crono.setBase(SystemClock.elapsedRealtime());
                crono.start();

                new Thread(){
                    public void run(){
                        if(toggle_mode.isChecked()){
                            Log.i("Tensorflow","Activated Online mode");
                            for(int j=0;j<20;j++){
                                final int value = j;
                                try{
                                    sleep(sensing_time);
                                    float averageAcc = model.calculateAverage(input_Accx,input_Accy,input_Accz,1,num_data,sensing_time);
                                    float averageGyr = model.calculateAverage(input_Gyrx,input_Gyry,input_Gyrz,2,num_data,sensing_time);
                                    Log.i("Tensorflow","Average Acc = " + averageAcc);
                                    Log.i("Tensorflow","Average Gyr = " + averageGyr);
                                    if(averageAcc > 0.1f && averageGyr > 0.1f){
                                        Log.i("Tensorflow", "Action" + String.valueOf(j));
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                toggle_m.setChecked(true);
                                                toggle_m.setBackgroundColor(Color.BLUE);
                                            }
                                        });
                                        sleep(wait_time_after);
                                        input_Accx = model.calculateNormalize(input_Accx,1);
                                        input_Accy = model.calculateNormalize(input_Accy,1);
                                        input_Accz = model.calculateNormalize(input_Accz,1);
                                        input_Gyrx = model.calculateNormalize(input_Gyrx,2);
                                        input_Gyry = model.calculateNormalize(input_Gyry,2);
                                        input_Gyrz = model.calculateNormalize(input_Gyrz,2);
                                        final float relations_val[] = new float[num_data*num_channel];
                                        for(int i=0;i<input_Accx.size();i++){
                                            relations_val[i+0*input_Accx.size()] = input_Accx.get(i);
                                            relations_val[i+1*input_Accy.size()] = input_Accy.get(i);
                                            relations_val[i+2*input_Accz.size()] = input_Accz.get(i);
                                            relations_val[i+3*input_Gyrx.size()] = input_Gyrx.get(i);
                                            relations_val[i+4*input_Gyry.size()] = input_Gyry.get(i);
                                            relations_val[i+5*input_Gyrz.size()] = input_Gyrz.get(i);
                                        }
                                        //Log.i("Tensorflow", "Inference init = " + SystemClock.currentThreadTimeMillis()) ;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {show_results(tensorflow.TensorflowModel(relations_val));}
                                        });
                                        sleep(wait_time_before-sensing_time);
                                    }else{
                                        Log.i("Tensorflow", "No Action" + String.valueOf(j));
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                toggle_m.setChecked(false);
                                                toggle_m.setBackgroundColor(Color.DKGRAY);
                                            }
                                        });
                                    }
                                }catch(InterruptedException e){
                                    e.printStackTrace();
                                }
                            }
                        }else{
                            Log.i("Tensorflow", "Loggin mode");
                            try {
                                for(int j=0;j<20;j++) {
                                    Log.i("Tensorflow", "Loggin mode inside " + j);
                                    sleep(1500);
                                    for (int i = 0; i < input_Accx.size(); i++) {
                                        relations_val[i + 0 * input_Accx.size()] = input_Accx.get(i);
                                        relations_val[i + 1 * input_Accy.size()] = input_Accy.get(i);
                                        relations_val[i + 2 * input_Accz.size()] = input_Accz.get(i);
                                        relations_val[i + 3 * input_Gyrx.size()] = input_Gyrx.get(i);
                                        relations_val[i + 4 * input_Gyry.size()] = input_Gyry.get(i);
                                        relations_val[i + 5 * input_Gyrz.size()] = input_Gyrz.get(i);
                                    }
                                    String data_values = " ";
                                    for(int k=0; k< relations_val.length; k++){
                                        data_values = data_values + String.valueOf(relations_val[k]) + ",";
                                    }
                                    data_values = data_values + "\n";
                                    write(data_values);
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }.start();
            }
        });

        findViewById(R.id.btn_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Tensorflow","Button Stop");
                crono.stop();
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                led.stop(true);
                sensorFusion.stop();
                sensorFusion.correctedAngularVelocity().stop();
                sensorFusion.correctedAcceleration().stop();
            }
        });

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("Tensorflow","Button Reset");
                board.tearDown();
            }
        });

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // this is simulated data
                show_results(tensorflow.TensorflowModel(relations));
            }
        });

        findViewById(R.id.btn_Sensor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // this is real data
                show_results(tensorflow.TensorflowModel(relations_val));
            }
        });

        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                System.exit(0);
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


