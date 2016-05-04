package dk.nota.lyt.libvlc;

import android.content.Context;
import android.support.annotation.MainThread;

import java.util.ArrayList;

/**
 * Created by dfg on 20-04-2016.
 */
public class PlaybackServiceHelper {

    private ArrayList<ConnectionCallback> callbacks = new ArrayList<>();
    private PlaybackServiceClient mClient;
    protected PlaybackService mService;

    public PlaybackServiceHelper(Context context) {
        mClient = new PlaybackServiceClient(context, mClientCallback);
    }

    public PlaybackServiceHelper(Context context, ConnectionCallback callback) {
        this(context);
        registerConnectionCallback(callback);
    }

    @MainThread
    public void registerConnectionCallback(ConnectionCallback callback) {
        if (callback == null)
            throw new IllegalArgumentException("callback must not be null");
        callbacks.add(callback);
        if (mService != null)
            callback.onConnected(mService);

    }

    @MainThread
    public void unregisterConnectionCallback(ConnectionCallback callback) {
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

    private final ConnectionCallback mClientCallback = new ConnectionCallback() {
        @Override
        public void onConnected(PlaybackService service) {
            mService = service;
            for (ConnectionCallback callback : callbacks)
                callback.onConnected(mService);
        }

        @Override
        public void onDisconnected() {
            mService = null;
            for (ConnectionCallback callback : callbacks)
                callback.onDisconnected();
        }
    };
}
