package com.example.bio.basictf;

/**
 * Created by BIO on 2017-07-21.
 */
import android.content.Context;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class Model_Tensorflow {

    // Tensorflow dependencies and Configuration
    private TensorFlowInferenceInterface inferenceInterface2;
    static {System.loadLibrary("tensorflow_inference");}

    private static final String MODEL_FILE2 = "file:///android_asset/optimized_tfdroid.pb";
    private static final String INPUT_NODE = "I";
    private static final String OUTPUT_NODE2 = "O";
    private static final int num_channel = 6;
    private static final int num_data    = 150;
    private static final int[] INPUT_SIZE2 = {1,num_data*num_channel};

    public void Load_Tensorflow(Context myContext) {
        inferenceInterface2 = new TensorFlowInferenceInterface();
        inferenceInterface2.initializeTensorFlow(myContext.getAssets(), MODEL_FILE2);
    }

    public float[] TensorflowModel(float[] input_vector){
        inferenceInterface2.fillNodeFloat(INPUT_NODE, INPUT_SIZE2, input_vector);
        inferenceInterface2.runInference(new String[] {OUTPUT_NODE2});
        float[] inference = new float[8];
        inferenceInterface2.readNodeFloat(OUTPUT_NODE2, inference);
        return inference;
    }
}
