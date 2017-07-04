/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.antmedia.android.broadcaster.network.IMediaMuxer;

/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {
    private static final String TAG = VideoEncoderCore.class.getSimpleName();
    private static final boolean VERBOSE = false;

    // TODO: these ought to be configurable as well
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    //private int frameRate = 20;               // 20fps
    private static final int IFRAME_INTERVAL = 2;           // 2 seconds between I-frames
    private IMediaMuxer mWriterHandler;

    private Surface mInputSurface;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;
    private boolean mMuxerStarted;
    private Map<Integer, Object> reservedBuffers = new HashMap<Integer, Object>();


    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate, int frameRate, IMediaMuxer writerHandler)
            throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

       // this.frameRate = frameRate;

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mWriterHandler = writerHandler;

    }


    public static boolean doesEncoderWork(int width, int height, int bitRate, int frameRate) {

        boolean success = false;
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        MediaCodec mEncoder = null;
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface mInputSurface = mEncoder.createInputSurface();
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        finally {
            mEncoder.release();
        }

        return success;
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, swipe_right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        if (mEncoder == null) {
            return;
        }
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
                Log.d("VideoEncoder", "INFO_OUTPUT_BUFFERS_CHANGED");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                byte[] config = new byte[sps.limit() + pps.limit()];
                sps.get(config, 0, sps.limit());
                pps.get(config, sps.limit(), pps.limit());

                mWriterHandler.writeVideo(config, config.length, 0);

                Log.d(TAG, "encoder output format changed: " + newFormat);

                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it

            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    long presentationTimeInMillis = mBufferInfo.presentationTimeUs/1000;  //convert it to milliseconds
                    //first it should be divided to 1000 and assign value to a long
                    //then cast it to int -
                    // Othe wise after about 35 minutes(exceeds integer max size) presentationTime will be negative
                    //in this assignment int presetationTime = (int)mBufferInfo.presentationTimeUs/1000;
                    int presetationTime = (int)presentationTimeInMillis;
                    byte[] data = getBuffer(mBufferInfo.size, mWriterHandler.getLastVideoFrameTimeStamp(), presetationTime);
                    encodedData.get(data, 0, mBufferInfo.size);
                    encodedData.position(mBufferInfo.offset);

                    mWriterHandler.writeVideo(data, mBufferInfo.size, presetationTime);
                }
                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached");
                    }
                    reservedBuffers.clear();
                    break;      // out of while
                }
            }
        }
    }

    private byte[] getBuffer(int size, int lastSentFrameTimestamp, int currentTimeStamp)
    {
        /**
         * how does it work?
         * we put byte array with their timestamp value to a hash map
         * when there is a new output buffer array, we check the last frame timestamp of mediamuxer
         * if the byte buffer timestamp is less than the value of last frame timestamp of mediamuxer
         * it means that we can use that byte buffer again because it is already written to network
         */
        Iterator<Map.Entry<Integer, Object>> iterator = reservedBuffers.entrySet().iterator();

        while(iterator.hasNext()) {
            Map.Entry<Integer, Object> next = iterator.next();
            if (next.getKey() <= lastSentFrameTimestamp)
            {
                // it means this frame is sent
                byte[] value = (byte[]) next.getValue();
                iterator.remove();
                if (value.length >= size)
                {
                    reservedBuffers.put(currentTimeStamp, value);
                    return value;
                }
                // if byte array length is not bigger than requested size,
                // we let this array to soft hands of GC
            }
        }

        // no eligible data found, create a new byte
        byte[] data = new byte[size];
        reservedBuffers.put(currentTimeStamp, data);
        return data;
    }

    public void stopMuxer() {
        if (mWriterHandler != null) {
            mWriterHandler.stopMuxer();
            mWriterHandler = null;
        }
    }
}
