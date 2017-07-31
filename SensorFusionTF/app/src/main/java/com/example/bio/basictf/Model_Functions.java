package com.example.bio.basictf;

import android.app.Activity;
import java.util.ArrayList;

/**
 * Created by BIO on 2017-06-28.
 */

public class Model_Functions extends Activity{

    public ArrayList<Float> calculateNormalize(ArrayList<Float> val, Integer select, Integer averg) {
        double min_v = -2;
        double max_v = +2;
        if(select == 1){ min_v = -2.0; max_v = +2.0;}
        if(select == 2){ min_v = -250.0; max_v = +250.;}

        ArrayList<Float> Norma = new ArrayList<>();
        if(averg == 1){
            for (int i = val.size()-10; i < val.size(); i++) {
                Norma.add((float) ((2 * ((val.get(i) - min_v) / (max_v - min_v))) - 1.0));
            }
        }else {
            for (int i = 0; i < val.size(); i++) {
                Norma.add((float) ((2 * ((val.get(i) - min_v) / (max_v - min_v))) - 1.0));
            }
        }
        return Norma;
    }

    public float calculateAverage(ArrayList<Float> xval, ArrayList<Float> yval, ArrayList<Float> zval, Integer select) {
        float sum = (float) 0.0;
        ArrayList<Float> mark1 = calculateNormalize(xval,select,1);
        ArrayList<Float> mark2 = calculateNormalize(yval,select,1);
        ArrayList<Float> mark3 = calculateNormalize(zval,select,1);
        for (int i=0; i< mark1.size(); i++) {
            sum = (sum + Math.abs(mark1.get(i)) + Math.abs(mark2.get(i)) + Math.abs(mark3.get(i)));
        }
        return xval.isEmpty()? 0: (float) (1.0 * sum / mark1.size());
    }




}
