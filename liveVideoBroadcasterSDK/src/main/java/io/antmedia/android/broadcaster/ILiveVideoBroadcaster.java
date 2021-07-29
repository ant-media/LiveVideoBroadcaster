package io.antmedia.android.broadcaster;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

/**
 * Created by mekya on 29/03/2017.
 */

public interface ILiveVideoBroadcaster {


    void init(Activity activity, int bitrate, int framerate);

    /**
     * Checks microphone permissions are granted
     * @return true if permissions are granted
     * false if permissions are not granted
     */
    boolean isPermissionGranted();


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

    void setMediaProjection(MediaProjection mediaProjection, int densityDpi, int widthPixels, int heightPixels);
}
