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
