package it.polito.mec.video.raven.sender.record;

/**
 * Created by luigi on 20/05/16.
 */
public class Size implements Comparable<Size> {

    private int mWidth, mHeight;

    public Size(int w, int h){
        mWidth = w;
        mHeight = h;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public int compareTo(Size another) {
        if (mWidth > another.mWidth) return 1;
        if (mWidth < another.mWidth) return -1;
        if (mHeight > another.mHeight) return 1;
        if (mHeight < another.mHeight) return -1;
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof Size)) return false;
        Size other = (Size) o;
        return this.mWidth == other.mWidth && this.mHeight == other.mHeight;
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }
}
