package io.antmedia.android.broadcaster.encoder;

import android.view.Surface;

import java.io.IOException;

import io.antmedia.android.broadcaster.network.IMediaMuxer;

public class VideoHandler extends Thread {

    private VideoEncoderCore mVideoEncoder;

    private boolean endOfStream = false;

    public void prepareEncoder(int width, int height, int bitRate, int frameRate,
                                IMediaMuxer writerHandle) throws IllegalStateException
    {
        try {
            mVideoEncoder = new VideoEncoderCore(width, height, bitRate, frameRate, writerHandle);

        } catch (IOException ioe) {
            mVideoEncoder = null;
            throw new RuntimeException(ioe);
        }
    }

    public Surface getInputSurface() {
        return mVideoEncoder != null ? mVideoEncoder.getInputSurface() : null;
    }

    @Override
    public void run() {
        while (true) {
            mVideoEncoder.drainEncoder(endOfStream);
            if (endOfStream) {
                mVideoEncoder.release();
                mVideoEncoder.getInputSurface().release();
                mVideoEncoder.stopMuxer();
                break;
            }
        }
    }


    public void stopEncoder() {
        endOfStream = true;

    }

}
