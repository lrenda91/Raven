package it.polito.mec.video.raven.sender.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.neovisionaries.ws.client.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import it.polito.mec.video.raven.VideoChunks;
import it.polito.mec.video.raven.sender.Util;
import it.polito.mec.video.raven.sender.encoding.*;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClientImpl extends WebSocketAdapter implements WSClient, EncodingListener {

    private static final boolean VERBOSE = false;
    private static final String TAG = "WSClient";
    private static final String WS_URI_FORMAT = "ws://%s:%d";
    private static final String HTTP_URI_FORMAT = "http://%s:%d";

    public interface Listener {
        void onConnectionEstablished(String uri);
        void onConnectionLost(boolean closedByServer);
        void onConnectionFailed(Exception e);
        void onResetReceived(int w, int h, int kbps);
        void onBandwidthChange(int Kbps, double performancesPercentage);
    }

    private Handler mMainHandler;
    private String mServerIP;
    private int mPort;

    protected WebSocket mWebSocket;
    private Listener mListener;

    public WSClientImpl(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mMeasureThread = new BandwidthMeasureThread();
        mListener = listener;
    }

    @Override
    public void onEncodingStarted(Params params) {
        mMeasureThread.setPause(false);
    }

    @Override
    public void onParamsChanged(Params actualParams) {

    }

    @Override
    public void onConfigHeaders(VideoChunks.Chunk chunk, Params params) {
        if (isOpen()) {
            sendConfigBytes(chunk.data, params.width(), params.height(), params.bitrate(), params.frameRate());
        }
    }

    @Override
    public void onEncodedChunk(VideoChunks.Chunk chunk) {
        if (isOpen()) {
            sendStreamBytes(chunk);
        }
    }

    @Override
    public void onEncodingPaused() {
        mMeasureThread.setPause(true);
    }

    @Override
    public void onEncodingStopped() {
        mMeasureThread.setPause(true);
    }

    @Override
    public WebSocket getWebSocket() {
        return mWebSocket;
    }

    public boolean isOpen(){
        return mWebSocket != null && mWebSocket.isOpen();
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        mServerIP = serverIP;
        mPort = port;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    String mConnectURI = String.format(WS_URI_FORMAT, serverIP, port);
                    mWebSocket = new WebSocketFactory().createSocket(mConnectURI, timeout);
                    mWebSocket.addListener(WSClientImpl.this);
                    mWebSocket.addHeader("rule","pub");
                    mWebSocket.connect();
                }
                catch(final Exception e){
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onConnectionFailed(e);
                        }
                    });
                    return;
                }
            }
        }).start();
    }

    @Override
    public void closeConnection() {
        mWebSocket.sendClose();
    }

    /*
    public void sendHelloMessage(//String device,
                                 String[] qualities, int actualSizeIdx,
                                 int[] bitrates, int actualBitrateIdx){
        try {
            String device = Util.getCompleteDeviceName();
            JSONObject configMsg = JSONMessageFactory.createHelloMessage(device, qualities, actualSizeIdx,
                    bitrates, actualBitrateIdx);
            mWebSocket.sendText(configMsg.toString());
            totalBytesToSend.addAndGet(configMsg.toString().length());
            contToSend.incrementAndGet();
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }*/

    private static String getFormattedParams(Params params){
        return String.format("%dx%d %d", params.width(), params.height(), params.bitrate());
    }

    public void sendHelloMessage2(List<Params> params, int currentIdx){
        try {
            String device = Util.getCompleteDeviceName();
            List<String> qualities = new ArrayList<>(params.size());
            for (Params p : params){
                qualities.add(getFormattedParams(p));
            }
            /*JSONObject configMsg = JSONMessageFactory.createHelloMessage(device, qualities, actualSizeIdx,
                    bitrates, actualBitrateIdx);*/
            JSONObject configMsg = JSONMessageFactory.createHelloMessage2(device, qualities, currentIdx);
            mWebSocket.sendText(configMsg.toString());
            totalBytesToSend.addAndGet(configMsg.toString().length());
            contToSend.incrementAndGet();
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void sendConfigBytes(final byte[] configData, int width, int height, int encodeBps, int frameRate){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigMessage(configData, width, height, encodeBps, frameRate);
            mWebSocket.sendText(configMsg.toString());
            totalBytesToSend.addAndGet(configMsg.toString().length());
            contToSend.incrementAndGet();
            //cont = 1;

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private static final int STREAM_HEADER_SIZE = (Integer.SIZE + Long.SIZE) / Byte.SIZE;

    public void sendStreamBytes(final VideoChunks.Chunk chunk){
        /*try {
            JSONObject obj = JSONMessageFactory.createStreamMessage(chunk);
            String text = obj.toString();
            totalBytesToSend.addAndGet(text.length());
            contToSend.incrementAndGet();
            mWebSocket.sendText(text);
        }
        catch(JSONException e){
            Log.e(TAG, e.getMessage());
        }
        */
        DataOutputStream dos = new DataOutputStream(baos);
        byte[] payload = null;
        try{
            dos.writeInt(chunk.flags);
            dos.writeLong(chunk.presentationTimestampUs);
            dos.write(chunk.data);
            payload = baos.toByteArray();

            assert (payload.length == (STREAM_HEADER_SIZE + chunk.data.length));
            totalBytesToSend.addAndGet(payload.length);
            contToSend.incrementAndGet();
            if (payload != null) mWebSocket.sendBinary(payload);
        }
        catch(IOException e){
            Log.e(TAG, e.getMessage());
            return;
        }
        finally {
            baos.reset();
            try { dos.close(); }
            catch(IOException e){}
        }
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        final String mConnectURI = String.format(WS_URI_FORMAT, mServerIP, mPort);
        if (VERBOSE) Log.d(TAG, "Successfully connected to " + mConnectURI);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionEstablished(mConnectURI);
            }
        });
    }

    @Override
    public void onConnectError(WebSocket websocket, final WebSocketException exception) throws Exception {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionFailed(exception);
            }
        });
    }


    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, final boolean closedByServer) throws Exception {
        mMeasureThread.stopAndWait();
        if (VERBOSE) Log.d("WS", "disconnected by server: " + closedByServer);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionLost(closedByServer);
            }
        });
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        try{
            JSONObject obj = new JSONObject(text);
            if (obj.has("type")){
                if (obj.get("type").equals("reset")){
                    if (obj.has("width")
                            && obj.has("height")
                            && obj.has("bitrate")){
                        final int width = obj.getInt("width");
                        final int height = obj.getInt("height");
                        final int bitrate = obj.getInt("bitrate");
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) mListener.onResetReceived(width, height, bitrate);
                            }
                        });
                    }
                }
            }
        }catch(JSONException e){}
    }

    @Override
    public void onFrameSent(WebSocket websocket, final WebSocketFrame frame) throws Exception {
        super.onFrameSent(websocket, frame);
        if (frame.isDataFrame()) {
            if (contSent.get() == 0){
                mMeasureThread.start();
            }
            totalSentBytes.addAndGet(frame.getPayloadLength());
            contSent.incrementAndGet();
        }
    }

    private AtomicLong totalSentBytes = new AtomicLong(0), contSent = new AtomicLong(0) ;
    private AtomicLong totalBytesToSend = new AtomicLong(0), contToSend = new AtomicLong(0);
    private BandwidthMeasureThread mMeasureThread;
    private AtomicBoolean mPostBandwidthChange = new AtomicBoolean(true);

    public void setBandWidthMeasureEnabled(boolean b){
        mPostBandwidthChange.set(b);
        if (VERBOSE) Log.d(TAG, "set post= "+b);
    }

    class BandwidthMeasureThread implements Runnable {
        private Thread mWorkerThread;
        private boolean mPauseRequest;
        private double mPercentage = 100;
        BandwidthMeasureThread(){
            synchronized (this){
                mPauseRequest = false;
            }
        }
        void start(){
            if (mWorkerThread != null) return;
            mWorkerThread = new Thread(this, getClass().getName());
            mWorkerThread.start();
        }
        void stopAndWait(){
            if (mWorkerThread == null) return;
            mWorkerThread.interrupt();
            try{ mWorkerThread.join(); } catch (InterruptedException e){}
            mWorkerThread = null;
        }
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (!Thread.interrupted()){
                try {
                    Thread.sleep(5000);
                    synchronized (this) {
                        while (mPauseRequest) {
                            if (VERBOSE) Log.d(TAG, "PAUSE BW MEASURE");
                            wait();
                        }
                    }
                }
                catch(InterruptedException e) {
                    break;
                }

                double toSendValue = (double) totalBytesToSend.get();
                double sentValue = (double) totalSentBytes.get();
                Log.d(TAG, "SENT: "+contSent.get()+" TO SEND: "+contToSend.get());
                if (toSendValue > 0){
                    double ratio = sentValue / toSendValue * 100.0;
                    double percentage = Math.round(ratio * 100.0) / 100.0;
                    double millis = (double) (System.currentTimeMillis() - startTime);
                    double elapsedSeconds = millis / 1000.0;
                    int Bps = (int)(sentValue / elapsedSeconds);
                    final int Kbps = Bps * 8 / 1000;
                    mPercentage = Math.min(percentage, 100);    //in case it exceeds 100.0
                    if (mPostBandwidthChange.get()) {
                        if (VERBOSE) Log.d(TAG, Kbps+" Kbps "+mPercentage+" %");
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null)
                                    mListener.onBandwidthChange(Kbps, mPercentage);
                            }
                        });
                    }
                }
                totalBytesToSend.set(0);
                totalSentBytes.set(0);
            }
            if (VERBOSE) Log.d(TAG, "STOP BW MEASURE");
        }

        synchronized void setPause(boolean b){
            mPauseRequest = b;
            notifyAll();
        }
    }

}
