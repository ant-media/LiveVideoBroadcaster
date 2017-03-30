package io.antmedia.android.broadcaster.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class Utils {

    public static final String APP_SHARED_PREFERENCES = "applicationDetails";
    private static final String DOES_ENCODER_WORKS = Utils.class.getName() + ".DOES_ENCODER_WORKS";


    public static final int ENCODER_NOT_TESTED = -1;
    public static final int ENCODER_WORKS = 1;
    public static final int ENCODER_NOT_WORKS = 0;


    //public static final String SHARED_PREFERENCE_FIRST_INSTALLATION="FIRST_INSTALLATION";
    private static SharedPreferences sharedPreference = null;



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

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        if (sharedPreference == null) {
            sharedPreference = context.getSharedPreferences(APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        }
        return sharedPreference;
    }


    public static int doesEncoderWorks(Context context) {
        return getDefaultSharedPreferences(context).getInt(DOES_ENCODER_WORKS, ENCODER_NOT_TESTED);
    }

    public static void setEncoderWorks(Context context, boolean works) {
        SharedPreferences sharedPreferences = getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(DOES_ENCODER_WORKS, works ? ENCODER_WORKS : ENCODER_NOT_WORKS);
        editor.apply();
    }

}
