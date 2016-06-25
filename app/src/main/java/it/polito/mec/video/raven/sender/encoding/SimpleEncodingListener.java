package it.polito.mec.video.raven.sender.encoding;

import it.polito.mec.video.raven.VideoChunks;

/**
 * Created by luigi on 27/04/16.
 */
public abstract class SimpleEncodingListener implements EncodingListener {

    @Override
    public void onEncodingStarted(Params params) { }

    @Override
    public void onParamsChanged(Params actualParams) {}

    @Override
    public void onConfigHeaders(VideoChunks.Chunk chunk, Params params) { }

    @Override
    public void onEncodedChunk(VideoChunks.Chunk chunk) { }

    @Override
    public void onEncodingPaused() { }

    @Override
    public void onEncodingStopped() { }

}
