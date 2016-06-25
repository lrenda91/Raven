package it.polito.mec.video.raven;

import java.util.LinkedList;

/**
 * MULTITHREAD SHARED OBJECT!!
 * The elementary stream coming out of the "video/avc" encoder needs to be fed back into
 * the decoder one chunk at a time.  If we just wrote the data to a file, we would lose
 * the information about chunk boundaries.  This class stores the encoded data in memory,
 * retaining the chunk organization.
 */
public class VideoChunks {

    public static class Chunk {
        public final byte[] data;
        public final int flags;
        public final long presentationTimestampUs;
        public Chunk(byte[] data, int flags, long presentationTimestampUs){
            this.data = data;
            this.flags = flags;
            this.presentationTimestampUs = presentationTimestampUs;
        }
    }

    private final int mMaxSize = 100;
    private LinkedList<Chunk> mChunks = new LinkedList<>();

    public synchronized void addChunk(byte[] data, int flags, long time) {
        if (mChunks.size() == mMaxSize){
            mChunks.removeFirst();
        }
        mChunks.addLast(new Chunk(data, flags, time));
        notifyAll();
    }

    public synchronized void addChunk(Chunk chunk) {
        if (mChunks.size() == mMaxSize){
            mChunks.removeFirst();
        }
        mChunks.addLast(chunk);
        notifyAll();
    }

    public synchronized void clear(){
        mChunks.clear();
    }

    /**
     * Returns the number of chunks currently held.
     */
    public synchronized int size() {
        return mChunks.size();
    }

    public synchronized Chunk getNextChunk(){
        while (mChunks.isEmpty()){
            try{
                wait();
            }
            catch (InterruptedException e){
                /**
                 * IMPORTANT: this exception will be thrown when cancel() is called
                 * on the AsyncTask which called this function, i.e. when interrupt()
                 * is called on the calling thread
                 */
                notifyAll();
                return null;
            }
        }
        Chunk nextChunk = mChunks.removeFirst();
        notifyAll();
        return nextChunk;
    }

}