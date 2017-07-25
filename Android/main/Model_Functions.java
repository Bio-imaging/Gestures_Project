package com.example.bio.basictf;

import android.app.Activity;
import java.util.ArrayList;

/**
 * Created by BIO on 2017-06-28.
 */

public class Model_Functions extends Activity{

    public float calculateAverage(ArrayList<Float> xval, ArrayList<Float> yval, ArrayList<Float> zval, Integer select,Integer num_data, Integer sensing_time) {
        double min_v; double max_v;
        if(select == 1){ min_v = -4.0; max_v = 4.0;}
        else{            min_v = -250; max_v = +250;}

        float sum = (float) 0.0;
        for (int i=num_data-(sensing_time/10) ; i< num_data; i++) {
            double mark1 = (2*( (xval.get(i)-min_v) / (max_v-min_v)) - 1);
            double mark2 = (2*( (yval.get(i)-min_v) / (max_v-min_v)) - 1);
            double mark3 = (2*( (zval.get(i)-min_v) / (max_v-min_v)) - 1);
            sum = (float) (sum + Math.abs(mark1) + Math.abs(mark2) + Math.abs(mark3));
        }
        return xval.isEmpty()? 0: (float) (1.0 * sum / xval.size());
    }

    public ArrayList<Float> calculateNormalize(ArrayList<Float> val, Integer select) {
//        Float min_v = Collections.min(val);
//        Float max_v = Collections.max(val);
        double min_v;
        double max_v;
        if(select == 1){ min_v = -4.0; max_v = 4.0;}
        else{            min_v = -250; max_v = +250;}

        for (int i=0; i< val.size(); i++) {
            val.set(i, (float) (2*( (val.get(i)-min_v) / (max_v-min_v)) - 1));
        }
        return val;
    }
}
