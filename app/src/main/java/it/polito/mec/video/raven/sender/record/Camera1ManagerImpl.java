package it.polito.mec.video.raven.sender.record;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import it.polito.mec.video.raven.sender.Util;


/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class Camera1ManagerImpl implements Camera1Manager {

    public interface Callback {
        void onCameraPreviewSizeChanged(int width, int height);
        void onCameraCapturedFrame(byte[] frame);
    }

    private static final String TAG = "CameraManager";
    private static final boolean VERBOSE = true;

    private static final int sRatioWidth = 4;
    private static final int sRatioHeight = 3;

    //by default, it is NV21
    public int mImageFormat = ImageFormat.NV21;

    private static final int mNumOfBuffers = 4;
    private byte[][] mBuffers;
    private int mCurrentBufferIdx = 0;
    private int mCurrentBuffersSize;
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data == null || data.length != mCurrentBuffersSize){
                Log.e(TAG, "Inconsistent camera buffer");
                return;
            }
            useNextCallbackBuffer();
            if (mCallback != null){
                mCallback.onCameraCapturedFrame(data);
            }
        }
    };

    private Context mContext;

    private Camera mCamera;
    private Callback mCallback;

    private List<Size> mSuitableSizes = new LinkedList<>();
    private int mSuitableSizesIndex = 0;

    public Camera1ManagerImpl(Context context, Callback callback){
        mContext = context;
        mCallback = callback;
    }

    @Override
    public void acquireCamera() {
        checkForCameraAcquisition(false);

        Camera.CameraInfo info = new Camera.CameraInfo();
        int frontCameraId = -1, backCameraId = -1, targetCameraId;
        for (int id=0; id < Camera.getNumberOfCameras(); id++){
            Camera.getCameraInfo(id, info);
            if ( (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                    && (backCameraId < 0) ){
                backCameraId = id;
            }
            else if ( (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    && (frontCameraId < 0) ){
                frontCameraId = id;
            }
        }
        //just prefer back camera, if available, otherwise, prefer front camera
        if (backCameraId >= 0){ targetCameraId = backCameraId; }
        else if (frontCameraId >= 0){ targetCameraId = frontCameraId; }
        else{
            Log.e(TAG, "Impossible to acquire camera. Shutting down "+TAG);
            throw new RuntimeException("Impossible to acquire camera. Shutting down "+TAG);
        }

        mCamera = Camera.open(targetCameraId);

        boolean isBackCamera = (targetCameraId == backCameraId);


        if (VERBOSE) Log.d(TAG, (isBackCamera ? "BACK" : "FRONT") +
                " Camera["+targetCameraId+"] acquired");

        Camera.Parameters parameters = mCamera.getParameters();
        List<Integer> supportedPreviewFrameRates = parameters.getSupportedPreviewFrameRates();
        int chosenFrameRate = 0;
        for (Integer fr : supportedPreviewFrameRates)
            if (fr > chosenFrameRate) chosenFrameRate = fr;

        parameters.setPreviewFrameRate(chosenFrameRate);

        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final int rotation = wm.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        Camera.getCameraInfo(targetCameraId, info);
        int result;
        if (isBackCamera){ // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        else{
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        }
        if (VERBOSE) Log.d(TAG, "Orientation degrees: "+result);
        mCamera.setDisplayOrientation(result);

        //YV12
        if(parameters.getSupportedPreviewFormats().contains(ImageFormat.YV12))
            mImageFormat = ImageFormat.YV12;

        else
            mImageFormat = ImageFormat.NV21;

        parameters.setPreviewFormat(mImageFormat);
        mCamera.setParameters(parameters);
        if (VERBOSE) Util.logCameraPictureFormat(TAG, mImageFormat);

        for (Camera.Size s : mCamera.getParameters().getSupportedPreviewSizes()){
            if ((s.width * sRatioHeight / sRatioWidth) == s.height){
                mSuitableSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSuitableSizes);
        if (VERBOSE) Util.logCameraSizes(TAG, mSuitableSizes);

        mBuffers = new byte[mNumOfBuffers][];
    }

    @Override
    public Camera getCameraInstance() {
        return mCamera;
    }

    @Override
    public List<Size> getSuitableSizes() {
        return mSuitableSizes;
    }

    @Override
    public Size getCurrentSize() {
        return mSuitableSizes.get(mSuitableSizesIndex);
    }

    @Override
    public void switchToMinorSize() {
        if (mSuitableSizesIndex == 0){ mSuitableSizesIndex = mSuitableSizes.size() - 1; }
        else{ mSuitableSizesIndex--; }
        switchToSize(getCurrentSize());
    }

    @Override
    public void switchToMajorSize() {
        mSuitableSizesIndex = (mSuitableSizesIndex + 1) % mSuitableSizes.size();
        switchToSize(getCurrentSize());
    }

    @Override
    public void switchToSize(Size newSize) throws IllegalArgumentException{
        checkForCameraAcquisition(true);
        int idx = mSuitableSizes.indexOf(newSize);
        if (idx < 0){
            throw new IllegalArgumentException("Illegal size: "+newSize);
        }
        /*if (newSize.equals(getCurrentSize())) {
            if (VERBOSE) Log.d(TAG, "No changes in camera size: "+newSize);
            return;
        }*/
        mSuitableSizesIndex = idx;
        int width = newSize.getWidth();
        int height = newSize.getHeight();
        resizeBuffers(width, height);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(width, height);
        mCamera.setParameters(parameters);
        if (mCallback != null) mCallback.onCameraPreviewSizeChanged(width, height);
    }

    @Override
    public void setPreviewSurface(SurfaceHolder surfaceHolder) throws IOException {
        //WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        //final int rotation = wm.getDefaultDisplay().getRotation();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "created");
                try {
                    checkForCameraAcquisition(true);
                    /*mCamera.stopPreview();
                    switch (rotation) {
                        case Surface.ROTATION_0:
                            mCamera.setDisplayOrientation(90);
                            break;
                        case Surface.ROTATION_270:
                            mCamera.setDisplayOrientation(180);
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_180:
                            break;
                    }*/
                    mCamera.setPreviewDisplay(holder);
                    //mCamera.setDisplayOrientation(90);
                    //mCamera.startPreview();

                } catch (IOException e) {
                    Log.e(TAG, e.getClass().getName() + ": " + e.getMessage());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "changed");

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) { Log.d(TAG, "destroyed");
            }
        });
    }

    @Override
    public void startPreview() {
        checkForCameraAcquisition(true);
        mCamera.startPreview();
    }

    @Override
    public void enableFrameCapture() {
        checkForCameraAcquisition(true);
        useNextCallbackBuffer();
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
    }

    @Override
    public void disableFrameCapture() {
        checkForCameraAcquisition(true);
        mCamera.setPreviewCallbackWithBuffer(null);
    }

    @Override
    public void stopPreview() {
        checkForCameraAcquisition(true);
        mCamera.stopPreview();
    }

    @Override
    public void releaseCamera() {
        if (mCamera == null){
            return;
        }
        mCamera.release();
        mCamera = null;
        if (VERBOSE) Log.d(TAG, "Camera released");
    }

    @Override
    public int getImageFormat() {
        return mImageFormat;
    }

    private void resizeBuffers(int width, int height){
        float bitsPerPixel = (float) ImageFormat.getBitsPerPixel(mImageFormat);
        float bytesPerPixel = bitsPerPixel / 8F;
        float bufferSize = width * height * bytesPerPixel;
        if (bufferSize != ((int) bufferSize)){
            //if (mWidth * mHeight *bytesPerPixel) is not integer, let's round it!!
            bufferSize++;
            Log.w(TAG, "Not integer size: " + bytesPerPixel);
        }
        mCurrentBuffersSize = (int) bufferSize;
        for (int idx=0; idx < mNumOfBuffers; idx++){
            mBuffers[idx] = new byte[mCurrentBuffersSize];
        }
        if (VERBOSE) Log.d(TAG, "new preview size=("+width+","+height+") ; Buffers size= "+ bufferSize);
    }


    private int getColorFormat(Camera.Parameters params){
        int res = -1;
        for (int format : params.getSupportedPreviewFormats()) {
            Util.logCameraPictureFormat(TAG+" supported", format);
            switch (format) {
                case ImageFormat.NV21:
                case ImageFormat.YV12:
                    res = format;
                    break;
            }
        }
        if (res < 0) res = ImageFormat.NV21;
        return res;
    }

    private void useNextCallbackBuffer(){
        mCamera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);
        mCurrentBufferIdx = (mCurrentBufferIdx +1) % mNumOfBuffers;
    }

    private void checkForCameraAcquisition(boolean cameraRequired){
        boolean cameraAcquiredNow = (mCamera != null);
        if (cameraAcquiredNow ^ cameraRequired){
            if (VERBOSE) Log.d(TAG, "Camera acquired: "+cameraAcquiredNow+"; required: "+cameraRequired);
            throw new IllegalStateException("State");
        }
    }

}
