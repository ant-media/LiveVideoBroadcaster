package io.antmedia.android.broadcaster.network;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;


import net.butterflytv.rtmp_client.RTMPMuxer;

import java.util.ArrayList;
import java.util.Iterator;

import io.antmedia.android.broadcaster.network.IMediaMuxer;

/**
 * Created by faraklit on 09.02.2016.
 */
public class RTMPStreamer extends Handler implements IMediaMuxer  {


    private static final boolean DEBUG = false;
    private static final String TAG = RTMPStreamer.class.getSimpleName();
    RTMPMuxer rtmpMuxer = new RTMPMuxer();

    public int frameCount;
    public  int result = 0;
    private int lastVideoFrameTimeStamp;
    private int lastAudioFrameTimeStamp;
    private int mLastReceivedVideoFrameTimeStamp = -1;
    private int mLastReceivedAudioFrameTimeStamp = -1;
    private int lastSentFrameTimeStamp = -1;
    private Object frameSynchronized = new Object();
    private boolean isConnected = false;

    public class Frame {
        byte[] data;
        int timestamp;
        int length;

        public Frame(byte[] data, int length, int timestamp) {
            this.data = data;
            this.length = length;
            this.timestamp = timestamp;
        }
    }

    private ArrayList<Frame> audioFrameList = new ArrayList<>();
    private ArrayList<Frame> videoFrameList = new ArrayList<>();


    public RTMPStreamer(Looper looper) {
        super(looper);
        mLastReceivedVideoFrameTimeStamp = -1;
        mLastReceivedAudioFrameTimeStamp = -1;
        lastSentFrameTimeStamp = -1;
    }

    public int getLastReceivedVideoFrameTimeStamp() {
        return mLastReceivedVideoFrameTimeStamp;
    }

    public int getLastReceivedAudioFrameTimeStamp() {
        return mLastReceivedAudioFrameTimeStamp;
    }

    public int getLastSentFrameTimeStamp() {
        return lastSentFrameTimeStamp;
    }

    /**
     *
     * @param url of the stream
     */
    public boolean open(String url) {
        frameCount = 0;
        lastVideoFrameTimeStamp = 0;
        lastAudioFrameTimeStamp = 0;
        mLastReceivedVideoFrameTimeStamp = -1;
        mLastReceivedAudioFrameTimeStamp = -1;
        lastSentFrameTimeStamp = -1;
        isConnected = false;
        int result = rtmpMuxer.open(url, 0, 0);

        if (result > 0) {
            //    file_open("/mnt/sdcard/stream.flv" + (int) Math.random() * 1000);
            //    writeFLVHeader(true, true);
            isConnected = true;
        }
        return isConnected;
    }

    public void close() {
        Log.i(TAG, "close rtmp connection");
        isConnected = false;
        rtmpMuxer.close();
    }

    /**
     * It is critically important to send the frames in time order.
     * If an audio packet's timestamp is before to any video packet timestamp,
     * connection can be closed by server. So we make packet ordering below according packet's timestamp
     *
     * @param msg
     */
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case SEND_AUDIO: {
                /**
                 * msg.obj aac data,
                 * msg.arg1 length of the data
                 * msg.arg2 timestamp
                 */

                if ((msg.arg2 >= mLastReceivedAudioFrameTimeStamp) && (msg.arg1 > 0)) {
                    //some initial frames(decoder params) may be equal to previos ones
                    // add packet if the new frame timestamp is bigger than the last frame
                    // otherwise discard the packet. If we don't discard it, rtmp connection totally drops
                    mLastReceivedAudioFrameTimeStamp = msg.arg2;
                    audioFrameList.add(new Frame((byte[]) msg.obj, msg.arg1, msg.arg2));
                }
                else {
                    Log.w(TAG, "discarding audio packet because time stamp is older than last packet or data lenth equal to zero");
                }
                sendFrames();
            }
            break;
            case SEND_VIDEO: {

                /**
                 * msg.obj h264 nal unit,
                 * msg.arg1 length of the data
                 * msg.arg2 timestamp
                 */
                if ((msg.arg2 >= mLastReceivedVideoFrameTimeStamp) && (msg.arg1 > 0)) {
                    //some initial frames(decoder params) may be equal to previous ones
                    // add packet if the new frame timestamp is bigger than the last frame
                    // otherwise discard the packet. If we don't discard it, rtmp connection totally drops
                    mLastReceivedVideoFrameTimeStamp = msg.arg2;
                    videoFrameList.add(new Frame((byte[]) msg.obj, msg.arg1, msg.arg2));
                }
                else {

                    Log.w(TAG, "discarding videp packet because time stamp is older  than last packet or data lenth equal to zero");
                }
                sendFrames();
            }
            break;
            case STOP_STREAMING:
                finishframes();
                close();
                break;
        }



    }

    private void finishframes()
    {
        int videoFrameListSize, audioFrameListSize;
        do {
            sendFrames();

            videoFrameListSize = videoFrameList.size();
            audioFrameListSize = audioFrameList.size();
            //one of the frame list should be exhausted while the other have frames
        } while ((videoFrameListSize > 0) && (audioFrameListSize > 0));

        if (videoFrameListSize > 0) {
            //send all video frames remained in the list
            sendVideoFrames(videoFrameList.get(videoFrameListSize - 1).timestamp);
        }
        else if (audioFrameListSize > 0) {
            //send all audio frames remained in the list
            sendAudioFrames(audioFrameList.get(audioFrameListSize - 1).timestamp);
        }

    }

    private void sendFrames() {
        // this is a simple sorting algorithm.
        // we do not know the audio or video frames timestamp in advance and they are not
        // deterministic. So we send video frames with the timestamp is less than the first one in the list
        // and the same algorithm applies for audio frames.
        int listSize = videoFrameList.size();
        if (listSize > 0) {
            sendAudioFrames(videoFrameList.get(0).timestamp);
        }

        listSize = audioFrameList.size();
        if (listSize > 0) {
            sendVideoFrames(audioFrameList.get(0).timestamp);
        }
    }

    private void sendAudioFrames(int timestamp) {
        Iterator<Frame> iterator = audioFrameList.iterator();
        while (iterator.hasNext())
        {
            Frame audioFrame = iterator.next();
            if (audioFrame.timestamp <= timestamp)
            {
                // frame time stamp should be equal or less than the previous timestamp
                // in some cases timestamp of audio and video frames may be equal
                if (audioFrame.timestamp >= lastSentFrameTimeStamp) {
                    if (audioFrame.timestamp == lastSentFrameTimeStamp) {
                        audioFrame.timestamp++;
                    }
                    if (isConnected) {
                        int result = rtmpMuxer.writeAudio(audioFrame.data, 0, audioFrame.length, audioFrame.timestamp);

                        if (DEBUG) {
                            Log.d(TAG, "send audio result: " + result + " time:" + audioFrame.timestamp + " length:" + audioFrame.length);
                        }

                        if (result < 0) {
                            close();
                        }
                    }
                    lastAudioFrameTimeStamp = audioFrame.timestamp;
                    lastSentFrameTimeStamp = audioFrame.timestamp;
                    synchronized (frameSynchronized) {
                        frameCount--;
                    }
                }
                iterator.remove();
            }
            else {
                //if timestamp is bigger than the auio frame timestamp
                //it will be sent later so break the loop
                break;
            }
        }
    }

    private void sendVideoFrames(int timestamp) {
        Iterator<Frame> iterator = videoFrameList.iterator();
        while (iterator.hasNext()) {
            Frame frame = iterator.next();
            if ((frame.timestamp <= timestamp))
            {
                // frame time stamp should be equal or less than timestamp
                // in some cases timestamp of audio and video frames may be equal
                if (frame.timestamp >= lastSentFrameTimeStamp) {
                    if (frame.timestamp == lastSentFrameTimeStamp) {
                        frame.timestamp++;
                    }
                    if (isConnected) {
                        int result = rtmpMuxer.writeVideo(frame.data, 0, frame.length, frame.timestamp);
                        if (DEBUG) {
                            Log.d(TAG, "send video result: " + result + " time:" + frame.timestamp + " length:" + frame.length);
                        }
                        if (result < 0) {
                            close();
                        }
                    }
                    lastVideoFrameTimeStamp = frame.timestamp;
                    lastSentFrameTimeStamp = frame.timestamp;
                    synchronized (frameSynchronized) {
                        frameCount--;
                    }
                }

                iterator.remove();
            }
            else {
                //if frame timestamp is not smaller than the timestamp
                // break the loop, it will be sent later
                break;
            }
        }
    }

    public int getLastAudioFrameTimeStamp() {
        return lastAudioFrameTimeStamp;
    }

    public int getLastVideoFrameTimeStamp() {
        return lastVideoFrameTimeStamp;
    }

    public void writeFLVHeader(boolean hasAudio, boolean hasVideo) {
        rtmpMuxer.write_flv_header(hasAudio, hasVideo);
    }

    public void file_open(String s) {
        rtmpMuxer.file_open(s);
    }


    public void file_close() {
        rtmpMuxer.file_close();
    }

    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void writeAudio(byte[] data, int size, int presentationTime) {
        Message message = obtainMessage(IMediaMuxer.SEND_AUDIO, data);
        message.arg1 = size;
        message.arg2 = presentationTime;
        sendMessage(message);
        synchronized (frameSynchronized) {
            frameCount++;
        }
        if (DEBUG) Log.d(TAG, "writeAudio size: " + size + " time:" + presentationTime);
    }

    @Override
    public void writeVideo(byte[] data, int length, int presentationTime) {
        Message message = obtainMessage(IMediaMuxer.SEND_VIDEO, data);
        message.arg1 = length;
        message.arg2 = presentationTime;
        sendMessage(message);
        synchronized (frameSynchronized) {
            frameCount++;
        }

        if (DEBUG) Log.d(TAG, "writeVideo size: " + length + " time:" + presentationTime);
    }

    @Override
    public void stopMuxer() {
        sendEmptyMessage(RTMPStreamer.STOP_STREAMING);
    }

    @Override
    public int getFrameCountInQueue() {
        synchronized (frameSynchronized) {
            return frameCount;
        }
    }

    public int getVideoFrameCountInQueue() {
        synchronized (frameSynchronized) {
            return videoFrameList.size();
        }
    }
}

