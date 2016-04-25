package dk.nota.lyt;

import android.content.Context;
import android.support.annotation.MainThread;

import java.util.ArrayList;

/**
 * Created by dfg on 20-04-2016.
 */
public class ServiceHelper {

    private ArrayList<PlaybackService.Client.Callback> mFragmentCallbacks = new ArrayList<>();
    final private PlaybackService.Client.Callback mActivityCallback;
    private PlaybackService.Client mClient;
    protected PlaybackService mService;

    public ServiceHelper(Context context, PlaybackService.Client.Callback activityCallback) {
        mClient = new PlaybackService.Client(context, mClientCallback);
        mActivityCallback = activityCallback;
    }

    @MainThread
    public void registerFragment(PlaybackService.Client.Callback connectCb) {
        if (connectCb == null)
            throw new IllegalArgumentException("connectCb can't be null");
        mFragmentCallbacks.add(connectCb);
        if (mService != null)
            connectCb.onConnected(mService);

    }

    @MainThread
    public void unregisterFragment(PlaybackService.Client.Callback connectCb) {
        if (mService != null)
            connectCb.onDisconnected();
        mFragmentCallbacks.remove(connectCb);
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

    private final PlaybackService.Client.Callback mClientCallback = new PlaybackService.Client.Callback() {
        @Override
        public void onConnected(PlaybackService service) {
            mService = service;
            mActivityCallback.onConnected(service);
            for (PlaybackService.Client.Callback connectCb : mFragmentCallbacks)
                connectCb.onConnected(mService);
        }

        @Override
        public void onDisconnected() {
            mService = null;
            mActivityCallback.onDisconnected();
            for (PlaybackService.Client.Callback connectCb : mFragmentCallbacks)
                connectCb.onDisconnected();
        }
    };
}
