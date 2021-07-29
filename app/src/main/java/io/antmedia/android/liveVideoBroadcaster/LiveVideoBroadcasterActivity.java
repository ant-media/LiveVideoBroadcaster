package io.antmedia.android.liveVideoBroadcaster;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;

import com.google.android.material.snackbar.Snackbar;

import java.util.Timer;
import java.util.TimerTask;

import io.antmedia.android.broadcaster.ILiveVideoBroadcaster;
import io.antmedia.android.broadcaster.LiveVideoBroadcaster;

public class LiveVideoBroadcasterActivity extends AppCompatActivity {


    public static final String RTMP_BASE_URL = "rtmp://ovh36.antmedia.io/LiveApp/";
    private static final String TAG = LiveVideoBroadcasterActivity.class.getSimpleName();
    private ViewGroup mRootView;
    boolean mIsRecording = false;
    private EditText mStreamNameEditText;
    private Timer mTimer;
    private long mElapsedTime;
    public TimerHandler mTimerHandler;
    private Intent mLiveVideoBroadcasterServiceIntent;
    private TextView mStreamLiveStatus;
    private ILiveVideoBroadcaster mLiveVideoBroadcaster;
    private Button mBroadcastControlButton;
    private MediaProjectionManager mMediaProjectionManager;

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private MediaProjection mMediaProjection;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LiveVideoBroadcaster.LocalBinder binder = (LiveVideoBroadcaster.LocalBinder) service;

            if (mLiveVideoBroadcaster == null) {
                mLiveVideoBroadcaster = binder.getService();
            }

            mMediaProjectionManager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);

        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mLiveVideoBroadcaster = null;
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //binding on resume not to having leaked service connection
        mLiveVideoBroadcasterServiceIntent = new Intent(this, LiveVideoBroadcaster.class);
        //this makes service do its job until done

        startService(mLiveVideoBroadcasterServiceIntent);


        setContentView(R.layout.activity_live_video_broadcaster);

        mTimerHandler = new TimerHandler();
        mStreamNameEditText = (EditText) findViewById(R.id.stream_name_edit_text);

        mRootView = (ViewGroup)findViewById(R.id.root_layout);
        mStreamLiveStatus = (TextView) findViewById(R.id.stream_live_status);

        mBroadcastControlButton = (Button) findViewById(R.id.toggle_broadcasting);

        VideoView mVideoView = findViewById(R.id.videoview);

        String mediaName = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        Uri videoUri = Uri.parse(mediaName);

        mVideoView.setVideoURI(videoUri);
        mVideoView.start();

    }


    @Override
    protected void onStart() {
        super.onStart();
        //this lets activity bind
        bindService(mLiveVideoBroadcasterServiceIntent, mConnection, 0);
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");

        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(this,"Screen permission is not granted", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.i(TAG, "Starting screen capture");

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            mLiveVideoBroadcaster.setMediaProjection(mMediaProjection, metrics.densityDpi, metrics.widthPixels, metrics.heightPixels);


            mLiveVideoBroadcaster.init(this, 2000000, 25);
        }
    }

    public void toggleBroadcasting(View v) {
        if (!mIsRecording)
        {
            if (mLiveVideoBroadcaster != null) {
                if (!mLiveVideoBroadcaster.isConnected()) {
                    String streamName = mStreamNameEditText.getText().toString();

                    new AsyncTask<String, String, Boolean>() {
                        ContentLoadingProgressBar
                                progressBar;
                        @Override
                        protected void onPreExecute() {
                            progressBar = new ContentLoadingProgressBar(LiveVideoBroadcasterActivity.this);
                            progressBar.show();
                        }

                        @Override
                        protected Boolean doInBackground(String... url) {
                            return mLiveVideoBroadcaster.startBroadcasting(url[0]);

                        }

                        @Override
                        protected void onPostExecute(Boolean result) {
                            progressBar.hide();
                            mIsRecording = result;
                            if (result) {
                                mStreamLiveStatus.setVisibility(View.VISIBLE);

                                mBroadcastControlButton.setText(R.string.stop_broadcasting);
                                startTimer();//start the recording duration
                            }
                            else {
                                Snackbar.make(mRootView, R.string.stream_not_started, Snackbar.LENGTH_LONG).show();

                                triggerStopRecording();
                            }
                        }
                    }.execute(RTMP_BASE_URL + streamName);
                }
                else {
                    Snackbar.make(mRootView, R.string.streaming_not_finished, Snackbar.LENGTH_LONG).show();
                }
            }
            else {
                Snackbar.make(mRootView, R.string.oopps_shouldnt_happen, Snackbar.LENGTH_LONG).show();
            }
        }
        else
        {
            triggerStopRecording();
        }

    }


    public void triggerStopRecording() {
        if (mIsRecording) {
            mBroadcastControlButton.setText(R.string.start_broadcasting);

            mStreamLiveStatus.setVisibility(View.GONE);
            mStreamLiveStatus.setText(R.string.live_indicator);

            stopTimer();
            mLiveVideoBroadcaster.stopBroadcasting();
        }

        mIsRecording = false;
    }

    //This method starts a mTimer and updates the textview to show elapsed time for recording
    public void startTimer() {

        if(mTimer == null) {
            mTimer = new Timer();
        }

        mElapsedTime = 0;
        mTimer.scheduleAtFixedRate(new TimerTask() {

            public void run() {
                mElapsedTime += 1; //increase every sec
                mTimerHandler.obtainMessage(TimerHandler.INCREASE_TIMER).sendToTarget();

                if (mLiveVideoBroadcaster == null || !mLiveVideoBroadcaster.isConnected()) {
                    mTimerHandler.obtainMessage(TimerHandler.CONNECTION_LOST).sendToTarget();
                }
            }
        }, 0, 1000);
    }


    public void stopTimer()
    {
        if (mTimer != null) {
            this.mTimer.cancel();
        }
        this.mTimer = null;
        this.mElapsedTime = 0;
    }

    private class TimerHandler extends Handler {
        static final int CONNECTION_LOST = 2;
        static final int INCREASE_TIMER = 1;

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INCREASE_TIMER:
                    mStreamLiveStatus.setText(getString(R.string.live_indicator) + " - " + getDurationString((int) mElapsedTime));
                    break;
                case CONNECTION_LOST:
                    /*
                    triggerStopRecording();
                    new AlertDialog.Builder(LiveVideoBroadcasterActivity.this)
                            .setMessage(R.string.broadcast_connection_lost)
                            .setPositiveButton(android.R.string.yes, null)
                            .show();

                     */

                    break;
            }
        }
    }

    public static String getDurationString(int seconds) {

        if(seconds < 0 || seconds > 2000000)//there is an codec problem and duration is not set correctly,so display meaningfull string
            seconds = 0;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        if(hours == 0)
            return twoDigitString(minutes) + " : " + twoDigitString(seconds);
        else
            return twoDigitString(hours) + " : " + twoDigitString(minutes) + " : " + twoDigitString(seconds);
    }

    public static String twoDigitString(int number) {

        if (number == 0) {
            return "00";
        }

        if (number / 10 == 0) {
            return "0" + number;
        }

        return String.valueOf(number);
    }
}
