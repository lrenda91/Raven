package it.polito.mec.video.raven.sender.encoding;

import it.polito.mec.video.raven.VideoChunks;

/**
 * Created by luigi on 27/04/16.
 */
public interface EncodingListener {

    void onEncodingStarted(Params actualParams);
    void onParamsChanged(Params actualParams);
    void onConfigHeaders(VideoChunks.Chunk chunk,
                         Params actualParams);
    void onEncodedChunk(VideoChunks.Chunk chunk);
    void onEncodingPaused();
    void onEncodingStopped();

}
