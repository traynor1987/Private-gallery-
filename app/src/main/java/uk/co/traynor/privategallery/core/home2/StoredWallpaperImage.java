package uk.co.traynor.privategallery.core.home2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Android's decoder establishes the same bounded stored-image invariant as normal ingestion. */
public final class StoredWallpaperImage {
    private static final int EDGE=2048;
    private static final long PIXELS=(long)EDGE*EDGE;
    private StoredWallpaperImage() {}
    private static BitmapFactory.Options bounds(byte[] bytes) {
        if(bytes.length==0 || bytes.length>8*1024*1024)throw new IllegalArgumentException("Wallpaper size unavailable");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1 || options.outHeight<1 || options.outWidth>40000 || options.outHeight>40000 || (long)options.outWidth*options.outHeight>200_000_000L ||
                !("image/png".equals(options.outMimeType)||"image/jpeg".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))
            throw new IllegalArgumentException("Wallpaper image unavailable");
        return options;
    }
    public static void validateImport(byte[] bytes) {
        Bitmap image=null;
        try {
            BitmapFactory.Options info=bounds(bytes);
            if(info.outWidth>EDGE || info.outHeight>EDGE || (long)info.outWidth*info.outHeight>PIXELS)
                throw new IllegalArgumentException("Archive wallpaper exceeds normalized image bounds");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inMutable=true;
            image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(image==null || image.getWidth()!=info.outWidth || image.getHeight()!=info.outHeight)
                throw new IllegalArgumentException("Archive wallpaper is not a valid decoded image");
        } catch(OutOfMemoryError exhausted) {
            throw new IllegalArgumentException("Wallpaper decode resources unavailable",exhausted);
        } finally {wipe(image);}
    }
    /** Defend old/local data too. Never allocate the original large image at Home rendering. */
    public static Bitmap decodeStored(byte[] bytes) {
        try {
            BitmapFactory.Options info=bounds(bytes),options=new BitmapFactory.Options();options.inMutable=true;
            int sample=1;while(Math.max(info.outWidth,info.outHeight)/sample>EDGE)sample*=2;
            options.inSampleSize=sample;
            Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(image!=null && (image.getWidth()>EDGE || image.getHeight()>EDGE || (long)image.getWidth()*image.getHeight()>PIXELS)){wipe(image);return null;}
            return image;
        } catch(RuntimeException | OutOfMemoryError unavailable) {return null;}
    }
    private static void wipe(Bitmap image){if(image!=null&&!image.isRecycled()){if(image.isMutable())image.eraseColor(0);image.recycle();}}
}
