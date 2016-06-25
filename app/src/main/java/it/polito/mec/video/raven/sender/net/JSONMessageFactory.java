package it.polito.mec.video.raven.sender.net;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.Deflater;

import it.polito.mec.video.raven.VideoChunks;

/**
 * Created by luigi on 24/01/16.
 */
public class JSONMessageFactory {

    public static final String RULE_KEY = "rule";
    public static final String RULE_VALUE = "pub";

    public static final String TYPE_KEY = "type";
    public static final String DATA_KEY = "data";
    public static final String FLAGS_KEY = "flags";
    public static final String WIDTH_KEY = "width";
    public static final String HEIGHT_KEY = "height";
    public static final String BPS_KEY = "encodeBps";
    public static final String FPS_KEY = "frameRate";
    public static final String TIMESTAMP_KEY = "ts";
    public static final String DEVICE_KEY = "device";
    public static final String QUALITIES_KEY = "qualities";
    public static final String CURRENT_QUALITY_KEY = "current";
    public static final String BITRATES_KEY = "bitrates";
    public static final String CURRENT_BITRATE_KEY = "currentBitrate";

    private JSONMessageFactory(){}

    private static JSONObject get() throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put(RULE_KEY, RULE_VALUE);
        return msg;
    }

    /*
    public static JSONObject createHelloMessage(String device, String[] qualities,
                                                int actualSizeIdx,
                                                int[] bitRates, int actualBitrateIdx) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "hello");
        msg.put(DEVICE_KEY, device);
        JSONArray sizesArray = new JSONArray();
        for (String s : qualities) {
            sizesArray.put(s);
        }
        msg.put(QUALITIES_KEY, sizesArray);
        msg.put(CURRENT_QUALITY_KEY, qualities[actualSizeIdx]);
        JSONArray bitRatesArray = new JSONArray();
        for (int br : bitRates) {
            bitRatesArray.put(br);
        }
        msg.put(BITRATES_KEY, bitRatesArray);
        msg.put(CURRENT_BITRATE_KEY, bitRates[actualBitrateIdx]);
        return msg;
    }
    */

    public static JSONObject createHelloMessage2(String device, List<String> qualities,
                                                int actualQualityIdx) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "hello");
        msg.put(DEVICE_KEY, device);
        JSONArray sizesArray = new JSONArray(qualities);
        msg.put(QUALITIES_KEY, sizesArray);
        msg.put(CURRENT_QUALITY_KEY, actualQualityIdx);
        /*JSONArray bitRatesArray = new JSONArray();
        for (int br : bitRates) {
            bitRatesArray.put(br);
        }
        msg.put(BITRATES_KEY, bitRatesArray);
        msg.put(CURRENT_BITRATE_KEY, bitRates[actualBitrateIdx]);*/
        return msg;
    }


    /**
     * Creates JSON object which wraps the first frame (SPS-PPS configuration)
     * @param configData
     * @return
     * @throws JSONException
     */
    public static JSONObject createConfigMessage(byte[] configData,
            int width, int height, int encodeBps, int frameRate) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "config");
        String base64 = Base64.encodeToString(configData, Base64.DEFAULT);
        msg.put(DATA_KEY, base64);
        msg.put(WIDTH_KEY, width);
        msg.put(HEIGHT_KEY, height);
        msg.put(BPS_KEY, encodeBps);
        msg.put(FPS_KEY, frameRate);
        return msg;
    }

    public static JSONObject createStreamMessage(VideoChunks.Chunk chunk) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "stream");
        String base64 = Base64.encodeToString(chunk.data, Base64.DEFAULT);
        msg.put(DATA_KEY, base64);
        msg.put(FLAGS_KEY, chunk.flags);
        msg.put(TIMESTAMP_KEY, chunk.presentationTimestampUs);
        return msg;
    }

    public static String compress(byte[] data) throws IOException {

        Deflater deflater = new Deflater();

        deflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        deflater.finish();

        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {

            int count = deflater.deflate(buffer); // returns the generated code... index

            outputStream.write(buffer, 0, count);

        }

        outputStream.close();

        //byte[] output = outputStream.toByteArray();
        String out = outputStream.toString();
        Log.d("JSON","Original: " + data.length + " b");
        Log.d("JSON","Compressed: " + out.length()  + " b");

        return out;

    }

}
