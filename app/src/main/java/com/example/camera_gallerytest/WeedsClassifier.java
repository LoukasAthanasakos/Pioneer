package com.example.camera_gallerytest;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class WeedsClassifier extends AsyncTask<Bitmap, Void, String> {

    private static final String TAG = "WeedsClassifier";
    /** Dimensions of inputs. */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;
    private final int[] intValues = new int [224 * 224];//[getImageSizeX() * getImageSizeY()];
    private final double THRESHOLD = 0.8;

    private String modelFile = "1_mobilenet_True_0.5_0_0_r.tflite";
    private String labelsFile = "weeds_labels.txt";
    private int numLabels = 5;

    private Interpreter tflite;
    private Context context;


    public WeedsClassifier(Context context){
        this.context = context;
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String doInference(Bitmap image){

        float[][] labelProbArray = new float[1][numLabels];
        int result = -1;
        String prediction;

        ByteBuffer preprocessedImage = preprocess(image);

        long startTime = SystemClock.uptimeMillis();
        tflite.run(preprocessedImage, labelProbArray);
        long endTime = SystemClock.uptimeMillis();
        List<String> labels = null;
        try {
            labels = loadLabelList();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "SIZE " + labels.size());

        Log.i(TAG, "Timecost to run model inference: " + (endTime - startTime));
        result = argmax(labelProbArray[0]);

        if (result != -1)
            prediction = labels.get(result);
        else
            prediction = "Take another picture, please";

        return prediction;
    }

    public int argmax(float[] classifierOutput){// Object[] argmax(float[] array){

        int best = -1;
        float best_confidence = 0.0f;

        for(int i = 0;i < classifierOutput.length;i++){

            float value = classifierOutput[i];

            if (value > best_confidence){
                best_confidence = value;
                best = i;
            }
        }
        Log.i("Best Confidence", String.valueOf(best_confidence));


        if  (best_confidence > THRESHOLD)
            return best; //new Object[]{best,best_confidence};
        else
            return -1;

    }

    private ByteBuffer preprocess(Bitmap bitmap){

        bitmap = ImageUtils.processBitmap(bitmap,DIM_IMG_SIZE_X);

        ByteBuffer imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE
                                * DIM_IMG_SIZE_X //getImageSizeX()
                                * DIM_IMG_SIZE_Y // getImageSizeY()
                                * DIM_PIXEL_SIZE
                                * getNumBytesPerChannel());

        imgData.order(ByteOrder.nativeOrder());

        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) { //getImageSizeX()
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) { //getImageSizeY()
                final int pixelValue = intValues[pixel++];
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        return imgData;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.context.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    protected int getNumBytesPerChannel() {
        return 4; // Float.SIZE / Byte.SIZE; BYTE_SIZE_OF_FLOAT
    }


    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<>();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.context.getAssets().open(labelsFile)));

        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }


    @Override
    protected String doInBackground(Bitmap... images) {
        return doInference(images[0]);
    }

    protected void onProgressUpdate(Integer... progress) {
        //setProgressPercent(progress[0]);
    }

    protected void onPostExecute(Long result) {
        //showDialog("Downloaded " + result + " bytes");
    }
}