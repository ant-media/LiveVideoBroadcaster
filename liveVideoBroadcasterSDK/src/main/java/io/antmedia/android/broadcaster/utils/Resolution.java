package io.antmedia.android.broadcaster.utils;

import java.io.Serializable;

/**
 * Created by mekya on 28/03/2017.
 */

public class Resolution implements Serializable
{
    public final int width;
    public final int height;

    public Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }
	
    public static Resolution newLikeDeviceResolution(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (window.getDefaultDisplay() == null) {
            Log.e("Resolution", "Window manager not found return default res 1280x720");
            return new Resolution(1280, 720);
        }
        window.getDefaultDisplay().getMetrics(metrics);
        int deviceWidth = metrics.widthPixels;
        int deviceHeight = metrics.heightPixels;
        if (metrics.widthPixels < metrics.heightPixels) {
            deviceWidth = metrics.heightPixels;
            deviceHeight = metrics.widthPixels;
        }
        return new Resolution(deviceWidth, deviceHeight);
    }
}