package io.antmedia.android.broadcaster;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import androidx.annotation.Nullable;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.antmedia.android.R;
import io.antmedia.android.broadcaster.encoder.AudioHandler;
import io.antmedia.android.broadcaster.encoder.VideoHandler;
import io.antmedia.android.broadcaster.network.IMediaMuxer;
import io.antmedia.android.broadcaster.network.RTMPStreamer;


/**
 * Created by mekya on 28/03/2017.
 */

public class LiveVideoBroadcaster extends Service implements ILiveVideoBroadcaster {

    private static final String TAG = LiveVideoBroadcaster.class.getSimpleName();
    private IMediaMuxer mRtmpStreamer;
    private AudioRecorderThread audioThread;
    private boolean isRecording = false;
    private AudioHandler audioHandler;
    private Activity context;
    private final IBinder mBinder = new LocalBinder();

    private int frameRate = 20;
    public static final int PERMISSIONS_REQUEST = 8954;

    public final static int SAMPLE_AUDIO_RATE_IN_HZ = 44100;
    private AlertDialog mAlertDialog;
    private HandlerThread mRtmpHandlerThread;
    private HandlerThread audioHandlerThread;
    private ConnectivityManager connectivityManager;
    private Timer connectionCheckerTimer;
    private MediaProjection mediaProjection;
    private int densityDpi;
    private int widthPixels;
    private int heightPixels;
    private VideoHandler videoHandler;
    private int bitrate;

    public boolean isConnected() {
        if (mRtmpStreamer != null) {
            return mRtmpStreamer.isConnected();
        }
        return false;
    }

    public void pause() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }

        stopBroadcasting();

    }


    public class LocalBinder extends Binder {
        public ILiveVideoBroadcaster getService() {
            // Return this instance of LocalService so clients can call public methods
            return LiveVideoBroadcaster.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();


    }

    @Override
    public void onDestroy() {
        audioHandlerThread.quitSafely();
        mRtmpHandlerThread.quitSafely();
        super.onDestroy();
    }

    public void init(Activity activity, int bitrate, int framerate) {
        try {
            this.bitrate = bitrate;
            this.frameRate = framerate;
            audioHandlerThread = new HandlerThread("AudioHandlerThread", Process.THREAD_PRIORITY_AUDIO);
            audioHandlerThread.start();
            audioHandler = new AudioHandler(audioHandlerThread.getLooper());
            this.context = activity;

            mRtmpHandlerThread = new HandlerThread("RtmpStreamerThread"); //, Process.THREAD_PRIORITY_BACKGROUND);
            mRtmpHandlerThread.start();
            mRtmpStreamer = new RTMPStreamer(mRtmpHandlerThread.getLooper());
            connectivityManager = (ConnectivityManager) this.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasConnection() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            return true;
        }
        return false;
    }

    public boolean startBroadcasting(final String rtmpUrl) {

        isRecording = false;

        if (!hasConnection()) {
            Log.w(TAG, "There is no active network connection");
        }

        connectionCheckerTimer = new Timer();
        connectionCheckerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (hasConnection())
                {
                    if (!isConnected())
                    {
                        if (mRtmpStreamer.open(rtmpUrl))
                        {
                            Log.i(TAG, "RTMP is getting connected to " + rtmpUrl + " frame count in queue: " + mRtmpStreamer.getFrameCountInQueue());
                        }
                        else {
                            Log.w(TAG, "RTMP cannot connect to the " + rtmpUrl + " frame count in queue: " + mRtmpStreamer.getFrameCountInQueue());
                        }
                    }
                    else {
                        Log.d(TAG, "RTMP is already connected to " + rtmpUrl + " frame count in queue: " + mRtmpStreamer.getFrameCountInQueue());
                    }
                }
                else {
                    Log.w(TAG, "There is no network connection. Frame count in queue: " + mRtmpStreamer.getFrameCountInQueue());
                }
            }
        }, 0, 1000);

        try {

            {
                final long recordStartTime = System.currentTimeMillis();

                videoHandler = new VideoHandler();
                videoHandler.prepareEncoder(widthPixels, heightPixels, bitrate, frameRate, mRtmpStreamer);

                mediaProjection.createVirtualDisplay("ScreenCapture",
                        widthPixels, heightPixels, densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        videoHandler.getInputSurface(), null,  null);

                int minBufferSize = AudioRecord
                        .getMinBufferSize(SAMPLE_AUDIO_RATE_IN_HZ,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT);

                audioHandler.startAudioEncoder(mRtmpStreamer, SAMPLE_AUDIO_RATE_IN_HZ, minBufferSize);

                audioThread = new AudioRecorderThread(SAMPLE_AUDIO_RATE_IN_HZ, recordStartTime, audioHandler, mediaProjection);
                audioThread.setName("Audio Handler Thread");
                audioThread.start();
                videoHandler.setName("Video Handler Thread");
                videoHandler.start();
                isRecording = true;

            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return isRecording;
    }


    public void stopBroadcasting() {
        if (isRecording) {

            if (connectionCheckerTimer != null) {
                connectionCheckerTimer.cancel();
                connectionCheckerTimer = null;
            }

            if (audioThread != null) {
                audioThread.stopAudioRecording();
            }

            if (audioHandler != null) {
                audioHandler.sendEmptyMessage(AudioHandler.END_OF_STREAM);
            }

            videoHandler.stopEncoder();

        }

    }


    @Override
    public void setMediaProjection(MediaProjectionManager mediaProjectionManager, Intent data, int resultCode, int densityDpi, final int widthPixels, final int heightPixels) {
        this.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        this.densityDpi = densityDpi;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;


    }

    public boolean isPermissionGranted() {
        boolean cameraPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean microPhonePermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        return cameraPermissionGranted && microPhonePermissionGranted;
    }

    public void requestPermission() {

        boolean cameraPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean microPhonePermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;


        final List<String> permissionList = new ArrayList();
        if (!cameraPermissionGranted) {
            permissionList.add(Manifest.permission.CAMERA);
        }
        if (!microPhonePermissionGranted) {
            permissionList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (permissionList.size() > 0 )
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context,
                    Manifest.permission.CAMERA)) {
                mAlertDialog = new AlertDialog.Builder(context)
                        .setTitle(R.string.permission)
                        .setMessage(getString(R.string.camera_permission_is_required))
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String[] permissionArray = permissionList.toArray(new String[permissionList.size()]);
                                ActivityCompat.requestPermissions(context,
                                        permissionArray,
                                        PERMISSIONS_REQUEST);
                            }
                        })
                        .show();
            }
            else if (ActivityCompat.shouldShowRequestPermissionRationale(context,
                    Manifest.permission.RECORD_AUDIO)) {
                mAlertDialog = new AlertDialog.Builder(context)
                        .setMessage(getString(R.string.microphone_permission_is_required))
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String[] permissionArray = permissionList.toArray(new String[permissionList.size()]);
                                ActivityCompat.requestPermissions(context,
                                        permissionArray,
                                        PERMISSIONS_REQUEST);
                            }
                        })
                        .show();

            }
            else {
                String[] permissionArray = permissionList.toArray(new String[permissionList.size()]);
                ActivityCompat.requestPermissions(context,
                        permissionArray,
                        PERMISSIONS_REQUEST);
            }

        }
    }

    public void showEncoderNotExistDialog() {
        mAlertDialog = new AlertDialog.Builder(context)
                //.setTitle("")
                .setMessage(R.string.not_eligible_for_broadcast)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
