package io.antmedia.android.broadcaster.encoder;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import io.antmedia.android.broadcaster.encoder.AudioEncoder;
import io.antmedia.android.broadcaster.network.IMediaMuxer;

/**
 * Created by mekya on 28/03/2017.
 */
public class AudioHandler extends Handler {

    public static final int RECORD_AUDIO = 0;
    public static final int END_OF_STREAM = 2;

    private AudioEncoder audioEncoder = null;

    public AudioEncoder getAudioEncoder() {
        return audioEncoder;
    }

    public AudioHandler(Looper looper) {
        super(looper);

    }

    public boolean startAudioEncoder(IMediaMuxer muxerHandler, int sampleRate, int bufferSize) {
        boolean result = false;

        audioEncoder = new AudioEncoder();
        try {
            result = audioEncoder.startAudioEncoder(sampleRate, 1, 64000, bufferSize, muxerHandler);
        } catch (Exception e) {
            e.printStackTrace();
            audioEncoder = null;
        }
        return result;
    }

    @Override
    public void handleMessage(Message msg) {
        if (audioEncoder == null) {
            return;
        }



        switch (msg.what) {
            case END_OF_STREAM:
                if (audioEncoder.getState() == Thread.State.RUNNABLE) {
                    Log.d("audio handler", "stop audio encoding...");
                    audioEncoder.stopEncoding();
                    removeMessages(RECORD_AUDIO);
                }
                break;
            case RECORD_AUDIO:
                        /* msg.obj is the byte array buffer
                         * msg.arg1 is the length of the byte array
                         * msg.arg2 is the timestamp of frame in milliseconds
                         */
                audioEncoder.encodeAudio((byte[]) msg.obj, msg.arg1, msg.arg2 * 1000);
                break;
        }
    }
}
