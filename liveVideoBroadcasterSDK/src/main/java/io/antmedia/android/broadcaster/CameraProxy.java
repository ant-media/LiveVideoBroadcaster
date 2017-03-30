package io.antmedia.android.broadcaster;

/**
 * Created by faraklit on 13.06.2016.
 */

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

public class CameraProxy {
    private static final String TAG = "CameraProxy";

    private static final int RELEASE = 1;
    private static final int AUTOFOCUS = 2;
    private static final int CANCEL_AUTOFOCUS = 3;
    private static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 4;
    private static final int SET_PARAMETERS = 5;
    private static final int START_SMOOTH_ZOOM = 6;
    private static final int ADD_CALLBACK_BUFFER = 7;
    private static final int SET_ERROR_CALLBACK = 8;
    private static final int SET_PREVIEW_DISPLAY = 9;
    private static final int START_PREVIEW = 10;
    private static final int STOP_PREVIEW = 11;
    private static final int OPEN_CAMERA = 12;
    private static final int SET_DISPLAY_ORIENTATION = 13;
    private static final int SET_PREVIEW_TEXTURE = 14;
    private final HandlerThread ht;

    private Camera _camera;
    private final CameraHandler _handler;
    private final ConditionVariable _signal = new ConditionVariable();
    private volatile Camera.Parameters _parameters;
    private boolean released = false;

    public CameraProxy(int cameraId) {
        ht = new HandlerThread("Camera Proxy Thread");
        ht.start();

        _handler = new CameraHandler(ht.getLooper());
        _signal.close();
        _handler.obtainMessage(OPEN_CAMERA, cameraId, 0).sendToTarget();
        _signal.block();
        if (_camera != null) {
            _handler.obtainMessage(SET_ERROR_CALLBACK, new ErrorCallback()).sendToTarget();
        }
    }

    public boolean isCameraAvailable() {
        return _camera != null && !isReleased();
    }

    public void release() {
        released = true;
        _signal.close();
        _handler.sendEmptyMessage(RELEASE);
        _signal.block();
        ht.quitSafely();

    }

    public void autoFocus(Camera.AutoFocusCallback callback) {
        _handler.obtainMessage(AUTOFOCUS, callback).sendToTarget();
    }

    public void cancelAutoFocus() {
        _handler.sendEmptyMessage(CANCEL_AUTOFOCUS);
    }

    public void setPreviewCallbackWithBuffer(Camera.PreviewCallback callback) {
        _handler.obtainMessage(SET_PREVIEW_CALLBACK_WITH_BUFFER, callback).sendToTarget();
    }

    public Camera.Parameters getParameters() {
        return _parameters;
    }

    public void setParameters(Camera.Parameters parameters) {
        _parameters = parameters;
        _handler.obtainMessage(SET_PARAMETERS, parameters).sendToTarget();
    }

    public void startSmoothZoom(int level) {
        _handler.obtainMessage(START_SMOOTH_ZOOM, level, 0).sendToTarget();
    }

    public void addCallbackBuffer(byte[] buffer) {
        _handler.obtainMessage(ADD_CALLBACK_BUFFER, buffer).sendToTarget();
    }

    public void setPreviewDisplay(SurfaceHolder holder) {
        _signal.close();
        _handler.obtainMessage(SET_PREVIEW_DISPLAY, holder).sendToTarget();
        _signal.block();
    }

    public void startPreview() {
        _handler.sendEmptyMessage(START_PREVIEW);
    }

    public void stopPreview() {
        _signal.close();
        _handler.sendEmptyMessage(STOP_PREVIEW);
        _signal.block();
    }

    public void setDisplayOrientation(int displayOrientation) {
        _handler.obtainMessage(SET_DISPLAY_ORIENTATION, displayOrientation, 0).sendToTarget();
    }

    public void setPreviewTexture(SurfaceTexture previewTexture) {
        _handler.obtainMessage(SET_PREVIEW_TEXTURE, previewTexture).sendToTarget();
    }

    public boolean isReleased() {
        return released;
    }


    private class CameraHandler extends Handler {
        public CameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            try {
                switch (msg.what) {
                    case OPEN_CAMERA:
                        _camera = Camera.open(msg.arg1);
                        _parameters = _camera.getParameters();
                        break;

                    case SET_DISPLAY_ORIENTATION:
                        _camera.setDisplayOrientation(msg.arg1);
                        break;

                    case RELEASE:
                        _camera.release();
                        break;

                    case AUTOFOCUS:
                        _camera.autoFocus((Camera.AutoFocusCallback)msg.obj);
                        break;

                    case CANCEL_AUTOFOCUS:
                        _camera.cancelAutoFocus();
                        break;

                    case SET_PREVIEW_TEXTURE:
                        _camera.setPreviewTexture((SurfaceTexture) msg.obj);
                        break;

                    case SET_PARAMETERS:
                        _camera.setParameters((Camera.Parameters)msg.obj);
                        break;

                    case START_SMOOTH_ZOOM:
                        _camera.startSmoothZoom(msg.arg1);
                        break;

                    case ADD_CALLBACK_BUFFER:
                        _camera.addCallbackBuffer((byte[])msg.obj);
                        break;

                    case SET_ERROR_CALLBACK:
                        _camera.setErrorCallback((Camera.ErrorCallback)msg.obj);
                        break;

                    case SET_PREVIEW_DISPLAY:
                        _camera.setPreviewDisplay((SurfaceHolder)msg.obj);
                        break;

                    case START_PREVIEW:
                        _camera.startPreview();
                        break;

                    case STOP_PREVIEW:
                        _camera.stopPreview();
                        break;

                    default:
                        Log.e(TAG, "Invalid message: " + msg.what);
                        break;
                }
            }
            catch (RuntimeException e) {
                handleException(msg, e);
            }
            catch (IOException e) {
                handleException(msg, new RuntimeException(e.getMessage(), e));
            }

            _signal.open();
        }

        private void handleException(Message msg, RuntimeException e) {
            Log.e(TAG, "Camera operation failed", e);

            if (msg.what != RELEASE && _camera != null) {
                try {
                    released = true;
                    _camera.release();
                }
                catch (Exception e2) {
                    Log.e(TAG, "Failed to release camera on error", e);
                }
            }

           // throw e;
        }
    }

    private static class ErrorCallback implements Camera.ErrorCallback {
        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "Got camera error callback. error=" + error);
        }
    }
}