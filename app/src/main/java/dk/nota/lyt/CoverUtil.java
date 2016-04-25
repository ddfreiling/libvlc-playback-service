/*
package dk.nota.lyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;

import java.io.File;

import dk.nota.lyt.media.MediaWrapper;

*
 * Created by dfg on 19-04-2016.


public class CoverUtil {

    private static final String TAG = CoverUtil.class.getCanonicalName();

    public static boolean hasExternalStorage() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public synchronized static Bitmap getCover(Context context, MediaWrapper media, int width) {
        BitmapCache cache = BitmapCache.getInstance(context);
        String coverPath = null;
        Bitmap cover = null;
        String cachePath = null;
        File cacheFile = null;

        if (width <= 0) {
            Log.e(TAG, "Invalid cover width requested");
            return null;
        }

        // if external storage is not available, skip covers to prevent slow audio browsing
        if (!hasExternalStorage())
            return null;

        try {
            // try to load from cache
            if (media.getArtist() != null && media.getAlbum() != null) {
                cachePath = getCoverCachePath(context, media, width);

                // try to get the cover from the LRUCache first
                cover = cache.getBitmapFromMemCache(cachePath);
                if (cover != null)
                    return cover;

                // try to get the cover from the storage cache
                cacheFile = new File(cachePath);
                if (cacheFile.exists()) {
                    if (cacheFile.length() > 0)
                        coverPath = cachePath;
                    else
                        return null;
                }
            }

            // try to get it from VLC
            if (coverPath == null || !cacheFile.exists())
                coverPath = getCoverFromVlc(context, media);

            // try to get the cover from android MediaStore
            if (coverPath == null || !(new File(coverPath)).exists())
                coverPath = getCoverFromMediaStore(context, media);

            // no found yet, looking in folder
            if (coverPath == null || !(new File(coverPath)).exists())
                coverPath = getCoverFromFolder(media);

            // read (and scale?) the bitmap
            cover = readCoverBitmap(coverPath, width);

            // store cover into both cache
            if (cachePath != null) {
                writeBitmap(cover, cachePath);
                cache.addBitmapToMemCache(cachePath, cover);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return cover;
    }

    private static String getCoverCachePath(Context context, MediaWrapper media, int width) {
        final int hash = MurmurHash.hash32(Utils.getMediaArtist(context, media) + Utils.getMediaAlbum(context, media));
        return COVER_DIR + (hash >= 0 ? "" + hash : "m" + (-hash)) + "_" + width;
    }

    private static Bitmap readCoverBitmap(Context context, String path, int dipWidth) {
        Bitmap cover = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int width = convertDpToPx(displayMetrics, dipWidth);

 Get the resolution of the bitmap without allocating the memory

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        if (options.outWidth > 0 && options.outHeight > 0) {
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;

            // Find the best decoding scale for the bitmap
            while( options.outWidth / options.inSampleSize > width)
                options.inSampleSize = options.inSampleSize * 2;

            // Decode the file (with memory allocation this time)
            cover = BitmapFactory.decodeFile(path, options);
        }

        return cover;
    }

    public static int convertPxToDp(DisplayMetrics displayMetrics, int px) {
        float logicalDensity = displayMetrics.density;
        int dp = Math.round(px / logicalDensity);
        return dp;
    }

    public static int convertDpToPx(DisplayMetrics displayMetrics, int dp) {
        return Math.round(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics)
        );
    }
}
*/
