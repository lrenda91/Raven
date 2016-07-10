package it.polito.mec.video.raven.sender.encoding;

import java.util.ArrayList;
import java.util.List;

import it.polito.mec.video.raven.sender.record.Size;


/**
 * Created by luigi on 29/04/16.
 */
public class Params {

    public static final Params[] PRESETS = new Params[]{
            new Params(320, 240, 576, 25),      //240p          LOW (bitrate < 1Mbps)
            //new Params(640, 480, 1216, 25),      //480p
            new Params(640, 480, 976, 25),      //480p
            new Params(640, 480, 1856, 25),      //480pHQ       MEDIUM (1Mbps < bitrate < 2Mbps)
            //new Params(640, 480, 960, 25),      //480p
            //new Params(640, 480, 1216, 25),     //480pHQ
            //new Params(768, 576, 1408, 25),     //576p
            //new Params(768, 576, 1536, 25),     //576pHQ
            new Params(768, 576, 1536, 25),     //576p          MEDIUM (1Mbps < bitrate < 2Mbps)
            //new Params(768, 576, 1856, 25),     //576pHQ
            new Params(768, 576, 2432, 25),     //576pHQ        MEDIUM (1Mbps < bitrate < 2Mbps)
            new Params(960, 720, 2432, 25),     //720p          HIGH (2Mbps < bitrate < 4Mbps)
            new Params(960, 720, 3712, 25),     //720pHQ        HIGH (2Mbps < bitrate < 4Mbps)
            //new Params(960, 720, 2432, 25),     //720pHQ
            //new Params(1440, 1080, 3712, 25),   //1080p
            //new Params(1440, 1080, 5632, 25),   //1080pHQ
    };

    public static List<Params> getNearestPresets(Size size){   //il più grande tra i più piccoli
        List<Params> nearest = new ArrayList<>(PRESETS.length);
        //Params nearest = null;
        boolean matchesSize = false;
        for (Params preset : PRESETS){
            if (preset.mWidth > size.getWidth()){
                break;
            }
            //search among 'minor' sizes
            if (!matchesSize){
                //make sure only 'preset' will enter in the set, then update 'matchesSize'
                nearest.clear();
                nearest.add(preset);
                matchesSize = (preset.mWidth == size.getWidth() && preset.mHeight == size.getHeight());
            }
            else{
                nearest.add(preset);
            }
        }
        return nearest;
    }

    private int mWidth = 0, mHeight = 0, mBitrate = 0, mFrameRate = 25; //by default

    private Params(int w, int h, int kbps, int fps){
        mWidth = w;
        mHeight = h;
        mBitrate = kbps;
        mFrameRate = fps;
    }

    public int bitrate() {
        return mBitrate;
    }

    public int frameRate() {
        return mFrameRate;
    }

    public int height() {
        return mHeight;
    }

    public int width() {
        return mWidth;
    }

    public Size getSize(){
        return new Size(mWidth, mHeight);
    }

    public static class Builder {
        private int wid = 0, hei = 0, brate = 0, fps = 25;
        public Builder width(int w){ wid = w; return this; }
        public Builder height(int h){ hei = h; return this; }
        public Builder bitRate(int kbps){ brate = kbps; return this; }
        public Builder frameRate(int frames){ fps = frames; return this; }
        public Params build(){
            return new Params(wid, hei, brate, fps);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof Params)) return false;
        Params other = (Params) o;
        return this.mWidth == other.mWidth
                && this.mHeight == other.mHeight
                && this.mBitrate == other.mBitrate
                && this.mFrameRate == other.mFrameRate;
    }

    @Override
    public String toString() {
        return String.format("[(%dx%d) %d Kbps %d fps]",mWidth, mHeight, mBitrate, mFrameRate);
    }
}
