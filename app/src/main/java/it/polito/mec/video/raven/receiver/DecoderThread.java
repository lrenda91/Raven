package it.polito.mec.video.raven.receiver;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import it.polito.mec.video.raven.VideoChunks;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class DecoderThread extends Thread implements Runnable {

    private static final String TAG = "DECODER";
    private static final boolean VERBOSE = false;

    private static final int TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";

    private ByteBuffer mConfigBuffer;
    private VideoChunks mEncodedFrames = new VideoChunks();
    private final int mWidth, mHeight;

    private Surface mOutputSurface;

    public DecoderThread(int w, int h){
        mWidth = w;
        mHeight = h;
    }

    public void drain(){
        mEncodedFrames.clear();
    }

    public synchronized void setConfigurationBuffer(byte[] csd0){
        mConfigBuffer = ByteBuffer.wrap(csd0);
        notifyAll();
    }

    public void submitEncodedData(VideoChunks.Chunk chunk){
        mEncodedFrames.addChunk(chunk);
    }

    public void setSurface(Surface s){
        mOutputSurface = s;
    }

    @Override
    public void run() {
        MediaCodec decoder = null;
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_ROTATION, 90);
        try{
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            //now, we must wait for csd-0 configuration bytes by the encoder
            //that is, until setConfigurationBuffer() will be called from another thread
            if (VERBOSE) Log.d(TAG, "Waiting for configuration buffer from the encoder...");
            synchronized (this){
                while (mConfigBuffer == null){
                    wait();
                }
            }
            byte[] array = new byte[mConfigBuffer.remaining()];
            mConfigBuffer.get(array);
            int spsIdx = -1, spsSize = 0, ppsIdx = -1, ppsSize = 0;
            for(int i=0; i <= array.length-4; i++){
                boolean delimiterFound =
                        (array[i]==0 && array[i+1]==0 && array[i+2]==0 && array[i+3]==1);
                if (spsIdx < 0 && delimiterFound){
                    spsIdx = i;
                }
                else if (ppsIdx < 0 && delimiterFound){
                    spsSize = i - spsIdx;
                    ppsIdx = i;
                    ppsSize = array.length - ppsIdx;
                }
            }
            byte[] spsArray = new byte[spsSize], ppsArray = new byte[ppsSize];
            mConfigBuffer.position(0);
            mConfigBuffer.get(spsArray, 0, spsSize);
            mConfigBuffer.get(ppsArray, 0, ppsSize);
            ByteBuffer sps = ByteBuffer.wrap(spsArray);
            ByteBuffer pps = ByteBuffer.wrap(ppsArray);

            format.setByteBuffer("csd-0", sps);
            format.setByteBuffer("csd-1", pps);
            byte[] b = new byte[mConfigBuffer.remaining()];
            mConfigBuffer.get(b);

            if (VERBOSE) Log.d(TAG, "Configured csd-0 buffer: "+mConfigBuffer.toString());
            decoder.configure(format, mOutputSurface,  null, 0);
            decoder.start();
        }
        catch(InterruptedException e){
            Log.d(TAG, "Cancel requested");
            decoder.release();
            Log.i(TAG, "Decoder Released!! Closing");
            return;
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return;
        }
        catch(Throwable t){
            Log.e(TAG, t.toString());
            return;
        }



        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        long counter = 0;

        int inputStatus = -1, outputStatus = -1;

        if (VERBOSE) Log.d(TAG, "Decoder starts...");

        while (!Thread.interrupted()){
            //if (!EOS_Received) {
            if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
            inputStatus = decoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputStatus >= 0) {
                ByteBuffer inputBuf = decoderInputBuffers[inputStatus];
                inputBuf.clear();

                VideoChunks.Chunk chunk = mEncodedFrames.getNextChunk();
                if (chunk == null){
                    if (VERBOSE) Log.d(TAG, "Cancelling thread...");
                    break;
                }
                //Log.d(TAG, "chunk seq # -> "+chunk.sn);
                if (VERBOSE) Log.d(TAG, "Received byte["+chunk.data.length+"] from server");

                inputBuf.put(chunk.data);
                counter++;

                decoder.queueInputBuffer(inputStatus, 0, chunk.data.length,
                        chunk.presentationTimestampUs, chunk.flags);
                if (VERBOSE) Log.d(TAG, "queued array # " + counter + ": "
                        + chunk.data.length + " bytes to decoder");
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            outputStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
            switch (outputStatus){
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    //if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                    decoderOutputBuffers = decoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    format = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + format);
                    break;
                default:
                    if (outputStatus < 0)
                        break;
                    if (VERBOSE) Log.d(TAG, "DECODER OUTPUT AVAILABLE!!!");
                    ByteBuffer outputFrame = decoderOutputBuffers[outputStatus];
                    if (outputFrame == null){
                        Log.e(TAG, "NULL OUTPUT FRAME");
                        break;
                    }
                    outputFrame.position(info.offset);
                    outputFrame.limit(info.offset + info.size);
                    decoder.releaseOutputBuffer(outputStatus, true /*render*/);
                    if (VERBOSE) Log.d(TAG, "released with render=true");
                    break;
            }
        }
        decoder.stop();
        decoder.release();
        Log.i(TAG, "Decoder Released!! Closing");
    }

}
