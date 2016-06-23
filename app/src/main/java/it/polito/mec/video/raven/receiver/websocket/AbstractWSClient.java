package it.polito.mec.video.raven.receiver.websocket;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketListener;

import java.net.InetAddress;

/**
 * Created by luigi on 02/12/15.
 */
public abstract class AbstractWSClient extends WebSocketAdapter implements WebSocketClient {

    protected WebSocketListener mProtocol;
    protected WebSocket mWebSocket;

    @Override
    public WebSocket getSocket() {
        return mWebSocket;
    }


}
