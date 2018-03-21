package dk.nota.lyt.libvlc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.MainThread;
import android.support.v4.content.ContextCompat;

/**
 * Created by dfg on 02-05-2016.
 */
public class PlaybackServiceClient {
    public static final String TAG = PlaybackServiceClient.class.getCanonicalName();

    private boolean mBound = false;
    private final ConnectionCallback mConnectionCallback;
    private final Context mContext;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            if (!mBound)
                return;

            final PlaybackService service = PlaybackService.getService(iBinder);
            if (service != null)
                mConnectionCallback.onConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mConnectionCallback.onDisconnected();
        }
    };

    private static Intent getServiceIntent(Context context) {
        return new Intent(context, PlaybackService.class);
    }

    private static void startService(Context context) {
        // TODO: We may need to use ContextCompat.startForegroundService >= Android Oreo in future
        //       Right now we don't, as this service is started exclusively from a foreground app.
        //       See https://developer.android.com/about/versions/oreo/background.html
        context.startService(getServiceIntent(context));
    }

    private static void stopService(Context context) {
        context.stopService(getServiceIntent(context));
    }

    public PlaybackServiceClient(Context context, ConnectionCallback connectionCallback) {
        if (context == null || connectionCallback == null)
            throw new IllegalArgumentException("Context and connectionCallback can't be null");
        mContext = context;
        mConnectionCallback = connectionCallback;
    }

    @MainThread
    public void connect() {
        if (mBound)
            throw new IllegalStateException("already connected");
        startService(mContext);
        mBound = mContext.bindService(
            getServiceIntent(mContext),
            mServiceConnection,
            Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT
        );
    }

    @MainThread
    public void disconnect() {
        if (mBound) {
            mBound = false;
            mContext.unbindService(mServiceConnection);
        }
    }

    public static void restartService(Context context) {
        stopService(context);
        startService(context);
    }
}
