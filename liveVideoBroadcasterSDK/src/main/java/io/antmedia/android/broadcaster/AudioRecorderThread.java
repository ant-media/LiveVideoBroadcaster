package io.antmedia.android.broadcaster;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Message;
import android.util.Log;

import io.antmedia.android.broadcaster.encoder.AudioHandler;

/**
 * Created by mekya on 28/03/2017.
 */

class AudioRecorderThread extends Thread {

    private static final String TAG = AudioRecorderThread.class.getSimpleName();
    private final int mSampleRate;
    private final long startTime;
    private final MediaProjection mediaProjection;
    private volatile boolean stopThread = false;

    private android.media.AudioRecord audioRecord;
    private AudioHandler audioHandler;

    public AudioRecorderThread(int sampleRate, long recordStartTime, AudioHandler audioHandler, MediaProjection mediaProjection) {
        this.mSampleRate = sampleRate;
        this.startTime = recordStartTime;
        this.audioHandler = audioHandler;
        this.mediaProjection = mediaProjection;
    }


    @Override
    public void run() {
        //Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        int bufferSize = android.media.AudioRecord
                .getMinBufferSize(mSampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
        byte[][] audioData;
        int bufferReadResult;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        {
            AudioPlaybackCaptureConfiguration audioPlaybackCaptureConfiguration =
                    new AudioPlaybackCaptureConfiguration
                            .Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build();
            audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfiguration)
                    .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(mSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();

        }
        else {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    mSampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }

        // divide byte buffersize to 2 to make it short buffer
        audioData = new byte[1000][bufferSize];

        audioRecord.startRecording();

        int i = 0;
        byte[] data;
        while ((bufferReadResult = audioRecord.read(audioData[i], 0, audioData[i].length)) > 0) {

            data = audioData[i];

            Message msg = Message.obtain(audioHandler, AudioHandler.RECORD_AUDIO, data);
            msg.arg1 = bufferReadResult;
            msg.arg2 = (int)(System.currentTimeMillis() - startTime);
            audioHandler.sendMessage(msg);


            i++;
            if (i == 1000) {
                i = 0;
            }
            if (stopThread) {
                break;
            }
        }

        Log.d(TAG, "AudioThread Finished, release audioRecord");

    }

    public void stopAudioRecording() {

        if (audioRecord != null && audioRecord.getRecordingState() == android.media.AudioRecord.RECORDSTATE_RECORDING) {
            stopThread = true;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

}
