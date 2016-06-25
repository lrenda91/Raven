package it.polito.mec.video.raven.sender.encoding;

import android.util.Log;

/**
 * Created by luigi on 12/04/16.
 */
public class StreamSenderJNI {

    static {
        try {
            System.loadLibrary("MediaEncoder");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load.\n" + e);
            System.exit(1);
        }
    }

    public static native void nativeInitEncoder();

    public static native void nativeReleaseEncoder();

    public static native byte[][] nativeGetHeaders();

    public static native void nativeApplyParams(final int width,
                                          final int height,
                                          final int bitrateKbps);

    public static native byte[] nativeDoEncode(final int width,
                                      final int height, byte[] yuv,
                                      final int flag);

    public static native String hello();
}
