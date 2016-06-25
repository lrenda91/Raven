package it.polito.mec.video.raven.sender.record;

import android.view.SurfaceView;

import it.polito.mec.video.raven.sender.encoding.Params;

/**
 * Created by luigi on 24/02/16.
 */
public interface Camcorder {

    void openCamera();

    void setSurfaceView(SurfaceView surfaceView);

    void startRecording();

    void pauseRecording();

    void stopRecording();

    void switchToVideoQuality(Params params);

    void closeCamera();

    Camera1Manager getCameraManager();

    Params getCurrentParams();

}
