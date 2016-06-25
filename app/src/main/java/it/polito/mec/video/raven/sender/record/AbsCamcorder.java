package it.polito.mec.video.raven.sender.record;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import android.support.v4.util.Pair;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import it.polito.mec.video.raven.VideoChunks;
import it.polito.mec.video.raven.sender.encoding.EncodingListener;
import it.polito.mec.video.raven.sender.encoding.Params;

/**
 * Created by luigi on 20/05/16.
 */
public abstract class AbsCamcorder implements Camcorder {

    protected static final Size sRatio = new Size(4,3);
    protected static final int FLAG_CODEC_SPECIFIC_DATA = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    protected static final int FLAG_MEDIA_DATA = 0;

    protected Context mContext;
    protected Camera1Manager mCameraManager;
    protected final List<Params> mPresets = new LinkedList<>();
    protected Params mCurrentParams;
    protected final Set<Pair<Handler,EncodingListener>> mEncodeListeners = new HashSet<>();

    public AbsCamcorder(Context context){
        if (context == null){
            throw new IllegalArgumentException("Context mustn't be null");
        }
        mContext = context;
    }

    @Override
    public Params getCurrentParams() {
        return mCurrentParams;
    }

    public final List<Params> getPresets(){
        return mPresets;
    }

    public boolean switchToHigherQuality() throws Exception {
        int idx = mPresets.indexOf(getCurrentParams());
        if (idx < 0) {
            throw new IllegalStateException("Can't find " + getCurrentParams() + " among presets");
        }
        int nextIdx = idx + 1;
        if (nextIdx >= mPresets.size()){
            return false;
        }
        Params next = mPresets.get(nextIdx);
        switchToVideoQuality(next);
        return true;
    }

    public boolean switchToLowerQuality() throws Exception {
        int idx = mPresets.indexOf(getCurrentParams());
        if (idx < 0) {
            throw new IllegalStateException("Can't find " + getCurrentParams() + " among presets");
        }
        int previousIdx = idx - 1;
        if (previousIdx < 0){
            return false;
        }
        Params prev = mPresets.get(previousIdx);
        switchToVideoQuality(prev);
        return true;
    }

    @Override
    public Camera1Manager getCameraManager() {
        return mCameraManager;
    }

    @Override
    public void openCamera() {
        mCameraManager.acquireCamera();
        //now, we have available sizes -> keep suitable presets
        List<Size> sizes = mCameraManager.getSuitableSizes();
        for (Size s : sizes){
            for (Params bestPreset : Params.getNearestPresets(s)){
                mPresets.add(new Params.Builder()
                        .width(s.getWidth())
                        .height(s.getHeight())
                        .bitRate(bestPreset.bitrate())
                        .frameRate(bestPreset.frameRate()).build());
            }
        }
        mCurrentParams = mPresets.get(0);
    }

    @Override
    public void closeCamera() {
        mCameraManager.releaseCamera();
    }

    @Override
    public void setSurfaceView(final SurfaceView surfaceView) {
        try {
            mCameraManager.setPreviewSurface(surfaceView.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int measuredHeight = surfaceView.getMeasuredHeight();
                ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
                lp.width = measuredHeight * sRatio.getWidth() / sRatio.getHeight();
                surfaceView.setLayoutParams(lp);

            }
        });*/
    }

    public boolean registerEncodingListener(EncodingListener listener, Handler handler){
        if (listener == null){
            return false;
        }
        return mEncodeListeners.add(new Pair<>(handler,listener));
    }

    public boolean unregisterEncodingListener(EncodingListener listener){
        if (listener == null){
            return false;
        }
        Iterator<Pair<Handler, EncodingListener>> iterator = mEncodeListeners.iterator();
        while (iterator.hasNext()){
            Pair<Handler, EncodingListener> pair = iterator.next();
            if (pair.second == listener){
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public void clearEncodingListeners(){
        mEncodeListeners.clear();
    }

    protected void notifyEncodingStarted(final Params params){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingStarted(params);
                    }
                });
            } else {
                listener.second.onEncodingStarted(params);
            }
        }
    }

    protected void notifyParamsChanged(final Params params){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onParamsChanged(params);
                    }
                });
            } else {
                listener.second.onParamsChanged(params);
            }
        }
    }

    protected void notifyConfigHeaders(final VideoChunks.Chunk chunk,
                                       final Params params){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            /*if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onConfigHeaders(chunk, width, height, encodeBps, frameRate);
                    }
                });
            } else {
                listener.second.onConfigHeaders(chunk, width, height, encodeBps, frameRate);
            }*/
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onConfigHeaders(chunk, params);
                    }
                });
            } else {
                listener.second.onConfigHeaders(chunk, params);
            }
        }
    }

    protected void notifyEncodedChunkAvailable(final VideoChunks.Chunk chunk){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodedChunk(chunk);
                    }
                });
            } else {
                listener.second.onEncodedChunk(chunk);
            }
        }
    }

    protected void notifyEncodingPaused(){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingPaused();
                    }
                });
            } else {
                listener.second.onEncodingPaused();
            }
        }
    }

    protected void notifyEncodingStopped(){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingStopped();
                    }
                });
            } else {
                listener.second.onEncodingStopped();
            }
        }
    }
}
