package dk.nota.lyt.vlc;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.MainThread;

import org.videolan.libvlc.util.AndroidUtil;

import java.util.ArrayList;

/**
 * Created by dfg on 20-04-2016.
 */
public class PlaybackServiceHelper {

    private ArrayList<PlaybackService.Client.ConnectionCallback> callbacks = new ArrayList<>();
    private PlaybackService.Client mClient;
    protected PlaybackService mService;

    public PlaybackServiceHelper(Context context) {
        mClient = new PlaybackService.Client(context, mClientCallback);
    }

    public PlaybackServiceHelper(Context context, PlaybackService.Client.ConnectionCallback callback) {
        this(context);
        registerConnectionCallback(callback);
    }

    @MainThread
    public void registerConnectionCallback(PlaybackService.Client.ConnectionCallback callback) {
        if (callback == null)
            throw new IllegalArgumentException("callback must not be null");
        callbacks.add(callback);
        if (mService != null)
            callback.onConnected(mService);

    }

    @MainThread
    public void unregisterConnectionCallback(PlaybackService.Client.ConnectionCallback callback) {
        if (mService != null)
            callback.onDisconnected();
        callbacks.remove(callback);
    }

    @MainThread
    public void onStart() {
        mClient.connect();
    }

    @MainThread
    public void onStop() {
        mClientCallback.onDisconnected();
        mClient.disconnect();
    }

    private final PlaybackService.Client.ConnectionCallback mClientCallback = new PlaybackService.Client.ConnectionCallback() {
        @Override
        public void onConnected(PlaybackService service) {
            mService = service;
            for (PlaybackService.Client.ConnectionCallback callback : callbacks)
                callback.onConnected(mService);
        }

        @Override
        public void onDisconnected() {
            mService = null;
            for (PlaybackService.Client.ConnectionCallback callback : callbacks)
                callback.onDisconnected();
        }
    };
}
