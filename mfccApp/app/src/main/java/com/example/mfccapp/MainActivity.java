package com.example.mfccapp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mfccapp.MFCC;
import com.example.mfccapp.FFT;

//import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
//import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 51200;
    private static final int SAMPLE_DURATION_MS = 3000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final String MODEL_FILENAME = "file:///android_asset/q_wavenet_mobile.pb";
    private static final String INPUT_DATA_NAME = "Placeholder:0";
    private static final String OUTPUT_SCORES_NAME = "output";

    private static final char[] map = new char[]{'0', ' ', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;
    private Button startButton;
    private TextView outputText;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    //private TensorFlowInferenceInterface inferenceInterface;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up the UI.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startButton = (Button) findViewById(R.id.start);
        startButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startRecording();
                    }
                });
        outputText = (TextView) findViewById(R.id.output_text);


        // Load the Pretrained WaveNet model.
        //inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);

        requestMicrophonePermission();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestMicrophonePermission() {
        requestPermissions(
                new String[] {android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        }
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Start recording");


        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            Log.v(LOG_TAG, "read: " + numberRead);
            int maxLength = recordingBuffer.length;
            recordingBufferLock.lock();
            try {
                if (recordingOffset + numberRead < maxLength) {
                    System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, numberRead);
                } else {
                    shouldContinue = false;
                }
                recordingOffset += numberRead;
            } finally {
                recordingBufferLock.unlock();
            }
        }
        record.stop();
        record.release();
        startRecognition();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    private void recognize() {
        Log.v(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        double[] doubleInputBuffer = new double[RECORDING_LENGTH];
        long[] outputScores = new long[157];
        String[] outputScoresNames = new String[]{OUTPUT_SCORES_NAME};


        recordingBufferLock.lock();
        try {
            int maxLength = recordingBuffer.length;
            System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
        } finally {
            recordingBufferLock.unlock();
        }

        // We need to feed in float values between -1.0 and 1.0, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < RECORDING_LENGTH; ++i) {
            doubleInputBuffer[i] = inputBuffer[i] / 32767.0;
           // Log.v(LOG_TAG, "ARRRRR Input======> " + Double.toString(doubleInputBuffer[i]));
        }

        //MFCC java library.
        Log.v(LOG_TAG, "The code reached line 201 successfully");
        //Log.v(LOG_TAG, "ARRRRR Input======> " + Arrays.toString(doubleInputBuffer));
        MFCC mfccConvert = new MFCC();
        Log.v(LOG_TAG, "The code reached line 204 successfully");

        //float[] mfccInput = mfccConvert.process(doubleInputBuffer);
        Log.v(LOG_TAG, "The code reached line 207 successfully");

        //Log.v(LOG_TAG, "MFCC Size======> " + mfccInput.length);
        //Log.v(LOG_TAG, "MFCC Input======> " + Arrays.toString(mfccInput));

        double[][] mfccInput = mfccConvert.melSpectrogram(doubleInputBuffer);
        Log.v(LOG_TAG, "MFCC Size======> " + Integer.toString(mfccInput.length) + " " + Integer.toString(mfccInput[0].length));
        Log.v(LOG_TAG, "MFCC Input======> " + Arrays.toString(mfccInput));

        for(int i=0; i<mfccInput.length ; i++){
            for(int j=0;j<mfccInput[0].length;j++){
                Log.v(LOG_TAG,"at i : "+i+" and j " + j+ " mel spec is " + mfccInput[i][j]);
            }
        }


        // Run the model.
       // inferenceInterface.feed(INPUT_DATA_NAME, mfccInput, 1, 157, 20);
        //inferenceInterface.run(outputScoresNames);
        //inferenceInterface.fetch(OUTPUT_SCORES_NAME, outputScores);
        //Log.v(LOG_TAG, "OUTPUT======> " + Arrays.toString(outputScores));


        //Output the result.
        String result = "";
        for (int i = 0;i<outputScores.length;i++) {
            if (outputScores[i] == 0)
                break;
            result += map[(int) outputScores[i]];
        }
        final String r = result;
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                outputText.setText(r);
            }
        });

        Log.v(LOG_TAG, "End recognition: " +result);
    }

}
