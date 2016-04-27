package dk.nota.lyt;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.util.VLCUtil;

import dk.nota.lyt.media.MediaWrapper;

/**
 * Created by dfg on 18-04-2016.
 */
public class Utils {

    private static final String TAG = Utils.class.getCanonicalName();
    private static LibVLC sLibVLC;

    public synchronized static LibVLC GetLibVLC(Context ctx, ArrayList<String> vlcOptions) throws IllegalStateException {
        if (sLibVLC == null) {
            if (!VLCUtil.hasCompatibleCPU(ctx)) {
                StringBuilder abiList = new StringBuilder();
                for (String arch : VLCUtil.getABIList()) {
                    abiList.append(" " + arch);
                }
                throw new IllegalStateException("LibVLC found no compatible device CPU. ABIs available:" + abiList);
            }
            sLibVLC = new LibVLC(vlcOptions);
            LibVLC.setOnNativeCrashListener(new LibVLC.OnNativeCrashListener() {
                @Override
                public void onNativeCrash() {
                    Log.e(TAG, "Native crash in LibVLC on PID "+ android.os.Process.myPid());
                }
            });
        }
        return sLibVLC;
    }

    public static synchronized boolean testCompatibleCPU(Context context) {
        if (sLibVLC == null && !VLCUtil.hasCompatibleCPU(context)) {
            return false;
        } else
            return true;
    }

    public static String getMediaArtist(MediaWrapper media) {
        final String artist = media.getArtist();
        return artist != null ? artist : "Unknown Artist";
    }

    public static String getMediaReferenceArtist(MediaWrapper media) {
        final String artist = media.getReferenceArtist();
        return artist != null ? artist : "Unknown Artist";
    }

    public static String getMediaAlbumArtist(MediaWrapper media) {
        final String albumArtist = media.getAlbumArtist();
        return albumArtist != null ? albumArtist : "Unknown Artist";
    }

    public static String getMediaAlbum(MediaWrapper media) {
        final String album = media.getAlbum();
        return album != null ? album : "Unknown Album";
    }

    public static String getMediaGenre(MediaWrapper media) {
        final String genre = media.getGenre();
        return genre != null ? genre : "Unknown Genre";
    }

    public static String getMediaSubtitle(MediaWrapper media) {
        if (media.getType() == MediaWrapper.TYPE_AUDIO)
            return media.getNowPlaying() != null
                    ? media.getNowPlaying()
                    : getMediaArtist(media) + " - " + getMediaAlbum(media);
        else
            return "";
    }

    public static String getMediaTitle(MediaWrapper mediaWrapper){
        String title = mediaWrapper.getTitle();
        if (title == null)
            title = getFileNameFromPath(mediaWrapper.getLocation());
        return title;
    }

    public static String getFileNameFromPath(String path){
        if (path == null)
            return "";
        int index = path.lastIndexOf('/');
        if (index> -1)
            return path.substring(index+1);
        else
            return path;
    }

    public static String getPathFromURI(Uri uri) {
        return null;
    }
}
