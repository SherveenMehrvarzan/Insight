package com.example.redemption;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Locale;

import org.tensorflow.lite.Interpreter;
import java.lang.Object.*;
import com.example.redemption.R.*;
import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Category;
import com.microsoft.projectoxford.vision.contract.Face;
import com.microsoft.projectoxford.vision.contract.Tag;
import com.microsoft.projectoxford.vision.contract.Caption;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;



public class Test extends AppCompatActivity {
    private Interpreter fRecognition;
    private File mCascadeFile;
    private CascadeClassifier faceDetector;
    private TextToSpeech tts;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        OpenCVLoader.initDebug();
        fRecognition = new Interpreter(loadModelFile());


        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        mCascadeFile = new File(cascadeDir,
                "haarcascade_frontalface_default.xml");
        FileOutputStream os;
        try {
            os = new FileOutputStream(mCascadeFile);
        } catch(FileNotFoundException e) {
            os = null;
        }

        byte[] buffer = new byte[4096];
        int bytesRead;
        try {
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
        } catch(IOException i){};
        cascadeDir.delete();

        faceDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {

            }
        });
        tts.setLanguage(Locale.US);
        //VisionServiceRestClient client = new VisionServiceRestClient(getString(R.string.subscription_key), getString(R.string.subscription_apiroot));

    }

    private MappedByteBuffer loadModelFile() {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("model.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch(IOException i) {
            return null;
        }
    }

    public void displayToast(View v){
        Mat img = null;
        try {
            img = Utils.loadResource(getApplicationContext(), R.drawable.test);
        } catch (IOException e) {
            e.printStackTrace();
        }

        float[][][] facialExpressions = processFaces(img);



    }

    private float[][][] processFaces(Mat img) {
        Mat img_result = img.clone();
        Imgproc.cvtColor(img_result, img_result, Imgproc.COLOR_BGR2GRAY);
        Rect[] facesArray = cropFaces(img_result);

        float[][][] facialExpressions = new float[facesArray.length][1][7];

        for (int i = 0; i < facesArray.length; i++) {

            Mat face_img = img_result.submat(facesArray[i].y,facesArray[i].y + facesArray[i].height, facesArray[i].x, facesArray[i].x + facesArray[i].width);
            Bitmap img_bitmap = Bitmap.createBitmap(face_img.cols(), face_img.rows(),Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(face_img, img_bitmap);
            facialExpressions[i] = facialExpressionRecognition(img_bitmap);
            Imgproc.rectangle(img, facesArray[i].tl(), facesArray[i].br(), new Scalar(255, 255, 0, 0), 3);

            String emotion = decodeFERProbs(facialExpressions[i][0]);
            Imgproc.putText(img, emotion, facesArray[i].tl(), Imgproc.FONT_HERSHEY_COMPLEX, 0.5, new Scalar(255,0,255,0));
            tts.speak("A person is " + emotion, TextToSpeech.QUEUE_ADD, null);
        }

        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB);
        Bitmap img_bitmap = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, img_bitmap);

        ImageView imageView = findViewById(R.id.img);
        imageView.setImageBitmap(img_bitmap);
        return facialExpressions;
    }

    private String decodeFERProbs(float[] probs) {
        int maxInd = 0;
        float maxVal = 0;
        for(int i = 0; i < probs.length; i++) {
            if(probs[i] > maxVal) {
                maxVal = probs[i];
                maxInd = i;
            }
        }
        String res = "";
        switch(maxInd) {
            case 0:
                res = "Angry";
                break;
            case 1:
                res = "Disgusted";
                break;
            case 2:
                res = "Fearful";
                break;
            case 3:
                res = "Happy";
                break;
            case 4:
                res = "Neutral";
                break;
            case 5:
                res = "Sad";
                break;
            case 6:
                res = "Surprised";
                break;
        }
        return res;
    }

    private Rect[] cropFaces(Mat img) {
        MatOfRect faceDetections = new MatOfRect();

        faceDetector.detectMultiScale(img, faceDetections);

        Log.i("Yay", String.format("Detected %s faces", faceDetections.toArray().length));

        return faceDetections.toArray();
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        // Specify the size of the byteBuffer
        int width = 48;
        int height = 48;

        int size = bitmap.getRowBytes() * bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(byteBuffer);
        return byteBuffer;
    }

    private float[][] facialExpressionRecognition(Bitmap bitmap) {
        Bitmap resized_bitmap = Bitmap.createScaledBitmap(bitmap, 48,48,true);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(resized_bitmap);
        float[][] res = new float[1][7];
        fRecognition.run(byteBuffer,res);
        return res;
    }
}
