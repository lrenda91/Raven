package it.polito.mec.video.raven.sender;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

/**
 * Created by luigi on 24/02/16.
 */
public class NetworkMonitor {

    public interface Callback {
        void onData(long txBytes, long rxBytes);
        void onDataRate(long txBps, long rxBps);
        void onUnsupportedTrafficStats();
    }

    private static final boolean VERBOSE = true;
    private static final String TAG = "NetMonitor";

    private int mAppUID = android.os.Process.myUid();
    private long mStartRX, mStartTX, mPreviousRX, mPreviousTX;
    private boolean mRunning = false;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Callback mCallback;

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            long txBytes = TrafficStats.getUidTxBytes(mAppUID) - mStartTX;
            long rxBytes = TrafficStats.getUidRxBytes(mAppUID) - mStartRX;
            long txBps = txBytes - mPreviousTX;
            long rxBps = rxBytes - mPreviousRX;
            mPreviousTX = txBytes;
            mPreviousRX = rxBytes;
            if (mCallback != null){
                mCallback.onData(txBytes, rxBytes);
                mCallback.onDataRate(txBps, rxBps);
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    public NetworkMonitor(Callback callback){
        mCallback = callback;
    }

    public void start(){
        mPreviousRX = 0L;
        mPreviousTX = 0L;
        mStartTX = TrafficStats.getUidTxBytes(mAppUID);
        mStartRX = TrafficStats.getUidRxBytes(mAppUID);
        if (mStartTX == TrafficStats.UNSUPPORTED || mStartTX == TrafficStats.UNSUPPORTED){
            if (mCallback != null){
                mCallback.onUnsupportedTrafficStats();
            }
            return;
        }
        mRunning = true;
        mHandler.postDelayed(mRunnable, 1000);
        if (VERBOSE) Log.d(TAG, "Started");
    }

    public void stop(){
        mHandler.removeCallbacks(mRunnable);
        mRunning = false;
        if (VERBOSE) Log.d(TAG, "Stopped");
    }

    public boolean isRunning(){
        return mRunning;
    }

}
