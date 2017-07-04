package io.antmedia.android.broadcaster.encoder;

/**
 * Created by faraklit on 17.02.2016.
 */

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;


import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.antmedia.android.broadcaster.CameraHandler;
import io.antmedia.android.broadcaster.encoder.gles.FullFrameRect;
import io.antmedia.android.broadcaster.encoder.gles.Texture2dProgram;
import io.antmedia.android.broadcaster.network.IMediaMuxer;

/**
 * Renderer object for our GLSurfaceView.
 * <p>
 * Do not call any methods here directly from another thread -- use the
 * GLSurfaceView#queueEvent() call.
 */
public class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = CameraSurfaceRenderer.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private static final int RECORDER_CONFIG_CHANGED = 3;

    private CameraHandler mCameraHandler;
    private TextureMovieEncoder mVideoEncoder;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingEnabled;
    private int mRecordingStatus;
    private int mFrameCount;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;
    private IMediaMuxer mWriterHandler;
    private long mRecordingStartTime;
    private int bitrate;
    private int frameRate = 25;

    /**
     * Constructs CameraSurfaceRenderer.
     * <p>
     * @param cameraHandler Handler for communicating with UI thread
     * @param movieEncoder video encoder object
     */
    public CameraSurfaceRenderer(CameraHandler cameraHandler,
                                 TextureMovieEncoder movieEncoder) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;


        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;
        mFrameCount = -1;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;

    }

    private Texture2dProgram.ProgramType mEffectType = Texture2dProgram.ProgramType.TEXTURE_EXT ;

    public void setEffect(Texture2dProgram.ProgramType effectType) {
        this.mEffectType = effectType;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        if (mSurfaceTexture != null) {
            Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL activity is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
    }

    /**
     * Notifies the renderer that we want to stop or start recording.
     */
    public void startRecording(long recordingStartTime) {
        mRecordingEnabled = true;
        Log.d(TAG, "changeRecordingState: was " + mRecordingEnabled + " now " + mRecordingEnabled);
        //if (isRecording)
        {
            mRecordingStartTime = recordingStartTime;
        }
    }

    public void stopRecording(){
        mRecordingEnabled = false;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int width, int height) {
        Log.d(TAG, "setCameraPreviewSize");
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
        if (mIncomingHeight >= 720) {
            bitrate = 850000;
        } else if (mIncomingHeight >= 480) {
            bitrate = 550000;
        } else if (mIncomingHeight >= 360) {
            bitrate = 450000;
        } else if (mIncomingHeight >= 288) {
            bitrate = 350000;
        } else if (mIncomingHeight >= 240) {
            bitrate = 250000;
        } else //if (mIncomingHeight >= 144)
        {
            bitrate = 100000;
        }
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        // We're starting up or coming back.  Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mRecordingEnabled = mVideoEncoder.isRecording();
        if (mRecordingEnabled) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }

        // Set up the texture blitter that will be used for on-screen display.  This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(mEffectType));

        mVideoEncoder.setEffect(mEffectType);

        mTextureId = mFullScreen.createTextureObject();

        // Create a SurfaceTexture, with an external texture, in this EGL activity.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        System.out.println("//Tell the UI thread to enable the camera preview.");
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
        if (mVideoEncoder != null) {
            mVideoEncoder.setFrameRate(frameRate);
        }
    }

    public int getFrameRate() {
        return mVideoEncoder != null ? mVideoEncoder.getFrameRate() : 0;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (VERBOSE) Log.d(TAG, "onDrawFrame tex=" + mTextureId);
        boolean showBox = false;


        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture.updateTexImage();

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {

            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    Log.d(TAG, "START recording bitrate: "  +bitrate);
                {

                    // start recording
                   boolean started = mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(
                                    mWriterHandler, mIncomingWidth, mIncomingHeight, bitrate, frameRate, EGL14.eglGetCurrentContext(), mEffectType),
                            mRecordingStartTime);
                    if (started) {
                        mRecordingStatus = RECORDING_ON;
                    }
                    else {
                        mRecordingStatus = RECORDING_OFF;
                    }
                }
                break;
                case RECORDER_CONFIG_CHANGED:
                    mVideoEncoder.releaseRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_RESUMED:
                    Log.d(TAG, "RESUME recording");
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    // stop recording
                    Log.d(TAG, "STOP recording");
                    mVideoEncoder.stopRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        //
        // TODO: be less lame.
        mVideoEncoder.setTextureId(mTextureId);

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(mSurfaceTexture);

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Log.i(TAG, "Drawing before incoming texture size set; skipping");
            return;
        }

        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        // Draw the video frame.
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        // Draw a flashing box if we're recording.  This only appears on screen.
       /* showBox = (mRecordingStatus == RECORDING_ON);
        if (showBox && (++mFrameCount & 0x04) == 0) {
            drawBox();
        }
        */
    }

    /**
     * Draws a red box in the corner.
     */
    private void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 100, 100);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    public void setOptions(IMediaMuxer writerHandler) {
        mWriterHandler = writerHandler;
    }

    // this function should be called after incoming width and height changed
    public void recorderConfigChanged() {
        // pay attention to this function, it causes throwing an exception in some circumstance when
        // it is called in recording state
        mRecordingStatus = RECORDER_CONFIG_CHANGED;
    }

    public List<Texture2dProgram.ProgramType> getEffectList() {
        return Texture2dProgram.EFFECTS;
    }


}

