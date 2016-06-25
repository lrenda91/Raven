package it.polito.mec.video.raven.receiver.net;

import com.neovisionaries.ws.client.WebSocket;

/**
 * Created by luigi on 02/12/15.
 */
public interface WSClient {

    WebSocket getSocket();

    void connect(String serverIP, int port, int timeout);

    void closeConnection();

}
