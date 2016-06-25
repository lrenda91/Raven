package it.polito.mec.video.raven.sender.encoding;

import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import it.polito.mec.video.raven.VideoChunks;
import it.polito.mec.video.raven.sender.Util;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class MediaCodecEncoderThread implements Runnable {

    private static final String TAG = "ENCODER";
    private static final boolean VERBOSE = false;

    public interface Listener {
        void onCodecStarted(int width, int height, int bitRate, int frameRate);
        void onCodecSpecificData(VideoChunks.Chunk chunk, int width, int height, int bitRate, int frameRate);
        void onCodecEncodedData(VideoChunks.Chunk chunk);
    }

    //private static final int TIMEOUT_US = -1;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 25;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 500000;

    private Thread mWorkerThread;
    private Listener mListener;
    private VideoChunks mRawFrames = new VideoChunks();
    private int mWidth, mHeight;

    //public Set<Pair<Handler,EncodingListener>> mEncodeListeners = new HashSet<>();

    /*public EncodingListener getListener(){
        return mListener;
    }*/
    public MediaCodecEncoderThread(Listener listener){
        mListener = listener;
    }

    public void startThread(int w, int h){
        if (mWorkerThread != null){
            return;
        }
        mWidth = w;
        mHeight = h;
        mWorkerThread = new Thread(this);
        mWorkerThread.start();
    }

    public void requestStop(){
        if (mWorkerThread == null){
            return;
        }
        mWorkerThread.interrupt();
    }

    public boolean waitForTermination(){
        if (mWorkerThread == null){
            return true;
        }
        boolean result = true;
        try{
            mWorkerThread.join();
        } catch(InterruptedException e){
            result = false;
        }
        mRawFrames.clear();
        mWorkerThread = null;
        return result;
    }

    public boolean isRunning(){
        return mWorkerThread != null;
    }

    /*
    public void setListener(EncodingListener mListener) {
        this.mListener = mListener;
    }*/

    public void submitAccessUnit(byte[] data){
        mRawFrames.addChunk(data, 0, 0);
    }

    @Override
    public void run() {
        MediaCodec encoder = null;
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        //Log.d(TAG, codecInfo.toString());
        int colorFormat = Util.getEncoderColorFormat(ImageFormat.NV21);
        Util.logColorFormat(TAG, colorFormat);
        //int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        //selectColorFormat(codecInfo, MIME_TYPE);
        if (colorFormat == 0){
            Log.e(TAG,"couldn't find a good color format for " + codecInfo.getName() + " / " + MIME_TYPE);
            return;
        }

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_BPS);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        try{
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return;
        }

        if (mListener != null){
            mListener.onCodecStarted(mWidth, mHeight, BIT_RATE_BPS, FRAME_RATE);
        }

        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        long framesCounter = 0, encodedSize = 0;

        boolean inputDone = false;
        boolean outputDone = false;
        int inputStatus = -1, outputStatus = -1;

        Log.d(TAG, "Encoder started");
        while (!Thread.interrupted() && !outputDone) {
            if (!inputDone) {
                //if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
                inputStatus = encoder.dequeueInputBuffer(10000);
                if (inputStatus < 0){
                    //Log.e(TAG, "Unknown input buffer status: "+inputStatus);
                    continue;
                }

                long pts = computePresentationTime(framesCounter);
                ByteBuffer inputBuf = encoderInputBuffers[inputStatus];
                inputBuf.clear();
                int bufferLength = 0;
                int flags = 0;
                if (VERBOSE) Log.d(TAG, "Waiting for new access unit from camera...");
                VideoChunks.Chunk chunk = mRawFrames.getNextChunk();
                if (chunk == null){
                    if (VERBOSE) Log.d(TAG, "Cancelling thread...");
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    inputDone = true;
                    if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                    encoder.queueInputBuffer(inputStatus, 0, bufferLength, pts, flags);
                }
                else{
                    byte[] previewData = chunk.data;
                    if (inputBuf.remaining() >= previewData.length) {

                        bufferLength = previewData.length;
                        if (VERBOSE) Log.d(TAG, "buf remaining()=" + inputBuf.remaining() + " ,size=" + previewData.length);
                        inputBuf.put(previewData);
                        encoder.queueInputBuffer(inputStatus, 0, bufferLength, pts, flags);
                    }
                }
                framesCounter++;
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (VERBOSE) Log.i(TAG, "Waiting for output buffer");
            outputStatus = encoder.dequeueOutputBuffer(info, 10000);
            if (outputStatus == MediaCodec.INFO_TRY_AGAIN_LATER){
                continue;
            }
            else if (outputStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
                if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
            } else if (outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
                ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
            } else if (outputStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + outputStatus);
            } else { // outputStatus >= 0
                ByteBuffer encodedData = encoderOutputBuffers[outputStatus];

                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);
                encodedSize += info.size;

                //publish result to the caller
                byte[] encodedArray = new byte[encodedData.remaining()]; //converting bytebuffer to byte array
                encodedData.get(encodedArray);
                final VideoChunks.Chunk chunk =
                        new VideoChunks.Chunk(encodedArray, info.flags, info.presentationTimeUs);

                boolean isConfigData = ((chunk.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
                if (isConfigData && mListener != null) {
                    if (VERBOSE){
                        String s = "[ "; for (byte b : encodedArray) s+=""+b+" "; s+="]";
                        Log.d(TAG, "config data: "+s);
                    }
                    mListener.onCodecSpecificData(chunk, mWidth, mHeight, BIT_RATE_BPS, FRAME_RATE);
                }
                else if (mListener != null){
                    mListener.onCodecEncodedData(chunk);
                }

                if (VERBOSE)
                    Log.d(TAG, "Encoded buffer size: "+info.size+" " +
                        "TOTAL Encoded size: "+encodedSize);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (VERBOSE) Log.i(TAG, "First coded packet ");
                } else {
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (VERBOSE) if (outputDone) Log.i(TAG, "Last coded packet ");
                }
                encoder.releaseOutputBuffer(outputStatus, false);
            }
        }
        encoder.stop();
        encoder.release();
        Log.i(TAG, "Encoder Released!! Closing.");
    }



    private static MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecInfo res = null;
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    if (types[j].equalsIgnoreCase(mimeType)) {
                        Log.d("CODECINFO", codecInfo.toString());
                        return codecInfo;
                        //res = codecInfo;
                    }
                }
            }
        }
        return null;
        //return res;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int idx=-1;
        String tag = "COLOR";
        for (int i=0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            switch (colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    Log.d(tag, "COLOR_FormatYUV420Planar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                    Log.d(tag, "COLOR_FormatYUV420PackedPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    Log.d(tag, "COLOR_FormatYUV420SemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                    Log.d(tag, "COLOR_FormatYUV420PackedSemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                    Log.d(tag, "COLOR_TI_FormatYUV420PackedSemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    Log.d(tag, "COLOR_FormatYUV420Flexible");// break;

                    return colorFormat;
            }
            idx=i;
        }
        if (idx >= 0){
            return capabilities.colorFormats[idx];
        }
        return 0;
    }

    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}
