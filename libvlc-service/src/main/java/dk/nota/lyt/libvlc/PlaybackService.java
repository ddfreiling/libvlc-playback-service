/*****************************************************************************
 * PlaybackService.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

/**
 * Modified for audio-only use and features needed in the LYT3 project by Nota
 * by Daniel Freiling (ddaf@nota.dk)
 */

package dk.nota.lyt.libvlc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import dk.nota.lyt.libvlc.media.MediaWrapper;
import dk.nota.lyt.libvlc.media.MediaWrapperList;
import dk.nota.lyt.libvlc.media.MediaEvent;
import dk.nota.lyt.libvlc.media.MediaPlayerEvent;

public class PlaybackService extends Service {

    private static final String TAG = PlaybackService.class.getCanonicalName();

    private static final int SHOW_PROGRESS = 0;
    private static final int SHOW_TOAST = 1;
    public static final String ACTION_REMOTE_GENERIC = PlaybackService.class.getPackage().getName() + ".remote.";
    public static final String ACTION_REMOTE_BACKWARD = ACTION_REMOTE_GENERIC+"Backward";
    public static final String ACTION_REMOTE_PLAY = ACTION_REMOTE_GENERIC+"Play";
    public static final String ACTION_REMOTE_PLAYPAUSE = ACTION_REMOTE_GENERIC+"PlayPause";
    public static final String ACTION_REMOTE_PAUSE = ACTION_REMOTE_GENERIC+"Pause";
    public static final String ACTION_REMOTE_STOP = ACTION_REMOTE_GENERIC+"Stop";
    public static final String ACTION_REMOTE_FORWARD = ACTION_REMOTE_GENERIC+"Forward";

    /* Binder for Service RPC */
    private class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }
    public static PlaybackService getService(IBinder iBinder) {
        LocalBinder binder = (LocalBinder) iBinder;
        return binder.getService();
    }

    private SharedPreferences mSettings;
    private final IBinder mBinder = new LocalBinder();
    private MediaWrapperList mMediaList = new MediaWrapperList();
    private MediaPlayer mMediaPlayer;
    private Activity mNotificationActivity;
    private String mNotificationAction;
    private String mMediaListIdentifier;

    final private ArrayList<PlaybackEventHandler> mPlaybackEventHandlers = new ArrayList<>();
    private boolean mDetectHeadset = true;
    private PowerManager.WakeLock mWakeLock;
    private final AtomicBoolean mExpanding = new AtomicBoolean(false);

    // Index management
    private Stack<Integer> mPrevious;
    private int mCurrentIndex; // Set to -1 if no media is currently loaded
    private int mPrevIndex; // Set to -1 if no previous media
    private int mNextIndex; // Set to -1 if no next media

    // Playback management
    MediaSessionCompat mMediaSession;
    protected MediaSessionCallback mSessionCallback;
    private static final long PLAYBACK_ACTIONS = PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    public static final int TYPE_AUDIO = 0;
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;
    private int mRepeating = REPEAT_NONE;
    private boolean mShuffling = false;
    private Random mRandom = null; // Used in shuffling process
    private long mSavedTime = 0l;
    private boolean mHasAudioFocus = false;
    private boolean mParsed = false;
    private boolean mSeekable = false;
    private boolean mPausable = false;
    private CountDownTimer mSleepTimer;
    private int mSleepTimerVolumeFadeDurationMillis = 5000;
    private HashSet<Integer> supportedSeekIntervals = new HashSet<Integer>(Arrays.asList(5, 15, 30, 60));
    private int mSeekIntervalSec = 15;

    /**
     * RemoteControlClient is for lock screen playback control.
     */
    private RemoteControlEventReceiver mRemoteControlClientReceiver = null;
    private ComponentName mRemoteControlClientReceiverComponent;

    private LibVLC LibVLC() {
        ArrayList<String> defaultOptions = new ArrayList<String>();
        if (DefaultOptions.AutoReconnect) {
            defaultOptions.add("--http-reconnect");
        }
        defaultOptions.add("--network-caching="+ DefaultOptions.NetworkCaching);
        defaultOptions.add("--file-caching="+ DefaultOptions.FileCaching);
        return Utils.GetLibVLC(this.getApplicationContext(), defaultOptions);
    }

    private MediaPlayer newMediaPlayer() {
        final MediaPlayer mp = new MediaPlayer(LibVLC());
        return mp;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mMediaPlayer = newMediaPlayer();
        if (!Utils.testCompatibleCPU(this)) {
            stopSelf();
            return;
        }

        mDetectHeadset = mSettings.getBoolean("enable_headset_detection", true);

        mCurrentIndex = -1;
        mPrevIndex = -1;
        mNextIndex = -1;
        mPrevious = new Stack<Integer>();
        mRemoteControlClientReceiverComponent = new ComponentName(this.getApplicationContext(),
                RemoteControlEventReceiver.class.getName());

        // Make sure the audio player will acquire a wake-lock while playing. If we don't do
        // that, the CPU might go to sleep while the song is playing, causing playback to stopService.
        PowerManager pm = (PowerManager) this.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        IntentFilter filter = new IntentFilter();
        filter.setPriority(Integer.MAX_VALUE);
        filter.addAction(ACTION_REMOTE_BACKWARD);
        filter.addAction(ACTION_REMOTE_PLAYPAUSE);
        filter.addAction(ACTION_REMOTE_PLAY);
        filter.addAction(ACTION_REMOTE_PAUSE);
        filter.addAction(ACTION_REMOTE_STOP);
        filter.addAction(ACTION_REMOTE_FORWARD);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        if(ACTION_REMOTE_PLAYPAUSE.equals(intent.getAction())){
            if (hasCurrentMedia())
                return START_STICKY;
            else
                loadLastPlaylist(TYPE_AUDIO);
        } else if (ACTION_REMOTE_PLAY.equals(intent.getAction())) {
            if (hasCurrentMedia())
                play();
            else
                loadLastPlaylist(TYPE_AUDIO);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mRemoteControlClientReceiver != null) {
            unregisterReceiver(mRemoteControlClientReceiver);
            mRemoteControlClientReceiver = null;
        }
        unregisterReceiver(mReceiver);
        mMediaPlayer.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!hasCurrentMedia())
            stopSelf();
        return true;
    }

    public IVLCVout getVLCVout()  {
        return mMediaPlayer.getVLCVout();
    }

    private final OnAudioFocusChangeListener mAudioFocusListener = AndroidUtil.isFroyoOrLater() ?
            createOnAudioFocusChangeListener() : null;

    private OnAudioFocusChangeListener createOnAudioFocusChangeListener() {
        return new OnAudioFocusChangeListener() {
            private boolean mLossTransient = false;
            private boolean mLossTransientCanDuck = false;
            private boolean wasPlaying = false;

            @Override
            public void onAudioFocusChange(int focusChange) {
                /*
                 * Pause playback during alerts and notifications
                 */
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.d(TAG, "AUDIOFOCUS_LOSS");
                        // Pause playback
                        changeAudioFocus(false);
                        pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Pause playback
                        mLossTransient = true;
                        wasPlaying = isPlaying();
                        if (wasPlaying)
                            pause();
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // Lower the volume
                        if (mMediaPlayer.isPlaying()) {
                            mMediaPlayer.setVolume(36);
                            mLossTransientCanDuck = true;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d(TAG, "AUDIOFOCUS_GAIN: " + mLossTransientCanDuck + ", " + mLossTransient);
                        // Resume playback
                        if (mLossTransientCanDuck) {
                            mMediaPlayer.setVolume(100);
                            mLossTransientCanDuck = false;
                        } else if (mLossTransient) {
                            if (wasPlaying)
                                mMediaPlayer.play();
                            mLossTransient = false;
                        }
                        break;
                }
            }
        };
    }

    private void changeAudioFocus(boolean acquire) {
        final AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am == null)
            return;

        if (acquire) {
            if (!mHasAudioFocus) {
                final int result = am.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    am.setParameters("bgm_state=true");
                    mHasAudioFocus = true;
                }
            }
        } else {
            if (mHasAudioFocus) {
                final int result = am.abandonAudioFocus(mAudioFocusListener);
                am.setParameters("bgm_state=false");
                mHasAudioFocus = false;
            }
        }
    }

    @MainThread
    public void setSeekIntervalSeconds(int intervalSeconds) {
        if (!supportedSeekIntervals.contains(intervalSeconds)) {
            throw new IllegalArgumentException("Invalid seek interval. Must be one of: "+
                    Arrays.toString(supportedSeekIntervals.toArray()));
        }
        this.mSeekIntervalSec = intervalSeconds;
        this.showNotification();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private boolean wasPlaying = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra("state", 0);
            if( mMediaPlayer == null ) {
                Log.w(TAG, "Intent received, but VLC is not loaded, skipping.");
                return;
            }

            // skip all headsets events if there is a call
            TelephonyManager telManager = (TelephonyManager) PlaybackService.this.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            if (telManager != null && telManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
                return;

            /*
             * Launch the activity if needed
             */
            if (action.startsWith(ACTION_REMOTE_GENERIC) && !mMediaPlayer.isPlaying() && !hasCurrentMedia()) {
                context.startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            }

            /*
             * Remote / headset control events
             */
            if (action.equalsIgnoreCase(ACTION_REMOTE_PLAYPAUSE)) {
                if (!hasCurrentMedia())
                    return;
                if (mMediaPlayer.isPlaying())
                    pause();
                else
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PLAY)) {
                if (!mMediaPlayer.isPlaying() && hasCurrentMedia())
                    play();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_PAUSE)) {
                if (hasCurrentMedia())
                    pause();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_STOP)) {
                stopService();
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_BACKWARD)) {
                setTime(Math.max(0, getTime() - mSeekIntervalSec * 1000));
            } else if (action.equalsIgnoreCase(ACTION_REMOTE_FORWARD)) {
                setTime(Math.min(getLength(), getTime() + mSeekIntervalSec * 1000));
            } else if (mDetectHeadset) {
                if (action.equalsIgnoreCase(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                    Log.d(TAG, "Headset Removed.");
                    wasPlaying = isPlaying();
                    if (wasPlaying && hasCurrentMedia())
                        pause();
                } else if (action.equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG) && state != 0) {
                    Log.d(TAG, "Headset Inserted.");
                    if (wasPlaying && hasCurrentMedia())
                        play();
                }
            }
        }
    };

    private final Media.EventListener mMediaListener = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
            boolean update = true;
            switch (event.type) {
                case Media.Event.MetaChanged:
                    /* Update Meta if file is already parsed */
                    if (mParsed && updateCurrentMeta(event.getMetaId()))
                        executeUpdate();
                    Log.d(TAG, "Media.Event.MetaChanged: " + event.getMetaId());
                    break;
                case Media.Event.ParsedChanged:
                    Log.d(TAG, "Media.Event.ParsedChanged");
                    updateCurrentMeta(-1);
                    mParsed = true;
                    break;
                default:
                    update = false;

            }
            if (update) {
                for (PlaybackEventHandler handler : mPlaybackEventHandlers) {
                    try {
                        handler.onMediaEvent(new MediaEvent(event));
                    } catch(Exception ex) {
                        Log.d(TAG, "Error notifying PlaybackEventHandlers.onMediaEvent: "+ ex.getMessage());
                    }
                }
                if (mParsed) {
                    showNotification();
                }
            }
        }
    };

    /**
     * Update current media meta and return true if player needs to be updated
     *
     * @param id of the Meta event received, -1 for none
     * @return true if UI needs to be updated
     */
    private boolean updateCurrentMeta(int id) {
        if (id == Media.Meta.Publisher)
            return false;
        final MediaWrapper mw = getCurrentMedia();
        if (mw != null)
            mw.updateMeta(mMediaPlayer);
        return id != Media.Meta.NowPlaying || getCurrentMedia().getNowPlaying() != null;
    }

    private final MediaPlayer.EventListener mMediaPlayerListener = new MediaPlayer.EventListener() {

        private void notifyPlaybackEventHandlers(MediaPlayer.Event event) {
            for (PlaybackEventHandler handler : mPlaybackEventHandlers) {
                try {
                    handler.onMediaPlayerEvent(new MediaPlayerEvent(event));
                } catch(Exception ex) {
                    Log.d(TAG, "Error notifying PlaybackEventHandler.onMediaPlayerEvent: "+ ex.getMessage());
                }
            }
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    Log.d(TAG, "MediaPlayer.Event.Playing");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    mHandler.sendEmptyMessage(SHOW_PROGRESS);
                    changeAudioFocus(true);
                    if (!mWakeLock.isHeld())
                        mWakeLock.acquire();
                    showNotification();
                    break;
                case MediaPlayer.Event.Paused:
                    Log.d(TAG, "MediaPlayer.Event.Paused");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    showNotification();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.Stopped:
                    Log.d(TAG, "MediaPlayer.Event.Stopped");
                    executeUpdate();
                    publishState(event.type);
                    executeUpdateProgress();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    break;
                case MediaPlayer.Event.EndReached:
                    Log.d(TAG, "MediaPlayer.Event.EndReached");
                    executeUpdateProgress();
                    determinePrevAndNextIndices(true);
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    changeAudioFocus(false);
                    // FIX: next() changes mCurrentIndex and can stop service,
                    // so we have to notify event handlers first (and return after next).
                    notifyPlaybackEventHandlers(event);
                    next();
                    return;
                case MediaPlayer.Event.EncounteredError:
                    Log.d(TAG, "MediaPlayer.Event.EncounteredError");
                    executeUpdate();
                    executeUpdateProgress();
                    if (mPausable) {
                        pause();
                    } else {
                        stopPlayback();
                    }
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    break;
                case MediaPlayer.Event.PositionChanged:
                    break;
                case MediaPlayer.Event.Vout:
                    break;
                case MediaPlayer.Event.ESAdded:
                    updateMetadata();
                    break;
                case MediaPlayer.Event.ESDeleted:
                    break;
                case MediaPlayer.Event.PausableChanged:
                    mPausable = event.getPausable();
                    break;
                case MediaPlayer.Event.SeekableChanged:
                    mSeekable = event.getSeekable();
                    break;
            }
            notifyPlaybackEventHandlers(event);
        }
    };

    private final MediaWrapperList.EventListener mListEventListener = new MediaWrapperList.EventListener() {

        @Override
        public void onItemAdded(int index, String mrl) {
            Log.d(TAG, "CustomMediaListItemAdded");
            if(mCurrentIndex >= index && !mExpanding.get())
                mCurrentIndex++;

            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemRemoved(int index, String mrl) {
            Log.d(TAG, "CustomMediaListItemDeleted");
            if (mCurrentIndex == index && !mExpanding.get()) {
                // The current item has been deleted
                mCurrentIndex--;
                determinePrevAndNextIndices();
                if (mNextIndex != -1)
                    next();
                else if (mCurrentIndex != -1) {
                    playIndex(mCurrentIndex, 0);
                } else
                    stopService();
            }

            if(mCurrentIndex > index && !mExpanding.get())
                mCurrentIndex--;
            determinePrevAndNextIndices();
            executeUpdate();
        }

        @Override
        public void onItemMoved(int indexBefore, int indexAfter, String mrl) {
            Log.d(TAG, "CustomMediaListItemMoved");
            if (mCurrentIndex == indexBefore) {
                mCurrentIndex = indexAfter;
                if (indexAfter > indexBefore)
                    mCurrentIndex--;
            } else if (indexBefore > mCurrentIndex
                    && indexAfter <= mCurrentIndex)
                mCurrentIndex++;
            else if (indexBefore < mCurrentIndex
                    && indexAfter > mCurrentIndex)
                mCurrentIndex--;

            // If we are in random mode, we completely reset the stored previous track
            // as their indices changed.
            mPrevious.clear();

            determinePrevAndNextIndices();
            executeUpdate();
        }
    };

    private void executeUpdate() {
        for (PlaybackEventHandler handler : mPlaybackEventHandlers) {
            try {
                handler.update();
            } catch(Exception ex) {
                Log.d(TAG, "Error notifying PlaybackEventHandler.update: "+ ex.getMessage());
            }
        }

        updateMetadata();
    }

    private void executeUpdateProgress() {
        for (PlaybackEventHandler handler : mPlaybackEventHandlers) {
            try {
                handler.updateProgress();
            } catch(Exception ex) {
                Log.d(TAG, "Error notifying PlaybackEventHandler.updateProgress: "+ ex.getMessage());
            }
        }
    }

    /**
     * Return the current media.
     *
     * @return The current media or null if there is not any.
     */
    @Nullable
    private MediaWrapper getCurrentMedia() {
        return mMediaList.getMedia(mCurrentIndex);
    }

    /**
     * Alias for mCurrentIndex >= 0
     *
     * @return True if a media is currently loaded, false otherwise
     */
    private boolean hasCurrentMedia() {
        return mCurrentIndex >= 0 && mCurrentIndex < mMediaList.size();
    }

    private final Handler mHandler = new AudioServiceHandler(this);

    private class AudioServiceHandler extends WeakHandler<PlaybackService> {
        public AudioServiceHandler(PlaybackService fragment) {
            super(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            PlaybackService service = getOwner();
            if (service == null) return;

            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (service.mPlaybackEventHandlers.size() > 0) {
                        removeMessages(SHOW_PROGRESS);
                        service.executeUpdateProgress();
                        sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
                    }
                    break;
                case SHOW_TOAST:
                    final Bundle bundle = msg.getData();
                    final String text = bundle.getString("text");
                    Toast.makeText(PlaybackService.this.getApplicationContext(),
                            text, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private static final int REQ_CODE = 123;
    private static final int NOTIFICATION_ID = 99;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void showNotification() {
        Log.d(TAG, "Update Notification");
        MediaWrapper currentMedia = getCurrentMedia();
        if (currentMedia == null) return;

        PendingIntent piStop = PendingIntent.getBroadcast(this, REQ_CODE, new Intent(ACTION_REMOTE_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piBackward = PendingIntent.getBroadcast(this, REQ_CODE, new Intent(ACTION_REMOTE_BACKWARD), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piPlay = PendingIntent.getBroadcast(this, REQ_CODE, new Intent(ACTION_REMOTE_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piForward = PendingIntent.getBroadcast(this, REQ_CODE, new Intent(ACTION_REMOTE_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT);

        String seekForwardIdStr = String.format("s%d_forw_white", mSeekIntervalSec);
        String seekBackwardIdStr = String.format("s%d_back_white", mSeekIntervalSec);
        int seekForwardId = getResources().getIdentifier(seekForwardIdStr, "drawable", getPackageName());
        int seekBackwardId = getResources().getIdentifier(seekBackwardIdStr, "drawable", getPackageName());

        NotificationCompat.Builder bob = new NotificationCompat.Builder(this);
        bob.addAction(seekBackwardId, getText(R.string.seekBackward), piBackward);
        if (mMediaPlayer.isPlaying()) {
            bob.addAction(R.drawable.pause_small_white, getText(R.string.pause), piPlay);
        } else {
            bob.addAction(R.drawable.play_small_white, this.getText(R.string.play), piPlay);
        }
        bob.addAction(seekForwardId, getText(R.string.seekForward), piForward);

        boolean isPlaying = mMediaPlayer.isPlaying();
//        long playbackPosition = mMediaPlayer.getTime();

        bob.setStyle(new NotificationCompat.MediaStyle()
                .setMediaSession(mMediaSession.getSessionToken())
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(piStop))
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setDeleteIntent(piStop)
            .setContentTitle(currentMedia.getTitle())
            .setTicker(currentMedia.getTitle()); //TODO: used by accessibility services!

        if (currentMedia.getArtist() != null && currentMedia.getAlbum() != null) {
            bob.setContentText(currentMedia.getArtist() + " - " + currentMedia.getAlbum());
        } else if (currentMedia.getArtist() != null) {
            bob.setContentText(currentMedia.getArtist());
        } else if (currentMedia.getAlbum() != null) {
            bob.setContentText(currentMedia.getArtist());
        }

        if (mNotificationActivity != null) {
            Intent onClickIntent = new Intent(this, mNotificationActivity.getClass());
            onClickIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            onClickIntent.setAction(mNotificationAction);
            onClickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent piOnClick = PendingIntent.getActivity(this, REQ_CODE, onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            bob.setContentIntent(piOnClick);
        }

        if (currentMedia.isPictureParsed()) {
            bob.setLargeIcon(currentMedia.getPicture());
        } else if (currentMedia.getArtworkURL() != null) {
            loadMediaArtworkAsync(currentMedia, bob);
        } else {
            bob.setLargeIcon(getDefaultArtwork());
        }

        Notification notification = bob.build();
        if (!AndroidUtil.isLolliPopOrLater() || isPlaying) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }
    }

    private Bitmap mDefaultArtworkBitmap;

    private Bitmap getDefaultArtwork() {
        if (mDefaultArtworkBitmap == null) {
            mDefaultArtworkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_album_artwork);
        }
        return mDefaultArtworkBitmap;
    }

    private void loadMediaArtworkAsync(final MediaWrapper media,
                                       final NotificationCompat.Builder builder) {
        Glide.with(getApplicationContext())
            .load(media.getArtworkURL())
            .asBitmap()
            .fitCenter()
            .placeholder(R.drawable.default_album_artwork)
            .into(new SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                    try {
                        builder.setLargeIcon(resource);
                        NotificationManagerCompat.from(PlaybackService.this)
                                .notify(NOTIFICATION_ID, builder.build());
                        media.setPicture(resource);
                        media.setPictureParsed(true);
                    } catch (Exception ex) {
                        Log.d(TAG, "Failed to set image for media with URL: "+ media.getArtworkURL());
                    }
                }
            });
    }

    private void hideNotification() {
        hideNotification(true);
    }

    /**
     * Hides the VLC notification and stops the service.
     *
     * @param stopPlayback True to also stopService playback at the same time. Set to false to preserve playback (e.g. for vout events)
     */
    private void hideNotification(boolean stopPlayback) {
        stopForeground(true);
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        if(stopPlayback)
            stopSelf();
    }

    /**
     * Sets Activity to show on Notification click
     * @param notificationActivity Activity to send intent to
     * @param action String identifier for action sent with intent
     */
    public <T extends Activity> void setNotificationActivity(T notificationActivity, String action)
    {
        mNotificationActivity = notificationActivity;
        mNotificationAction = action;
    }

    @MainThread
    public void pause() {
        if (mPausable) {
            savePosition();
            mHandler.removeMessages(SHOW_PROGRESS);
            mMediaPlayer.pause();
            broadcastMetadata();
            pauseSleepTimer();
        }
    }

    @MainThread
    public void play() {
        if (!hasCurrentMedia() && mMediaList.size() > 0) {
            playIndex(0);
        } else if (hasCurrentMedia()) {
            mMediaPlayer.play();
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            updateMetadata();
            broadcastMetadata();
        }
        if (mSleepTimer != null && mSleepTimer.isPaused()) {
            mSleepTimer.resume();
        }
    }

    @MainThread
    public void stopPlayback() {
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        if (mMediaPlayer == null)
            return;
        savePosition();
        final Media media = mMediaPlayer.getMedia();
        if (media != null) {
            media.setEventListener(null);
            mMediaPlayer.setEventListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.setMedia(null);
            media.release();
        }
        mMediaList.removeEventListener(mListEventListener);
        mCurrentIndex = -1;
        mPrevious.clear();
        mHandler.removeMessages(SHOW_PROGRESS);
        hideNotification();
        broadcastMetadata();
        executeUpdate();
        executeUpdateProgress();
        changeAudioFocus(false);
        cancelSleepTimer();
    }

    @MainThread
    public void stopService() {
        removeAllCallbacks();
        stopForeground(true);
        stopPlayback();
        stopSelf();
    }

    private void determinePrevAndNextIndices() {
        determinePrevAndNextIndices(false);
    }

    private void determinePrevAndNextIndices(boolean expand) {
        if (expand) {
            mExpanding.set(true);
            mNextIndex = expand();
            mExpanding.set(false);
        } else {
            mNextIndex = -1;
        }
        mPrevIndex = -1;

        if (mNextIndex == -1) {
            // No subitems; play the next item.
            int size = mMediaList.size();
            mShuffling &= size > 2;

            // Repeating once doesn't change the index
            if (mRepeating == REPEAT_ONE) {
                mPrevIndex = mNextIndex = mCurrentIndex;
            } else {

                if(mShuffling) {
                    if(mPrevious.size() > 0)
                        mPrevIndex = mPrevious.peek();
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stopService (depending on RepeatType).
                    if(mPrevious.size() + 1 == size) {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                            return;
                        } else {
                            mPrevious.clear();
                            mRandom = new Random(System.currentTimeMillis());
                        }
                    }
                    if(mRandom == null) mRandom = new Random(System.currentTimeMillis());
                    // Find a new index not in mPrevious.
                    do
                    {
                        mNextIndex = mRandom.nextInt(size);
                    }
                    while(mNextIndex == mCurrentIndex || mPrevious.contains(mNextIndex));

                } else {
                    // normal playback
                    if(mCurrentIndex > 0)
                        mPrevIndex = mCurrentIndex - 1;
                    if(mCurrentIndex + 1 < size)
                        mNextIndex = mCurrentIndex + 1;
                    else {
                        if(mRepeating == REPEAT_NONE) {
                            mNextIndex = -1;
                        } else {
                            mNextIndex = 0;
                        }
                    }
                }
            }
        }
    }

    private void initMediaSession() {
        ComponentName mediaButtonEventReceiver = new ComponentName(this, RemoteControlEventReceiver.class);
        mSessionCallback = new MediaSessionCallback();
        mMediaSession = new MediaSessionCompat(this, "VLCWrapper", mediaButtonEventReceiver, null);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setCallback(mSessionCallback);
        try {
            mMediaSession.setActive(true);
        } catch (NullPointerException e) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport controls.
            mMediaSession.setActive(false);
            mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mMediaSession.setActive(true);
        }
    }

    public MediaSessionCompat.Token getSessionToken() {
        if (mMediaSession == null)
            initMediaSession();
        return mMediaSession.getSessionToken();
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            play();
        }

        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onStop() {
            stopService();
        }

        @Override
        public void onSkipToNext() {
            next();
        }

        @Override
        public void onSkipToPrevious() {
            previous();
        }

        @Override
        public void onSeekTo(long pos) {
            setTime(pos);
        }

        @Override
        public void onFastForward() {
            next();
        }

        @Override
        public void onRewind() {
            previous();
        }
    }

    protected void updateMetadata() {
        MediaWrapper media = getCurrentMedia();
        if (media == null)
            return;
        if (mMediaSession == null)
            initMediaSession();
        String title = media.getNowPlaying();
        if (title == null)
            title = media.getTitle();
        MediaMetadataCompat.Builder bob = new MediaMetadataCompat.Builder();
        bob.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, Utils.getMediaGenre(media))
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, media.getTrackNumber())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, Utils.getMediaArtist(media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, Utils.getMediaReferenceArtist(media))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, Utils.getMediaAlbum(media))
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, media.getArtworkURL())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getLength());
        mMediaSession.setMetadata(bob.build());
    }

    protected void publishState(int state) {
        if (mMediaSession == null)
            return;
        PlaybackStateCompat.Builder bob = new PlaybackStateCompat.Builder();
        bob.setActions(PLAYBACK_ACTIONS);
        switch (state) {
            case MediaPlayer.Event.Playing:
                bob.setState(PlaybackStateCompat.STATE_PLAYING, getCurrentMediaPosition(), getRate());
                break;
            case MediaPlayer.Event.Stopped:
                bob.setState(PlaybackStateCompat.STATE_STOPPED, getCurrentMediaPosition(), getRate());
                break;
            default:
                bob.setState(PlaybackStateCompat.STATE_PAUSED, getCurrentMediaPosition(), getRate());
        }
        PlaybackStateCompat pbState = bob.build();
        mMediaSession.setPlaybackState(pbState);
        mMediaSession.setActive(state != PlaybackStateCompat.STATE_STOPPED);
    }

    private void notifyTrackChanged() {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        updateMetadata();
        broadcastMetadata();
    }

    private void onMediaChanged() {
        notifyTrackChanged();

        saveCurrentMedia();
        determinePrevAndNextIndices();
    }

    private void onMediaListChanged() {
        saveMediaList();
        determinePrevAndNextIndices();
        executeUpdate();
    }

    @MainThread
    public void next() {
        int size = mMediaList.size();

        mPrevious.push(mCurrentIndex);
        mCurrentIndex = mNextIndex;
        if (size == 0 || mCurrentIndex < 0 || mCurrentIndex >= size) {
            if (mCurrentIndex < 0)
                saveCurrentMedia();
            Log.w(TAG, "Warning: invalid next index, aborted !");
            stopService();
            return;
        }
        playIndex(mCurrentIndex, 0);
        saveCurrentMedia();
    }

    @MainThread
    public void previous() {
        int size = mMediaList.size();
        if (hasPrevious() && mCurrentIndex > 0 &&
                (!mMediaPlayer.isSeekable() || mMediaPlayer.getTime() < 5000l)) {
            mCurrentIndex = mPrevIndex;
            if (mPrevious.size() > 0)
                mPrevious.pop();
            if (size == 0 || mPrevIndex < 0 || mCurrentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !");
                stopService();
                return;
            }
        } else
            setPosition(0f);

        playIndex(mCurrentIndex, 0);
        saveCurrentMedia();
    }

    @MainThread
    public void shuffle() {
        if (mShuffling)
            mPrevious.clear();
        mShuffling = !mShuffling;
        savePosition();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void setRepeatType(int repeatType) {
        mRepeating = repeatType;
        savePosition();
        determinePrevAndNextIndices();
    }

    private void broadcastMetadata() {
        MediaWrapper media = getCurrentMedia();
        if (media == null || media.getType() != MediaWrapper.TYPE_AUDIO)
            return;

        boolean playing = mMediaPlayer.isPlaying();

        Intent broadcast = new Intent("com.android.music.metachanged");
        broadcast.putExtra("track", media.getTitle());
        broadcast.putExtra("artist", media.getArtist());
        broadcast.putExtra("album", media.getAlbum());
        broadcast.putExtra("duration", media.getLength());
        broadcast.putExtra("playing", playing);
        sendBroadcast(broadcast);
    }

    public synchronized void loadLastPlaylist(int type) {
        boolean audio = type == TYPE_AUDIO;
        String currentMedia = mSettings.getString(audio ? "current_song" : "current_media", "");
        if (currentMedia.equals(""))
            return;
        String[] locations = mSettings.getString(audio ? "audio_list" : "media_list", "").split(" ");
        if (locations.length == 0)
            return;

        List<String> mediaPathList = new ArrayList<String>(locations.length);
        for (int i = 0 ; i < locations.length ; ++i)
            mediaPathList.add(Uri.decode(locations[i]));

        mShuffling = mSettings.getBoolean(audio ? "audio_shuffling" : "media_shuffling", false);
        mRepeating = mSettings.getInt(audio ? "audio_repeating" : "media_repeating", REPEAT_NONE);
        int position = mSettings.getInt(audio ? "position_in_audio_list" : "position_in_media_list",
                Math.max(0, mediaPathList.indexOf(currentMedia)));
        long time = mSettings.getLong(audio ? "position_in_song" : "position_in_media", -1);
        mSavedTime = time;
        // load playlist
        loadLocations(mediaPathList);
        if (time > 0)
            setTime(time);
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", 0);
        editor.putLong(audio ? "position_in_song" : "position_in_media", 0);
        editor.apply();
    }

    private synchronized void saveCurrentMedia() {
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "current_song" : "current_media", mMediaList.getMRL(Math.max(mCurrentIndex, 0)));
        editor.apply();
    }

    private synchronized void saveMediaList() {
        if (getCurrentMedia() == null)
            return;
        StringBuilder locations = new StringBuilder();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
            locations.append(" ").append(Uri.encode(mMediaList.getMRL(i)));
        }
        //We save a concatenated String because putStringSet is APIv11.
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putString(audio ? "audio_list" : "media_list", locations.toString().trim());
        editor.apply();
    }

    private synchronized void savePosition(){
        if (getCurrentMedia() == null)
            return;
        SharedPreferences.Editor editor = mSettings.edit();
        boolean audio = true;
        for (int i = 0; i < mMediaList.size(); i++) {
            if (mMediaList.getMedia(i).getType() == MediaWrapper.TYPE_VIDEO)
                audio = false;
        }
        editor.putBoolean(audio ? "audio_shuffling" : "media_shuffling", mShuffling);
        editor.putInt(audio ? "audio_repeating" : "media_repeating", mRepeating);
        editor.putInt(audio ? "position_in_audio_list" : "position_in_media_list", mCurrentIndex);
        editor.putLong(audio ? "position_in_song" : "position_in_media", mMediaPlayer.getTime());
        editor.apply();
    }

    private boolean validateLocation(String location)
    {
        /* Check if the MRL contains a scheme */
        if (!location.matches("\\w+://.+"))
            location = "file://".concat(location);
        if (location.toLowerCase(Locale.ENGLISH).startsWith("file://")) {
            /* Ensure the file exists */
            File f;
            try {
                f = new File(new URI(location));
            } catch (URISyntaxException e) {
                return false;
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (!f.isFile())
                return false;
        }
        return true;
    }

    @MainThread
    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    @MainThread
    public boolean isSeekable() {
        return mSeekable;
    }

    @MainThread
    public boolean isPausable() {
        return mPausable;
    }

    @MainThread
    public boolean isShuffling() {
        return mShuffling;
    }

    @MainThread
    public boolean canShuffle()  {
        return (getMediaListSize() > 2) ? true : false;
    }

    @MainThread
    public int getRepeatType() {
        return mRepeating;
    }

    @MainThread
    public boolean hasMedia()  {
        return hasCurrentMedia();
    }

    @MainThread
    public boolean hasPlaylist()  {
        return (getMediaListSize() > 1) ? true : false;
    }

    @MainThread
    public String getAlbum() {
        if (hasCurrentMedia())
            return Utils.getMediaAlbum(getCurrentMedia());
        else
            return null;
    }

    @MainThread
    public String getArtist() {
        if (hasCurrentMedia()) {
            final MediaWrapper media = getCurrentMedia();
            return media.getNowPlaying() != null ?
                    media.getTitle()
                    : Utils.getMediaArtist(media);
        } else
            return null;
    }

    @MainThread
    public String getArtistPrev() {
        if (mPrevIndex != -1)
            return Utils.getMediaArtist(mMediaList.getMedia(mPrevIndex));
        else
            return null;
    }

    @MainThread
    public String getArtistNext() {
        if (mNextIndex != -1)
            return Utils.getMediaArtist(mMediaList.getMedia(mNextIndex));
        else
            return null;
    }

    @MainThread
    public String getTitle() {
        if (hasCurrentMedia())
            return getCurrentMedia().getNowPlaying() != null ? getCurrentMedia().getNowPlaying() : getCurrentMedia().getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitlePrev() {
        if (mPrevIndex != -1)
            return mMediaList.getMedia(mPrevIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public String getTitleNext() {
        if (mNextIndex != -1)
            return mMediaList.getMedia(mNextIndex).getTitle();
        else
            return null;
    }

    @MainThread
    public synchronized void addCallback(PlaybackEventHandler handler) {
        if (!mPlaybackEventHandlers.contains(handler)) {
            mPlaybackEventHandlers.add(handler);
            if (hasCurrentMedia() && isPlaying())
                mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    }

    @MainThread
    public synchronized void removeCallback(PlaybackEventHandler handler) {
        mPlaybackEventHandlers.remove(handler);
    }

    @MainThread
    public synchronized void removeAllCallbacks() {
        mPlaybackEventHandlers.clear();
    }

    @MainThread
    public long getTime() {
        return mMediaPlayer.getTime();
    }

    @MainThread
    public long getLength() {
        return  mMediaPlayer.getLength();
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     */
    @MainThread
    public void loadLocations(List<String> mediaPathList) {
        ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
//        MediaDatabase db = MediaDatabase.getInstance();

        for (int i = 0; i < mediaPathList.size(); i++) {
            String location = mediaPathList.get(i);
            MediaWrapper mediaWrapper = null; //db.getMedia(Uri.parse(location));
            if (mediaWrapper == null) {
                if (!validateLocation(location)) {
                    Log.w(TAG, "Invalid media location " + location);
                    continue;
                }
                Log.v(TAG, "Creating on-the-fly Media object for " + location);
                mediaWrapper = new MediaWrapper(Uri.parse(location));
            }
            mediaList.add(mediaWrapper);
        }
        load(mediaList);
    }

    @MainThread
    public void loadUri(Uri uri) {
        String path = uri.toString();
        if (TextUtils.equals(uri.getScheme(), "content")) {
            path = "file://"+ Utils.getPathFromURI(uri);
        }
        loadLocation(path);
    }

    @MainThread
    public void loadLocation(String mediaPath) {
        loadLocations(Collections.singletonList(mediaPath));
    }

    public void load(List<MediaWrapper> mediaList, String identifier) {
        load(mediaList);
        mMediaListIdentifier = identifier;
    }

    @MainThread
    public void load(List<MediaWrapper> mediaList) {
        Log.v(TAG, "Loading medialist of size: " + mediaList.size());

        if (hasCurrentMedia())
            savePosition();

        mMediaList.removeEventListener(mListEventListener);
        mMediaList.clear();
        mMediaListIdentifier = null;
        MediaWrapperList currentMediaList = mMediaList;

        mPrevious.clear();

        for (int i = 0; i < mediaList.size(); i++) {
            currentMediaList.add(mediaList.get(i));
        }

        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }

        // Add handler after loading the list
        mMediaList.addEventListener(mListEventListener);

        // Autoplay disabled
        //playIndex(mCurrentIndex, 0);
        saveMediaList();
        //onMediaChanged();
    }

    @MainThread
    public void load(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        load(arrayList);
    }

    /**
     * Play a media from the media list (playlist)
     *
     * @param index The index of the media
     * @param flags LibVLC.MEDIA_* flags
     */
    public void playIndex(int index, int flags) {
        if (mMediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !");
            return;
        }
        if (index >= 0 && index < mMediaList.size()) {
            mCurrentIndex = index;
        } else {
            Log.w(TAG, "Warning: index " + index + " out of bounds");
            mCurrentIndex = 0;
        }

        String mrl = mMediaList.getMRL(index);
        if (mrl == null)
            return;
        final MediaWrapper mw = mMediaList.getMedia(index);
        if (mw == null)
            return;

        /* Pausable and seekable are true by default */
        mParsed = false;
        mPausable = mSeekable = true;
        final Media media = new Media(LibVLC(), mw.getUri());
//        VLCOptions.setMediaOptions(media, this, flags | mw.getFlags());
        media.setEventListener(mMediaListener);
        mMediaPlayer.setMedia(media);
        media.release();

        mMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
        changeAudioFocus(true);
        mMediaPlayer.setEventListener(mMediaPlayerListener);
        mMediaPlayer.play();
        if (mSavedTime != 0l)
            mMediaPlayer.setTime(mSavedTime);
        mSavedTime = 0l;

        determinePrevAndNextIndices();
    }

    /**
     * Use this function to play a media inside whatever MediaList LibVLC is following.
     *
     * Unlike load(), it does not import anything into the primary list.
     */
    @MainThread
    public void playIndex(int index) {
        playIndex(index, 0);
    }


    /**
     * Append to the current existing playlist
     */
    @MainThread
    public void append(List<MediaWrapper> mediaList) {
        if (!hasCurrentMedia())
        {
            load(mediaList);
            return;
        }

        for (int i = 0; i < mediaList.size(); i++) {
            MediaWrapper mediaWrapper = mediaList.get(i);
            mMediaList.add(mediaWrapper);
        }
        onMediaListChanged();
    }

    @MainThread
    public void append(MediaWrapper media) {
        ArrayList<MediaWrapper> arrayList = new ArrayList<MediaWrapper>();
        arrayList.add(media);
        append(arrayList);
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    public void moveItem(int positionStart, int positionEnd) {
        mMediaList.move(positionStart, positionEnd);
        PlaybackService.this.saveMediaList();
    }

    @MainThread
    public void insertItem(int position, MediaWrapper mw) {
        mMediaList.insert(position, mw);
        saveMediaList();
        determinePrevAndNextIndices();
    }


    @MainThread
    public void remove(int position) {
        mMediaList.remove(position);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    @MainThread
    public void removeLocation(String location) {
        mMediaList.remove(location);
        saveMediaList();
        determinePrevAndNextIndices();
    }

    public int getMediaListSize() {
        return mMediaList.size();
    }

    @MainThread
    public List<MediaWrapper> getMedias() {
        final ArrayList<MediaWrapper> ml = new ArrayList<MediaWrapper>();
        for (int i = 0; i < mMediaList.size(); i++) {
            ml.add(mMediaList.getMedia(i));
        }
        return ml;
    }

    @MainThread
    public List<String> getMediaLocations() {
        ArrayList<String> medias = new ArrayList<String>();
        for (int i = 0; i < mMediaList.size(); i++) {
            medias.add(mMediaList.getMRL(i));
        }
        return medias;
    }

    @MainThread
    public String getCurrentMediaLocation() {
        return mMediaList.getMRL(mCurrentIndex);
    }

    @MainThread
    public int getCurrentMediaPosition() {
        return mCurrentIndex;
    }

    @MainThread
    public MediaWrapper getCurrentMediaWrapper() {
        return PlaybackService.this.getCurrentMedia();
    }

    @MainThread
    public void setTime(long time) {
        if (mSeekable && getTime() != time)
            mMediaPlayer.setTime(time);
    }

    @MainThread
    public boolean hasNext() {
        return mNextIndex != -1;
    }

    @MainThread
    public boolean hasPrevious() {
        return mPrevIndex != -1;
    }

    @MainThread
    public void detectHeadset(boolean enable)  {
        mDetectHeadset = enable;
    }

    @MainThread
    public float getRate()  {
        return mMediaPlayer.getRate();
    }

    @MainThread
    public void setRate(float rate) {
        mMediaPlayer.setRate(rate);
        // Progress will now move at a different pace - update clients.
        executeUpdateProgress();
    }

    @MainThread
    public void navigate(int where) {
        mMediaPlayer.navigate(where);
    }

    @MainThread
    public MediaPlayer.Chapter[] getChapters(int title) {
        return mMediaPlayer.getChapters(title);
    }

    @MainThread
    public MediaPlayer.Title[] getTitles() {
        return mMediaPlayer.getTitles();
    }

    @MainThread
    public int getChapterIdx() {
        return mMediaPlayer.getChapter();
    }

    @MainThread
    public void setChapterIdx(int chapter) {
        mMediaPlayer.setChapter(chapter);
    }

    @MainThread
    public int getTitleIdx() {
        return mMediaPlayer.getTitle();
    }

    @MainThread
    public void setTitleIdx(int title) {
        mMediaPlayer.setTitle(title);
    }

    @MainThread
    public int getVolume() {
        return mMediaPlayer.getVolume();
    }

    @MainThread
    public int setVolume(int volume) {
        return mMediaPlayer.setVolume(volume);
    }

    @MainThread
    public void setPosition(float pos) {
        if (mSeekable)
            mMediaPlayer.setPosition(pos);
    }

    @MainThread
    public int getAudioTracksCount() {
        return mMediaPlayer.getAudioTracksCount();
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getAudioTracks() {
        return mMediaPlayer.getAudioTracks();
    }

    @MainThread
    public int getAudioTrack() {
        return mMediaPlayer.getAudioTrack();
    }

    @MainThread
    public boolean setAudioTrack(int index) {
        return mMediaPlayer.setAudioTrack(index);
    }

    @MainThread
    public int getVideoTracksCount() {
        return mMediaPlayer.getVideoTracksCount();
    }

    @MainThread
    public boolean addSubtitleTrack(String path) {
        return mMediaPlayer.setSubtitleFile(path);
    }

    @MainThread
    public MediaPlayer.TrackDescription[] getSpuTracks() {
        return mMediaPlayer.getSpuTracks();
    }

    @MainThread
    public int getSpuTrack() {
        return mMediaPlayer.getSpuTrack();
    }

    @MainThread
    public boolean setSpuTrack(int index) {
        return mMediaPlayer.setSpuTrack(index);
    }

    @MainThread
    public int getSpuTracksCount() {
        return mMediaPlayer.getSpuTracksCount();
    }

    @MainThread
    public boolean setAudioDelay(long delay) {
        return mMediaPlayer.setAudioDelay(delay);
    }

    @MainThread
    public long getAudioDelay() {
        return mMediaPlayer.getAudioDelay();
    }

    @MainThread
    public boolean setSpuDelay(long delay) {
        return mMediaPlayer.setSpuDelay(delay);
    }

    @MainThread
    public long getSpuDelay() {
        return mMediaPlayer.getSpuDelay();
    }

    @MainThread
    public void setEqualizer(MediaPlayer.Equalizer equalizer) {
        mMediaPlayer.setEqualizer(equalizer);
    }

    /**
     * Sets a unique identifier for the current media list.
     * Used to easily recognize the list of media being played.
     * @param identifier
     */
    @MainThread
    public void setMediaListIdentifier(String identifier) {
        mMediaListIdentifier = identifier;
    }

    /**
     * Gets a unique identifier for the current media list.
     * Either one previously set explicitly, or a hash based on the medialist's MRLs if not set.
     * If none or empty playlist it returns null.
     * @return
     */
    @MainThread
    public String getMediaListIdentifier() {
        if (mMediaList == null || mMediaList.size() == 0) {
            return null;
        }
        if (mMediaListIdentifier != null) {
            return mMediaListIdentifier;
        }
        return Utils.getHashFromMediaList(mMediaList);
    }

    @MainThread
    public long getSleepTimerRemaining() {
        if (mSleepTimer != null && !mSleepTimer.isFinishedOrCancelled()) {
            return mSleepTimer.getMillisLeftUntilFinished();
        } else {
            return 0;
        }
    }

    @MainThread
    public void cancelSleepTimer() {
        if (mSleepTimer != null && !mSleepTimer.isFinishedOrCancelled()) {
            Log.d(TAG, "SleepTimer cancelled");
            mSleepTimer.cancel();
            notifyEventHandlers(MediaPlayerEvent.SleepTimerChanged);
        }
    }

    @MainThread
    public void pauseSleepTimer() {
        if (mSleepTimer != null && !mSleepTimer.isFinishedOrCancelled()) {
            mSleepTimer.pause();
        }
    }

    @MainThread
    public void resumeSleepTimer() {
        if (mSleepTimer != null && mSleepTimer.isPaused()) {
            mSleepTimer.resume();
        }
    }

    @MainThread
    public void setSleepTimer(long milliseconds) {
        cancelSleepTimer();
        mSleepTimer = new CountDownTimer(milliseconds, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                notifyEventHandlers(MediaPlayerEvent.SleepTimerChanged);
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "SleepTimer reached - Fade volume & pause");
                if (isPlaying()) {
                    new Thread(mFadeOutAndPauseTask).start();
                }
                notifyEventHandlers(MediaPlayerEvent.SleepTimerChanged);
            }
        };
        mSleepTimer.start(true);
        notifyEventHandlers(MediaPlayerEvent.SleepTimerChanged);
        if (this.isPlaying()) {
            this.resumeSleepTimer();
        }
    }

    private void notifyEventHandlers(int eventType) {
        MediaPlayerEvent evt = new MediaPlayerEvent(eventType);
        for (PlaybackEventHandler handler : mPlaybackEventHandlers) {
            try {
                handler.onMediaPlayerEvent(evt);
            } catch (Exception ex) {
                Log.d(TAG, "Error notifying PlaybackEventHandler.onMediaPlayerEvent: " + ex.getMessage());
            }
        }
    }

    @MainThread
    public void setSleepTimerVolumeFadeDuration(int milliseconds) {
        this.mSleepTimerVolumeFadeDurationMillis = milliseconds;
    }

    private Runnable mFadeOutAndPauseTask = new Runnable() {
        private int fadeTickMillis = 250;

        @Override
        public void run() {
            // NOTE: Normal volume is 100, 0 is muted.
            int previousVolume = PlaybackService.this.getVolume();
            while (PlaybackService.this.getVolume() > 0) {
                double decreaseBy = (double)fadeTickMillis / mSleepTimerVolumeFadeDurationMillis * 100.0;
                int newVolume = Math.max(getVolume() - (int)decreaseBy, 0);
                Log.d(TAG, "Fade volume: "+ newVolume);
                setVolume(newVolume);
                try {
                    Thread.sleep(fadeTickMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            PlaybackService.this.pause();
            PlaybackService.this.setVolume(previousVolume);
        }
    };

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    public int expand() {
        final Media media = mMediaPlayer.getMedia();
        if (media == null)
            return -1;
        final MediaList ml = media.subItems();
        media.release();
        int ret;

        if (ml.getCount() > 0) {
            mMediaList.remove(mCurrentIndex);
            for (int i = ml.getCount() - 1; i >= 0; --i) {
                final Media child = ml.getMediaAt(i);
                child.parse();
                mMediaList.insert(mCurrentIndex, new MediaWrapper(child));
                child.release();
            }
            ret = 0;
        } else {
            ret = -1;
        }
        ml.release();
        return ret;
    }

    public void restartMediaPlayer() {
        stopService();
        mMediaPlayer.release();
        mMediaPlayer = newMediaPlayer();
        /* TODO RESUME */
    }

    public abstract class WeakHandler<T> extends Handler {
        private WeakReference<T> mOwner;

        public WeakHandler(T owner) {
            mOwner = new WeakReference<T>(owner);
        }

        public T getOwner() {
            return mOwner.get();
        }
    }
}