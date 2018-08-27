/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.antmedia.android.broadcaster.encoder;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;

import io.antmedia.android.broadcaster.encoder.gles.EglCore;
import io.antmedia.android.broadcaster.encoder.gles.FullFrameRect;
import io.antmedia.android.broadcaster.encoder.gles.Texture2dProgram;
import io.antmedia.android.broadcaster.encoder.gles.WindowSurface;
import io.antmedia.android.broadcaster.network.IMediaMuxer;

/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL activity that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL activity creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 *     call TextureMovieEncoder#frameAvailable().
 * </ul>
 *
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
public class TextureMovieEncoder implements Runnable {
    private static final String TAG = TextureMovieEncoder.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_QUIT = 5;
    private static final int MSG_RELEASE_RECORDING = 6;
    private static final int MSG_CHANGE_EFFECT = 7;

    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    private FullFrameRect mFullScreen;
    private int mTextureId;
    private int mFrameNum;
    private VideoEncoderCore mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    private long mRecordingStartTime;
    private long mLastFrameTime = 0;
    private Texture2dProgram.ProgramType mProgramType;
    private EncoderConfig mEncoderConfig;

    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     *       with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final EGLContext mEglContext;
        final IMediaMuxer writerHandler;
        final Texture2dProgram.ProgramType mProgramType;
        public int mFrameRate;

        public EncoderConfig(IMediaMuxer handler, int width, int height, int bitRate, int frameRate,
                             EGLContext sharedEglContext, Texture2dProgram.ProgramType programType) {
            writerHandler = handler;
            mWidth = width;
            mHeight = height;
            mBitRate = bitRate;
            mEglContext = sharedEglContext;
            mProgramType = programType;
            mFrameRate = frameRate;
        }

    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public boolean startRecording(EncoderConfig config, long mRecordingStartTime) {
        Log.d(TAG, "Encoder: startRecording()");
        synchronized (mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return false;
            }
            this.mRecordingStartTime = mRecordingStartTime;
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
        return true;
    }


    public void releaseRecording() {
        if (mHandler != null) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_RELEASE_RECORDING));
            mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        }
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopRecording() {
        if (mHandler != null) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
            mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        }
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    public void frameAvailable(SurfaceTexture st) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        if (mHandler == null) {
            return;
        }

        float[] transform = new float[16];      // TODO - avoid alloc every frame
        st.getTransformMatrix(transform);
        /*
        long timestamp = st.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }
        */

        long frameTime = System.currentTimeMillis();
        if (mVideoEncoder != null && (frameTime - mLastFrameTime) >= getFrameInterval())
        {
           Log.d(TAG, " get frame interval :" + getFrameInterval());
            // encode data at least in every 50 milliseconds, it measn 20fps or less
            long timestamp = (frameTime - mRecordingStartTime)
                    * 1000000; // convert it to nano seconds
            mLastFrameTime = frameTime;
            mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                    (int) (timestamp >> 32), (int) timestamp, transform));
        }
    }

    private long getFrameInterval() {
        return 1000 / mEncoderConfig.mFrameRate;
    }

    public void setFrameRate(int framerate) {
        if (mEncoderConfig != null) {
            mEncoderConfig.mFrameRate = framerate;
        }
    }

    public int getFrameRate() {
        return mEncoderConfig != null ? mEncoderConfig.mFrameRate : 0;
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }

    public void setEffect(Texture2dProgram.ProgramType programType) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CHANGE_EFFECT, 0, 0, programType));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {


        private WeakReference<TextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<TextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording(true);
                    break;
                case MSG_RELEASE_RECORDING:
                    encoder.handleStopRecording(false);
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_CHANGE_EFFECT:
                    encoder.changeEffect((Texture2dProgram.ProgramType) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    System.out.println("looper msg quit....");
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    private void changeEffect(Texture2dProgram.ProgramType type) {
        if (mFullScreen != null) {
            //// TODO: 25.04.2016 try with true parameter
            mFullScreen.release(false);
        }
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(type));
        mProgramType = type;

    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        Log.d(TAG, "handleStartRecording " + config);
        this.mEncoderConfig = config;
        mFrameNum = 0;
        prepareEncoder(config.mEglContext, config.mWidth, config.mHeight, config.mBitRate, config.mFrameRate,
                config.writerHandler, config.mProgramType);
    }

    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {
        if (VERBOSE) Log.d(TAG, "handleFrameAvailable tr=" + transform);
        if (mFullScreen != null) {
            mVideoEncoder.drainEncoder(false);
            mFullScreen.drawFrame(mTextureId, transform);

            //   drawBox(mFrameNum++);

            mInputWindowSurface.setPresentationTime(timestampNanos);
            mInputWindowSurface.swapBuffers();
        }
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording(boolean stopMuxer) {
        Log.d(TAG, "handleStopRecording");
        mVideoEncoder.drainEncoder(true);
        releaseEncoder();
        if (stopMuxer) {
            mVideoEncoder.stopMuxer();
        }
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        //Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
    }

    /**
     * Tears down the EGL surface and activity we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new activity.
     * <p>
     * This is useful if the old activity we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Log.d(TAG, "handleUpdatedSharedContext " + newSharedContext);

        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new activity.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(mProgramType));
    }

    private void prepareEncoder(EGLContext sharedContext, int width, int height, int bitRate, int frameRate,
                                IMediaMuxer writerHandle, Texture2dProgram.ProgramType programType)
            throws IllegalStateException
    {
        try {
            mVideoEncoder = new VideoEncoderCore(width, height, bitRate, frameRate, writerHandle);

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

        mProgramType = programType;
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(programType));

    }

    private void releaseEncoder() {
        mVideoEncoder.release();
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    /**
     * Draws a box, with position offset.
     */
    private void drawBox(int posn) {
        final int width = mInputWindowSurface.getWidth();
        int xpos = (posn * 4) % (width - 50);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, 100, 100);
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
