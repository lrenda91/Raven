package it.polito.mec.video.raven.receiver.websocket;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mec.video.raven.receiver.VideoChunks;

/**
 * Created by luigi on 24/01/16.
 */
public class JSONMessageFactory {

    private static final String RULE_KEY = "rule";
    private static final String RULE_VALUE = "sub";

    private static final String TYPE_KEY = "type";
    private static final String DATA_KEY = "data";
    private static final String FLAGS_KEY = "flags";
    private static final String TIMESTAMP_KEY = "ts";
    /*private static final String CONFIG_TYPE_VALUE = "config";
    private static final String STREAM_TYPE_VALUE = "stream";
    private static final String RESET_TYPE_VALUE = "reset";*/

    private JSONMessageFactory(){}

    private static JSONObject get() throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put(RULE_KEY, RULE_VALUE);
        return msg;
    }

    public static JSONObject createHelloMessage() throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "hello");
        return msg;
    }

    public static JSONObject createConfigRequestMessage() throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "config");
        return msg;
    }

    public static JSONObject createConfigMessage(byte[] configData) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "config");
        String base64 = Base64.encodeToString(configData, Base64.DEFAULT);
        try {
            //msg[data] = Buffer() in javascript
            msg.put(DATA_KEY, new String(configData));
        }
        catch(Exception e){
            throw new JSONException(e.getClass().getSimpleName());
        }
        return msg;
    }

    public static JSONObject createStreamMessage(VideoChunks.Chunk chunk) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "stream");
        String base64 = Base64.encodeToString(chunk.data, Base64.DEFAULT);
        try {
            //msg[data] = Buffer() in javascript
            msg.put(DATA_KEY, new String(chunk.data));
        }
        catch(Exception e){
            throw new JSONException(e.getClass().getSimpleName());
        }
        msg.put(FLAGS_KEY, chunk.flags);
        msg.put(TIMESTAMP_KEY, chunk.presentationTimestampUs);
        return msg;
    }

    public static JSONObject createResetMessage() throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "reset");
        return msg;
    }

}
