package it.polito.mec.video.raven.sender;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.CamcorderProfile;
import android.media.MediaCodecInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import it.polito.mec.video.raven.sender.record.Size;

/**
 * Created by luigi on 27/01/16.
 */
public class Util {

    /*
    public static boolean a(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        int type = info.getType(), subType = info.getSubtype();
        Log.d("NET", String.format("type: %s subtype: %s", info.getTypeName(), info.getSubtypeName()));
        if (type == ConnectivityManager.TYPE_WIFI) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                Integer linkSpeed = wifiInfo.getLinkSpeed(); //measured using WifiInfo.LINK_SPEED_UNITS
                Log.d("NET", "speed: "+linkSpeed);
            }
            return true;
        } else if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    return false; // ~ 14-64 kbps
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    return true; // ~ 400-1000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    return true; // ~ 600-1400 kbps
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return false; // ~ 100 kbps
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    return true; // ~ 2-14 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    return true; // ~ 700-1700 kbps
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    return true; // ~ 1-23 Mbps
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return true; // ~ 400-7000 kbps
                case TelephonyManager.NETWORK_TYPE_EHRPD: // API level 11
                    return true; // ~ 1-2 Mbps
                case TelephonyManager.NETWORK_TYPE_EVDO_B: // API level 9
                    return true; // ~ 5 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPAP: // API level 13
                    return true; // ~ 10-20 Mbps
                case TelephonyManager.NETWORK_TYPE_IDEN: // API level 8
                    return false; // ~25 kbps
                case TelephonyManager.NETWORK_TYPE_LTE: // API level 11
                    return true; // ~ 10+ Mbps
                // Unknown
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                default:
                    return false;
            }
        } else {
            return false;
        }
    }
*/









    /**
     * Performs in place Cb and Cr swapping for a YUV 4:2:0 Planar frame <br>
     * The frame could be formatted as :
     * <ul>
     * <li>YUV420p, that is [Y1Y2Y3Y4....][U1U2...][V1V2...]</li> or
     * <li>YV12, that is [Y1Y2Y3Y4....][V1V2...][U1U2...]</li>
     * </ul>
     * Whatever will be the input format, the function converts it in the other format
     * @param frame the YUV420p frame
     * @return
     */
    public static byte[] swapYUV420Planar(byte[] frame){
        int p = frame.length * 2 / 3;    //<-- this is equals to width*height
        int idx = p;
        int Clen = p/4; //<-- that is, (width/2*height/2)
        for (int i=0; i< Clen; i++){
            int uIdx = idx+i;
            int vIdx = idx+i+Clen;
            byte U = frame[uIdx];
            byte V = frame[vIdx];
            byte tmp = U;
            frame[uIdx] = V;
            frame[vIdx] = tmp;
        }
        return frame;
    }

    /**
     * Performs in place Cb and Cr swapping for a YUV 4:2:0 SemiPlanar frame <br>
     * The frame could be formatted as :
     * <ul>
     * <li>NV12, that is [Y1Y2Y3Y4....][U1V1U2V2...]</li> or
     * <li>NV21, that is [Y1Y2Y3Y4....][V1U1V2U2...]</li>
     * </ul>
     * Whatever will be the input format, the function converts it in the other format
     * @param frame the YUV420sp frame
     * @return
     */
    public static byte[] swapYUV420SemiPlanar(byte[] frame, int width, int height) {
        int p = frame.length * 2 / 3;    //<-- this is equals to width*height
        int idx = p;
        int Clen = p/2; //<-- that is, (width*height/2)
        for (int i=0; i<Clen; i+=2){
            int uIdx = idx+i;
            int vIdx = idx+i+1;
            byte U = frame[uIdx];
            byte V = frame[vIdx];
            byte tmp = U;
            frame[uIdx] = V;
            frame[vIdx] = tmp;
        }
        return frame;
    }

    public static byte[] swapColors(byte[] data, int w, int h, int pictureFormat){
        switch(pictureFormat){
            case ImageFormat.YV12:
                //return swapYUV420SemiPlanar(data,w, h);
                return swapYUV420Planar(data);
            case ImageFormat.NV21:
                return swapYUV420SemiPlanar(data,w,h);
            default:
                Log.w("Util", "No color format to swap");
        }
        return data;
    }

    public static int getEncoderColorFormat(int previewFormat){
        if (Build.VERSION.SDK_INT >= 21){
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        }
        switch(previewFormat){
            case ImageFormat.NV21:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            case ImageFormat.YV12:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        }
        return -1;
    }

    public static void logColorFormat(String tag, int colorFormat){
        switch(colorFormat){
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Log.d(tag, "COLOR_FormatYUV420Planar"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                Log.d(tag, "COLOR_FormatYUV420SemiPlanar"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Log.d(tag, "COLOR_FormatYUV420Flexible"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface:
                Log.d(tag, "COLOR_FormatSurface"); break;
        }
    }

    public static void logCameraSizes(String tag, List<Size> sizes){
        String log = "[ ";
        for (Size s : sizes){
            log += s.getWidth()+"x"+s.getHeight()+" ";
        }
        log += "]";
        Log.d(tag, log);
    }

    public static void logCameraPictureFormat(String tag, int picFormat){
        switch(picFormat){
            case ImageFormat.NV21:
                Log.d(tag, "NV21"); break;
            case ImageFormat.YV12:
                Log.d(tag, "YV12"); break;
            case ImageFormat.JPEG:
                Log.d(tag, "JPEG"); break;
            case ImageFormat.YUY2:
                Log.d(tag, "YUY2"); break;
            case ImageFormat.YUV_420_888:
                Log.d(tag, "YUV_420_888"); break;
            case ImageFormat.YUV_422_888:
                Log.d(tag, "YUV_422_888"); break;
            case ImageFormat.YUV_444_888:
                Log.d(tag, "YUV_444_888"); break;
            default:
                Log.d(tag, "unknown ImageFormat"); break;
        }
    }


    private static int[] specificProfileQualities = {
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_QVGA,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_1080P};

    public static List<CamcorderProfile> getAvailableProfiles(int cameraID){
        List<CamcorderProfile> profiles = new ArrayList<>(specificProfileQualities.length);
        for (int quality : specificProfileQualities){
            if (CamcorderProfile.hasProfile(cameraID, quality)){
                CamcorderProfile p = CamcorderProfile.get(cameraID, quality);
                profiles.add(p);
                Log.d("PROFILE", p.videoFrameWidth+"x"+p.videoFrameHeight+" "+p.videoFrameRate+" fps "+
                    p.videoBitRate+" bps ");
            }
        }
        return profiles;
    }

    public static boolean isValidIPv4(String ipString){
        Pattern p = Pattern.compile(
                "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
        return p.matcher(ipString).matches();
    }

    public static String getCompleteDeviceName(){
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

}
