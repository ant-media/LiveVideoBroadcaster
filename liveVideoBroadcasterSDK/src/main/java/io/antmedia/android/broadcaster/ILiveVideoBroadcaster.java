package io.antmedia.android.broadcaster;

import android.app.Activity;
import android.opengl.GLSurfaceView;

import java.util.ArrayList;

import io.antmedia.android.broadcaster.utils.Resolution;

/**
 * Created by mekya on 29/03/2017.
 */

public interface ILiveVideoBroadcaster {

    /**
     * Initializes video broadcaster
     * @param activity
     * the activity which is using this service
     * @param mGLView
     * the GLSurfaceView which is used to render camera view
     */
    void init(Activity activity, GLSurfaceView gLView);

    /**
     * Checks whether camera and microphone permissions are granted
     * @return true if permissions are granted
     * false if permissions are not granted
     */
    boolean isPermissionGranted();

    /**
     * Request for missiong permissions
     * Camera and microphone permissions are required
     */
    void requestPermission();

    /**
     * Opens camera in an another thread and render camera view on GLSurfaceView
     * @param cameraId specifies which camera to open
     *                 can be
     *                 Camera.CameraInfo.CAMERA_FACING_BACK, Camera.CameraInfo.CAMERA_FACING_FRONT;
     *
     */
    void openCamera(int cameraId);

    /**
     * Changes the camera,
     * if active camera is back camera, releases the back camera and
     * open the front camera, it behaves same with the front camera
     */
    void changeCamera();


    /**
     * Set adaptive streaming enable or disable
     *
     * @param enable, if true , adaptive streaming is enabled, defaults false
     */
    void setAdaptiveStreaming(boolean enable);

    /**
     * Set the resolution of the active camera
     * @param size
     */
    void setResolution(Resolution size);


    /**
     * @return the supported preview sizes of the active camera
     */
    ArrayList<Resolution> getPreviewSizeList();

    /**
     *
     * @return current preview size of the active camera
     */
    Resolution getPreviewSize();

    /**
     * Sets the display orientation of the camera for portrait or landscape orientation
     */
    void setDisplayOrientation();

    /**
     * Pauses and releases the camera, it is safe to call this function in OnPause of the activity
     */
    void pause();

    /**
     *
     * @return true if broadcasting is active and app is connected to server
     * false if it is not connected or connection is dropped
     */
    boolean isConnected();

    /**
     * Starts broadcasting the specified url
     * @param url the rtmp url which should be in form rtmp://SERVER_ADDRESS/APP_NAME/STREAM_NAME
     * @return true if it starts broadcasting successfully,
     * false if something is wrong and cannot start
     */
    boolean startBroadcasting(String url);

    /**
     * Stops broadcastings to the server
     */
    void stopBroadcasting();


}
