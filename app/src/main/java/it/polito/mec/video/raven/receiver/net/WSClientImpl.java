package it.polito.mec.video.raven.receiver.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClientImpl extends WebSocketAdapter implements WSClient {

    private static final boolean VERBOSE = true;
    private static final String TAG = "WSClient";

    private Handler mMainHandler;

    public interface Listener {
        void onConnectionLost(boolean closedByServer);
        void onConnectionEstablished();
        void onConnectionFailed(Exception e);
        void onConfigParamsReceived(byte[] configParams, int w, int h, int bitrate);
        void onStreamChunkReceived(byte[] chunk, int flags, long timestamp);
    }

    private WebSocket mWebSocket;
    private Listener mListener;

    public WSClientImpl(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    @Override
    public WebSocket getSocket() {
        return mWebSocket;
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String uri = "ws://" + serverIP + ":" + port;
                    mWebSocket = new WebSocketFactory().createSocket(uri, timeout);
                    mWebSocket.addListener(WSClientImpl.this);
                    mWebSocket.connect();
                    if (VERBOSE) Log.d(TAG, "Successfully connected to " + uri);
                } catch (final Exception e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onConnectionFailed(e);
                        }
                    });
                    return;
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) mListener.onConnectionEstablished();
                    }
                });
                sendHello();
                requestConfigParams();
            }
        }).start();
    }

    @Override
    public void closeConnection() {
        mWebSocket.sendClose();
    }

    public void sendHello(){
        try {
            JSONObject configMsg = JSONMessageFactory.createHelloMessage();
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {

        }
    }

    public void requestConfigParams(){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigRequestMessage();
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {

        }
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        JSONObject obj = null;
        try{
            obj = new JSONObject(text);
            if (obj.has("type")){
                Object type = obj.get("type");
                if (type.equals("config")) {
                    if (VERBOSE) Log.d(TAG, "Received config from server: "+text);
                    String sParams = obj.getString("configArray");
                    int width = obj.getInt("width");
                    int height = obj.getInt("height");
                    int Kbps = obj.getInt("encodeBps");
                    final byte[] params = Base64.decode(sParams, Base64.DEFAULT);
                    if (mListener != null) mListener.onConfigParamsReceived(params, width, height, Kbps);
                }
                else if (type.equals("stream")){
                    String sChunk = obj.getString("data");
                    final byte[] chunk = Base64.decode(sChunk, Base64.DEFAULT);
                    final int flags = obj.getInt("flags");
                    final long timestamp = obj.getLong("ts");
                    if (mListener != null) mListener.onStreamChunkReceived(chunk, flags, timestamp);
                }
            }
        }
        catch(JSONException e){
            Log.e(TAG, "Can't parse JSON from text: "+text);
        }

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
        if (VERBOSE) Log.d("WS", "disconnected by server: " + closedByServer);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionLost(closedByServer);
            }
        });
    }

}
