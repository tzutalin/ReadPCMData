package com.da.readpcmtest;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.PowerManager;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author tzu.ta.lin
 * Android API Level should be larger than 19
 * Push to /system/priv-app/ because of permission problems
 * It is for test only
 */
public class MainActivity extends Activity {
    // ===========================================================
    // Fields
    // ===========================================================
    private static String TAG                   = "PCM_Test";
    private static final String MEDIA_DUMP_FILE = "/sdcard/pcmdata.pcm";
    
    // ===========================================================
    // Fields
    // ===========================================================
    private boolean mIsRecord                   = false;
    private boolean mInterrupted                = false;
    
    private Button  mStartBtn                   = null;
    private Button  mStopBtn                    = null;

    
    private PowerManager.WakeLock mWakeLock     = null;
    // ===========================================================
    // UI 
    // ===========================================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Android123");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mInterrupted = true;
    }

    public void initUI() {

        mStartBtn = (Button) findViewById(R.id.button1);
        mStopBtn = (Button) findViewById(R.id.button2);

        // Button start
        mStartBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!mIsRecord) {
                    Log.d(TAG, "start Recording");
                    mInterrupted = false;
                    startPCMTest();
                }
            }
        });

        // Button stop
        mStopBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Log.d(TAG, "STOP Recording");
                mInterrupted = true;
            }
        });
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
    private class AudioRecordCaptureThread implements Runnable {
        public void run() {
            Log.d(TAG, "AudioRecordCaptureThread");

            int sampleRate = 44100;
            
            final int PCM_MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            final int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            
            ByteBuffer mPcmBuffer = ByteBuffer.allocateDirect(minBufferSize);
            
            // REMOTE_SUBMIX means audio source for a submix of audio streams to be presented remotely.
            AudioRecord tAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX, sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, mPcmBuffer.capacity()); 
            
            try {
                if (tAudioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioRecord instance failed to initialize, state is (" + tAudioRecorder.getState() + ")");
                }

                openDebugFile();
                
                tAudioRecorder.startRecording();
                int read_size = 0;
                
                if (tAudioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    throw new IllegalStateException("AudioRecord instance failed to start recording");
                }
                
                mWakeLock.acquire();
                mIsRecord = true;

                while (!isInterrupted()) {
                    byte[] pcmByteArray = null;
                    read_size = tAudioRecorder.read(mPcmBuffer, PCM_MIN_BUFFER_SIZE); 
                    
                    Log.d(TAG, "PCM_MIN_BUFFER_SIZE : " + PCM_MIN_BUFFER_SIZE);
                    Log.d(TAG, "read_size : " + read_size);
                    
                    if (read_size > 0) {
                        pcmByteArray = Arrays.copyOf(mPcmBuffer.array(), read_size);
                        
                        // convert to big endian 
                        pcmByteArray = conver2BigEndian(pcmByteArray, read_size);
                        
                        if (pcmByteArray != null) {
                            writeToDebugFile(pcmByteArray.length, pcmByteArray);
                        }
                        
                    } else {                
                        Log.w(TAG, " read_size is 0. AudioRecord should not happen, it is a block function");
                    }
                }

            } catch (Exception e) {
                Log.d(TAG, "Exception in AudioRecordCaptureThread " + e);
            } finally {
                tAudioRecorder.stop();
                closeDebugFile();
                
                tAudioRecorder.release();
                
                mInterrupted = true;
                mIsRecord = false;
                
                mWakeLock.release();
                Log.d(TAG, "release in AudioRecordCaptureThread");
            }
        } 
    }
    
    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================
    private void startPCMTest() {
        new Thread(new AudioRecordCaptureThread()).start();
    }

    private boolean isInterrupted() {
        return mInterrupted;
    }
    
    private byte[] conver2BigEndian(byte[] byteArray,final int size) {
        byte bigEndienPCMDataTemp = 0;
        for (int i = 0; i < size; i = i + 2) {
            if (i + 1 < size) {
                bigEndienPCMDataTemp = byteArray[i + 1];
                byteArray[i + 1] = byteArray[i];
                byteArray[i] = bigEndienPCMDataTemp;
            }
        }
        return byteArray;
    }

    private static FileOutputStream sfos = null;

    public static void openDebugFile() {
        closeDebugFile();
        // For Test, write pcm data into a file
        Log.d(TAG, "[debugWriteToFile]: writetofile is true");
        
        try {
            File file = new File(MEDIA_DUMP_FILE);
            if (file.exists()) {
                file.delete();
            }
            
            sfos = new FileOutputStream(file);
            
            Log.d(TAG, "[debugWriteToFile]: writetofile is true");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeDebugFile() {
        if (sfos != null) {
            try {
                sfos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        sfos = null;
    }

    public static void writeToDebugFile(final int byte_size, byte[] pcmByte) {
        try {
            if (sfos != null) {
                sfos.write(pcmByte, 0, byte_size);
            }
        } catch (Exception e) {
            Log.w(TAG, "write file Exception");
        }
    }
}
