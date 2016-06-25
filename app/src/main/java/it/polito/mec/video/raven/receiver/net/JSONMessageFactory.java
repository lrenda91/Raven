package it.polito.mec.video.raven.receiver.net;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import it.polito.mec.video.raven.VideoChunks;

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

    public static byte[] decompress(String s) throws IOException, DataFormatException {
        byte[] data = s.getBytes();
        Inflater inflater = new Inflater();

        inflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        byte[] buffer = new byte[1024];

        while (!inflater.finished()) {

            int count = inflater.inflate(buffer);

            outputStream.write(buffer, 0, count);

        }

        outputStream.close();

        byte[] out = outputStream.toByteArray();
        Log.d("JSON","Original: " + data.length + " b");
        Log.d("JSON","Compressed: " + out.length  + " b");

        return out;

    }

}
