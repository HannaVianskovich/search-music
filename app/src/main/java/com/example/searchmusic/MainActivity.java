package com.example.searchmusic;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.ybq.android.spinkit.sprite.Sprite;
import com.github.ybq.android.spinkit.style.DoubleBounce;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView etInput,Input;
    Button btnreadStart;
    ProgressBar progressBar;
    final String TAG = "myLogs";
    int myBufferSize = 40192;
    AudioRecord audioRecord;
    boolean isReading = false;
    String nameSong = null;
    String strBody = null;
    String text = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = (ProgressBar)findViewById(R.id.spin_kit);
        Sprite doubleBounce = new DoubleBounce();
        btnreadStart = (Button) findViewById(R.id.button2);
        createAudioRecorder();

        Log.d(TAG, "init state = " + audioRecord.getState());
        etInput = (TextView) findViewById(R.id.etInput);
        etInput.setMovementMethod(new ScrollingMovementMethod());
        Input = (TextView) findViewById(R.id.Input);
    }
    void createAudioRecorder() {

        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int minInternalBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                channelConfig, audioFormat);
        int internalBufferSize = minInternalBufferSize * 4;
        Log.d(TAG, "minInternalBufferSize = " + minInternalBufferSize
                + ", internalBufferSize = " + internalBufferSize
                + ", myBufferSize = " + myBufferSize);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, internalBufferSize);

        audioRecord.setPositionNotificationPeriod(1000);

        audioRecord.setNotificationMarkerPosition(10000);
        audioRecord
                .setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
                    public void onPeriodicNotification(AudioRecord recorder) {
                        Log.d(TAG, "onPeriodicNotification");
                    }

                    public void onMarkerReached(AudioRecord recorder) {
                        Log.d(TAG, "onMarkerReached");
                        isReading = false;
                    }

                });
    }
    public void readStart(View v) {
        Log.d(TAG, "record start");

        progressBar = (ProgressBar)findViewById(R.id.spin_kit);
        Sprite doubleBounce = new DoubleBounce();
        progressBar.setIndeterminateDrawable(doubleBounce);
        progressBar.setVisibility(View.VISIBLE);
        audioRecord.startRecording();
        int recordingState = audioRecord.getRecordingState();
        Log.d(TAG, "recordingState = " + recordingState);
        Log.d(TAG, "read start");

        isReading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (audioRecord == null)
                    return;
                ByteArrayOutputStream fos = null;
                try {
                    fos = new ByteArrayOutputStream();
                    byte[] myBuffer = new byte[myBufferSize];
                    int readCount = audioRecord.read(myBuffer, 0, myBufferSize);
                    int counter = 0;
                    int totalCount = 0;
                    while (readCount != 0) {
                        totalCount += readCount;
                        Log.d(TAG, "readCount = " + readCount + ", totalCount = "
                                + totalCount);
                        fos.write(myBuffer, 0, readCount);

                        Thread.sleep(50);
                        if (++counter < 10) {
                            readCount = audioRecord.read(myBuffer, 0, myBufferSize);
                        } else {
                            readCount = 0;
                        }
                    }

                    audioRecord.stop();
                    Log.d(TAG, "done");

                } catch (Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                }
                try {
                    System.out.println( Environment.getExternalStorageDirectory());

                    byte[] byteArray = fos.toByteArray();
                    String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);
                    System.out.println(encoded.charAt(encoded.length() - 1));

                    OkHttpClient client = new OkHttpClient();

                    MediaType mediaType = MediaType.parse("text/plain");
                    RequestBody body = RequestBody.create(mediaType, encoded);
                    Request request = new Request.Builder()
                            .url("https://shazam.p.rapidapi.com/songs/detect")
                            .post(body)
                            .addHeader("content-type", "text/plain")
                            .addHeader("x-rapidapi-key", "your key")
                            .addHeader("x-rapidapi-host", "shazam.p.rapidapi.com")
                            .build();

                    try {
                        Response response = client.newCall(request).execute();
                        strBody = response.body().string();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                try {

                                    System.out.println(strBody);
                                    JSONObject reader = new JSONObject(strBody);
                                    nameSong = reader.getJSONObject("track").getString( "subtitle")+
                                            reader.getJSONObject("track").getString("title");
                                    System.out.println(nameSong);

                                    Input.setText(nameSong);
                                    text = reader.getJSONObject("track").getJSONArray("sections").getJSONObject(1). getString ("text");

                                    System.out.println(text);
                                    etInput.setText(text);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                progressBar.setVisibility(View.GONE);

                            }
                        });

                        System.out.println(nameSong);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }
}
