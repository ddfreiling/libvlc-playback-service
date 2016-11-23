package dk.nota.lyt.demo;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;

import dk.nota.lyt.libvlc.ConnectionCallback;
import dk.nota.lyt.libvlc.DefaultOptions;
import dk.nota.lyt.libvlc.PlaybackEventHandler;
import dk.nota.lyt.libvlc.PlaybackService;
import dk.nota.lyt.libvlc.PlaybackServiceHelper;
import dk.nota.lyt.libvlc.Utils;
import dk.nota.lyt.libvlc.media.MediaEvent;
import dk.nota.lyt.libvlc.media.MediaPlayerEvent;
import dk.nota.lyt.libvlc.media.MediaWrapper;

public class MainActivity extends AppCompatActivity implements ConnectionCallback {

    final private PlaybackServiceHelper mHelper = new PlaybackServiceHelper(this, this);
    private static final String TAG = MainActivity.class.getCanonicalName();
    public static final String OPENED_FROM_NOTIFICATION = "OPENED_FROM_NOTIFICATION";

    private PlaybackService mService;

    @Override
    protected void onStart() {
        super.onStart();

        DefaultOptions.FileCaching = 5000;
        DefaultOptions.NetworkCaching = 5000;
        mHelper.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHelper.onStop();
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        if (mService != null) {
            Log.i(TAG, "--- Connected to: PlaybackService ---");
            Log.i(TAG, "Current playlist identifier: "+ mService.getMediaListIdentifier());

            mService.setNotificationActivity(MainActivity.this, OPENED_FROM_NOTIFICATION);
            ArrayList<MediaWrapper> playlist = new ArrayList<>();
            MediaWrapper media1 = GetMedia("http://www.noiseaddicts.com/samples_1w72b820/4357.mp3",
                    "Skyggeforbandelsen", "Helene Tegtmeier", "Del 1 af 2",
                    "http://bookcover.nota.dk/714070_w140_h200.jpg");
            MediaWrapper media2 = GetMedia("http://www.noiseaddicts.com/samples_1w72b820/4357.mp3",
                    "Skyggeforbandelsen", "Helene Tegtmeier", "Del 2 af 2",
                    "http://bookcover.nota.dk/714070_w140_h200.jpg");
            MediaWrapper media3 = GetMedia("http://www.moviesoundclips.net/download.php?id=3706&ft=mp3",
                    "Gangsta rap", "Benjamin Zephaniah", "ALBUM",
                    null);
            playlist.add(media1);
            playlist.add(media2);
            playlist.add(media3);
//            playlist.add(new MediaWrapper(AndroidUtil.LocationToUri("http://www.noiseaddicts.com/samples_1w72b820/3816.mp3")));
//            playlist.add(new MediaWrapper(AndroidUtil.LocationToUri("http://www.noiseaddicts.com/samples_1w72b820/202.mp3")));
            String ident = mService.getMediaListIdentifier();
            if (ident == null || !ident.equals("123456")) {
                mService.load(playlist);
                mService.setMediaListIdentifier("123456");
            }
            mService.removeAllCallbacks();
            mService.addCallback(eventHandler);
        }
    }

    private PlaybackEventHandler eventHandler = new PlaybackEventHandler() {
        @Override
        public void update() {
            Log.d(TAG, "Update");
        }

        @Override
        public void updateProgress() {
            Log.d(TAG, "UpdateProgress");
        }

        @Override
        public void onMediaEvent(MediaEvent event) {
            Log.d(TAG, "MediaEvent: " + event.type);
        }

        @Override
        public void onMediaPlayerEvent(MediaPlayerEvent event) {
            Log.d(TAG, "MediaPlayerEvent: " + event.type);
            if (event.type == MediaPlayerEvent.SleepTimerChanged) {
                Log.d(TAG, "SleepTimerChanged: " + mService.getSleepTimerRemaining());
            }
        }
    };

    private MediaWrapper GetMedia(String mediaUrl, String displayTitle, String artist, String album, String artworkUrl) {
        MediaWrapper media = new MediaWrapper(Utils.LocationToUri(mediaUrl));
        media.setDisplayTitle(displayTitle);
        media.setArtist(artist);
        media.setAlbum(album);
        media.setArtworkURL(artworkUrl);
        return media;
    }

    @Override
    public void onDisconnected() {
        mService = null;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent().getAction() != null) {
            Log.i(TAG, "Received intent action: "+ getIntent().getAction());
        }

        findViewById(R.id.btnPlayPause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
            }
        });

        findViewById(R.id.btnPlayIdx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.playIndex(1);
                mService.setTime(10000);
            }
        });

        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.next();
            }
        });

        findViewById(R.id.btnPrevious).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.previous();
            }
        });

        findViewById(R.id.btnStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.stopService();
            }
        });

        CheckBox rateBox = (CheckBox) findViewById(R.id.checkBoxRate);
        rateBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mService.setRate(isChecked ? 2 : 1);
            }
        });

        SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "Seekbar: "+ progress);
                float playbackRate = 2.0f / 100 * progress;
                Log.d(TAG, "NewRate: "+ playbackRate);
                mService.setRate(playbackRate);
                TextView label = (TextView) findViewById(R.id.labelPlaybackRate);
                label.setText("Playback Rate: "+ playbackRate);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        findViewById(R.id.btnSleep).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.setSleepTimer(5000);
            }
        });

        findViewById(R.id.btnReload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelper.onStop();
                mHelper.onStart();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy, app closing");
    }
}
