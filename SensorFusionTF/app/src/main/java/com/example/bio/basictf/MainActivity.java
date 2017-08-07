package com.example.bio.basictf;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Chronometer;
import android.widget.TextView;
import android.content.*;
import android.os.IBinder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.os.Handler;

import com.github.johnpersano.supertoasts.library.Style;
import com.github.johnpersano.supertoasts.library.SuperToast;
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
import java.util.Arrays;
import java.util.Date;

import bolts.Continuation;
import bolts.Task;
import com.github.johnpersano.supertoasts.library.SuperActivityToast;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    // Tensorflow dependencies and Configuration
    private static final int num_channel = 6;
    private static final int num_data    = 150;
    // Application View
    private ToggleButton toggle_m;
    private Chronometer crono;
    // Mbientlab dependencies
    private BtleService.LocalBinder serviceBinder;
    private SensorFusionBosch sensorFusion;
    private Led led;
    //private final String MW_MAC_ADDRESS= "C5:AD:BC:E0:F1:50";
    private final String MW_MAC_ADDRESS= "F5:30:7F:69:BC:46";
    private MetaWearBoard board;
    // Variables that are used during the program
    private String name_file = "mbientlab.csv";
    private ArrayList<Float> input_Accx  = new ArrayList<>();
    private ArrayList<Float> input_Accy  = new ArrayList<>();
    private ArrayList<Float> input_Accz  = new ArrayList<>();
    private ArrayList<Float> input_Gyrx  = new ArrayList<>();
    private ArrayList<Float> input_Gyry  = new ArrayList<>();
    private ArrayList<Float> input_Gyrz  = new ArrayList<>();
    private static float[] relations = new float[num_data*num_channel];
    final float[] relations_val = new float[num_data*num_channel];
    // Values for segmentation
    private int sensing_time = 500; // data in milliseconds
    private int wait_time_before = 500; // data in milliseconds
    private int wait_time_after  = 1000; // data in milliseconds
    // Functions used
    private File file;
    private FileOutputStream stream;
    private final Handler handler = new Handler();
    private Boolean running = true;
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
        board= serviceBinder.getMetaWearBoard(remoteDevice);
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
                                input_Accx.add(data.value(CorrectedAcceleration.class).x());
                                input_Accy.add((float) (data.value(CorrectedAcceleration.class).y() - 1.0));
                                input_Accz.add(data.value(CorrectedAcceleration.class).z());
                                if(input_Accx.size() > 100){
                                    input_Accx.remove(0);
                                    input_Accy.remove(0);
                                    input_Accz.remove(0);
                                }
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
                                input_Gyrx.add(data.value(CorrectedAngularVelocity.class).y());
                                input_Gyry.add(data.value(CorrectedAngularVelocity.class).x());
                                input_Gyrz.add(data.value(CorrectedAngularVelocity.class).z());
                                if(input_Gyrx.size() > 100){
                                    input_Gyrx.remove(0);
                                    input_Gyry.remove(0);
                                    input_Gyrz.remove(0);
                                }
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
        String mostrar =  " Poke H: " + String.valueOf(inference[2]) + newline
                        + " Poke V: " + String.valueOf(inference[3]) + newline
                        + " Shape V : " + String.valueOf(inference[4]) + newline
                        + " Shape X : " + String.valueOf(inference[5]) + newline
                        + " Circle C : " + String.valueOf(inference[6]) + newline
                        + " Circle CC : " + String.valueOf(inference[7]);
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
        for (int i = 0; i < relations.length; i++) {
            relations[i] = (float) (Math.random()*0.001);
            relations_val[i] = (float) (Math.random()*0.001);
        }
        try {
            file = new File(Environment.getExternalStorageDirectory(), name_file);
            stream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void write(String accX, String accY, String accZ, String gyrX, String gyrY, String gyrZ, float[] allVector) {
        String line_space = "\n";
        String char_space = ",";
        String char_tab   = "\t";
        try {
            for(int act=0;act<6;act++){
                for(int idx= 0+150*act;idx<150*(act+1);idx++){
                    stream.write(String.valueOf(allVector[idx]).getBytes());stream.write(char_space.getBytes());
                }stream.write(line_space.getBytes());
            }
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
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(MainActivity.this);
        crono = (Chronometer) findViewById(R.id.lbl_crono);
        toggle_m = (ToggleButton) findViewById(R.id.btn_toogle);
        final Model_Functions model = new Model_Functions();
        final Model_Tensorflow tensorflow = new Model_Tensorflow();
        long t1 =  new Date().getTime();
        tensorflow.Load_Tensorflow(this.getApplicationContext());
        long t2 = new Date().getTime();
        long t3 =  t2 - t1;
        Log.i("Tensorflow", "time loading model" + String.valueOf(t3));

        InitializeVariables();
        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.i("Tensorflow", "Button Start");
                running = true;
                sensorFusion.correctedAcceleration().start();
                sensorFusion.correctedAngularVelocity().start();
                sensorFusion.start();
                led.play();
                crono.setBase(SystemClock.elapsedRealtime());
                crono.start();

                input_Accx.clear();
                input_Accy.clear();
                input_Accz.clear();
                input_Gyrx.clear();
                input_Gyry.clear();
                input_Gyrz.clear();


                SuperActivityToast.create(view.getContext(),new Style(), Style.ANIMATIONS_FADE)
                        .setText("Loading")
                        .setTextSize(25)
                        .setIconPosition(Style.ICONPOSITION_BOTTOM)
                        .setIconResource(R.drawable.home_bio)
                        .setDuration(Style.DURATION_SHORT)
                        .setFrame(Style.FRAME_KITKAT)
                        .show();

                new Thread(){
                    public void run(){
                        Log.i("Tensorflow","Activated Online mode");
                        Integer Inicio = 0;
                        while(running){
                            try{
                                if(Inicio == 0){
                                    sleep(2000);
                                    Inicio = 1;

                                }
                                sleep(100);
                                long start_time =  new Date().getTime();

                                float averageAcc = model.calculateAverage(input_Accx,input_Accy,input_Accz,1);
                                float averageGyr = model.calculateAverage(input_Gyrx,input_Gyry,input_Gyrz,2);
                                Log.i("Tensorflow","Average Acc = " + averageAcc);
                                Log.i("Tensorflow","Average Gyr = " + averageGyr);

                                if(averageAcc > 0.2f && averageGyr > 0.2f) {
                                    long threshold_time =  new Date().getTime();
                                    Log.i("Tensorflow", "Action");
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            toggle_m.setChecked(true);
                                            toggle_m.setBackgroundColor(Color.BLUE);
                                        }
                                    });
                                    sleep(800);
                                    long get_data_time =  new Date().getTime();
                                    ArrayList<Float> input_Accx2 = new ArrayList(model.calculateNormalize(input_Accx,1,0));
                                    ArrayList<Float> input_Accy2 = new ArrayList(model.calculateNormalize(input_Accy,1,0));
                                    ArrayList<Float> input_Accz2 = new ArrayList(model.calculateNormalize(input_Accz,1,0));
                                    ArrayList<Float> input_Gyrx2 = new ArrayList(model.calculateNormalize(input_Gyrx,2,0));
                                    ArrayList<Float> input_Gyry2 = new ArrayList(model.calculateNormalize(input_Gyry,2,0));
                                    ArrayList<Float> input_Gyrz2 = new ArrayList(model.calculateNormalize(input_Gyrz,2,0));
                                    Log.i("Tensorflow", String.valueOf(input_Accx2.size()));
                                    int shift1 = 25;
                                    int shift2 = 25;
                                    for(int i=0;i<input_Accx2.size();i++){
                                        relations_val[shift1+(num_data*0)+i] = input_Accx2.get(i);
                                        relations_val[shift1+(num_data*1)+i] = input_Accy2.get(i);
                                        relations_val[shift1+(num_data*2)+i] = input_Accz2.get(i);
                                    }
                                    for(int i=0;i<input_Gyrx2.size();i++){
                                        relations_val[shift2+(num_data*3)+i] = input_Gyrx2.get(i);
                                        relations_val[shift2+(num_data*4)+i] = input_Gyry2.get(i);
                                        relations_val[shift2+(num_data*5)+i] = input_Gyrz2.get(i);
                                    }

                                    long posting_time =  new Date().getTime();
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            show_results(tensorflow.TensorflowModel(relations_val));
                                        }
                                    });
                                    long writing_time =  new Date().getTime();
                                    write(input_Accx2.toString(), input_Accy2.toString(), input_Accz2.toString(),
                                            input_Gyrx2.toString(), input_Gyry2.toString(),input_Gyrz2.toString(),relations_val);
                                    long final_time =  new Date().getTime();

                                    Log.i("Tensorflow","From start to end = "+ String.valueOf(final_time-start_time));
                                    Log.i("Tensorflow","From start to detect th = "+ String.valueOf(threshold_time-start_time));
                                    Log.i("Tensorflow","From th to detect data = "+ String.valueOf(get_data_time-threshold_time));
                                    Log.i("Tensorflow","From data to post (pross) = "+ String.valueOf(posting_time-get_data_time));
                                    Log.i("Tensorflow","From post to write = "+ String.valueOf(writing_time-posting_time));
                                    Log.i("Tensorflow","From write to end  = "+ String.valueOf(final_time-writing_time));


                                    sleep(500);

                                }else{
                                    Log.i("Tensorflow", "No Action");
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            toggle_m.setChecked(false);
                                            toggle_m.setBackgroundColor(Color.DKGRAY);
                                        }
                                    });
                                }

//                                Log.i("Tensorflow", "Acc X Vector " + String.valueOf(input_Accx.size()));
//                                Log.i("Tensorflow", "Acc Y Vector " + String.valueOf(input_Accy.size()));
//                                Log.i("Tensorflow", "Acc Z Vector " + String.valueOf(input_Accz.size()));
//                                Log.i("Tensorflow", "Gyr X Vector " + String.valueOf(input_Gyrx.size()));
//                                Log.i("Tensorflow", "Gyr Y Vector " + String.valueOf(input_Gyry.size()));
//                                Log.i("Tensorflow", "Gyr Z Vector " + String.valueOf(input_Gyrz.size()))
                            }catch(InterruptedException e){e.printStackTrace();}
                        }
                    }
                }.start();
            }
        });

        findViewById(R.id.btn_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                running = false;
                Log.i("Tensorflow","Button Stop");
                crono.stop();
                led.stop(true);
                sensorFusion.stop();
                sensorFusion.correctedAngularVelocity().stop();
                sensorFusion.correctedAcceleration().stop();
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

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // this is simulated data
                long tf_t1 =  new Date().getTime();
                show_results(tensorflow.TensorflowModel(relations));
                long tf_t2 = new Date().getTime();
                long tf_t3 =  tf_t2 - tf_t1;
                Log.i("Tensorflow", "time to show model result " + String.valueOf(tf_t3));
            }
        });

        findViewById(R.id.btn_Sensor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // this is real data
                long tf_t1 =  new Date().getTime();
                show_results(tensorflow.TensorflowModel(relations_val));
                long tf_t2 = new Date().getTime();
                long tf_t3 =  tf_t2 - tf_t1;
                Log.i("Tensorflow", "time to show model result " + String.valueOf(tf_t3));
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


