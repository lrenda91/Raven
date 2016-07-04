package it.polito.mec.video.raven.sender.record;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import it.polito.mec.video.raven.VideoChunks;
import it.polito.mec.video.raven.sender.encoding.Params;
import it.polito.mec.video.raven.sender.encoding.StreamSenderJNI;


/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class NativeRecorderImpl extends AbsCamcorder implements Camera1ManagerImpl.Callback {

    private static final String TAG = "x264EncodingThread";
    private static final boolean VERBOSE = false;

    private boolean mIsRecording = false, mIsPaused = false;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    public NativeRecorderImpl(Context context){
        super(context);
        mCameraManager = new Camera1ManagerImpl(context, this);
    }

    @Override
    public void openCamera() {
        super.openCamera();
        startBackgroundThread();
    }

    @Override
    public void onCameraCapturedFrame(final byte[] frame) {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                byte[] res = StreamSenderJNI.nativeDoEncode(
                        mCurrentParams.width(),
                        mCurrentParams.height(),
                        frame,
                        mCurrentParams.bitrate());

                if (res == null){
                    return;
                }
                final VideoChunks.Chunk chunk = new VideoChunks.Chunk(res,
                        FLAG_MEDIA_DATA,
                        System.currentTimeMillis());
                notifyEncodedChunkAvailable(chunk);
            }
        });
    }

    @Override
    public void onCameraPreviewSizeChanged(int width, int height) { }

    @Override
    public void startRecording() {
        if (!mIsPaused) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    /*StreamSenderJNI.nativeApplyParams(
                            mCurrentParams.width(),
                            mCurrentParams.height(),
                            mCurrentParams.bitrate()
                    );*/
                    byte[][] headers = StreamSenderJNI.nativeGetHeaders();
                    byte[] sps = headers[0], pps = headers[1];
                    byte[] merged = new byte[sps.length + pps.length];
                    System.arraycopy(sps, 0, merged, 0, sps.length);
                    System.arraycopy(pps, 0, merged, sps.length, pps.length);
                    final VideoChunks.Chunk chunk = new VideoChunks.Chunk(
                            merged,
                            FLAG_CODEC_SPECIFIC_DATA,
                            0);
                    notifyConfigHeaders(chunk, mCurrentParams);
                    if (VERBOSE) Log.d(TAG, "Config frame produced");
                }
            });
        }


        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
        mIsRecording = true;
        mIsPaused = false;
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyEncodingStarted(mCurrentParams);
                if (VERBOSE) Log.d(TAG, "Started");
            }
        });
    }

    @Override
    public void pauseRecording() {
        mCameraManager.disableFrameCapture();
        //remove all pending encoding frames callbacks -> prevent stalling on server sde!!
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mIsRecording = false;
        mIsPaused = true;
        notifyEncodingPaused();
        if (VERBOSE) Log.d(TAG, "Paused");
    }

    @Override
    public void stopRecording() {
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();
        //remove all pending encoding frames callbacks -> prevent stalling on server sde!!
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mIsPaused = false;
        mIsRecording = false;
        notifyEncodingStopped();
        if (VERBOSE) Log.d(TAG, "Stopped");
    }

    @Override
    public void switchToVideoQuality(final Params params){
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        final Size size = new Size(params.width(), params.height());
        try{
            mCameraManager.switchToSize(size);
            mCameraManager.startPreview();
        }
        catch(IllegalArgumentException e){
            Log.e(TAG, e.getMessage());
            return;
        }

        assert mBackgroundThread != null;
        synchronized (mBackgroundThread){
            try{
                while (mBackgroundHandler == null){
                    mBackgroundThread.wait();
                }
            }catch(InterruptedException e){}
        }
        mBackgroundHandler.removeCallbacksAndMessages(null);

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                mCurrentParams = params;

                StreamSenderJNI.nativeApplyParams(
                        mCurrentParams.width(),
                        mCurrentParams.height(),
                        mCurrentParams.bitrate()
                );
                notifyParamsChanged(mCurrentParams);
            }
        });

        if (mIsRecording){

            startRecording();
        }
    }

    @Override
    public void closeCamera() {
        mCameraManager.releaseCamera();
        stopBackgroundThread();
    }


    private void startBackgroundThread(){
        if (mBackgroundThread != null){
            throw new RuntimeException("HandlerThread still alive. Can't start it");
        }
        mBackgroundThread = new HandlerThread(TAG){
            @Override
            protected void onLooperPrepared() {
                synchronized (mBackgroundThread){
                    mBackgroundHandler = new Handler(Looper.myLooper());
                    mBackgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            StreamSenderJNI.nativeInitEncoder();
                            if (VERBOSE) Log.d(TAG, "nativeInit");
                        }
                    });
                    mBackgroundThread.notifyAll();
                }

            }
        };
        mBackgroundThread.start();
        if (VERBOSE) Log.d("REC", "HandlerThread started");
    }

    private void stopBackgroundThread(){
        if (mBackgroundThread == null){
            throw new RuntimeException("HandlerThread dead. Can't stop it");
        }
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                StreamSenderJNI.nativeReleaseEncoder();
                if (VERBOSE) Log.d(TAG, "nativeRelease");
                Looper.myLooper().quit();
            }
        });
        try{
            mBackgroundThread.join();
            if (VERBOSE) Log.d(TAG, "HandlerThread quit");
        }catch(InterruptedException e){ }
        mBackgroundHandler = null;
        mBackgroundThread = null;
    }

}
