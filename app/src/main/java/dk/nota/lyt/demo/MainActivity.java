package dk.nota.lyt.demo;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.videolan.libvlc.util.AndroidUtil;

import java.util.ArrayList;

import dk.nota.lyt.PlaybackService;
import dk.nota.lyt.ServiceHelper;
import dk.nota.lyt.media.MediaWrapper;

public class MainActivity extends AppCompatActivity implements PlaybackService.Client.Callback {

    final private ServiceHelper mHelper = new ServiceHelper(this, this);
    private static final String TAG = MainActivity.class.getCanonicalName();
    public static final String ACTION_SHOW_PLAYER = "ACTION_SHOW_PLAYER";

    private PlaybackService mService;

    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHelper.onStop();
    }

    public ServiceHelper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        if (mService != null) {
            Log.i(TAG, "------ Adding test Media! --------");

            mService.setNotificationActivity(MainActivity.this, "NOTIFICATION_CLICKED");
            ArrayList<MediaWrapper> playlist = new ArrayList<>();
            MediaWrapper media1 = new MediaWrapper(AndroidUtil.LocationToUri("http://www.noiseaddicts.com/samples_1w72b820/4357.mp3"));
            media1.setDisplayTitle("My Display Title");
            media1.setArtist("MyArtist");
            media1.setAlbum("MyAlbum");
            media1.setDescription("My undeniably long-winded unnecessary description");
            media1.setArtworkURL("https://bookcover.nota.dk/714070_w140_h200.jpg");
            playlist.add(media1);
            playlist.add(new MediaWrapper(AndroidUtil.LocationToUri("http://www.noiseaddicts.com/samples_1w72b820/3816.mp3")));
            playlist.add(new MediaWrapper(AndroidUtil.LocationToUri("http://www.noiseaddicts.com/samples_1w72b820/202.mp3")));
            mService.load(playlist, 0);
        }
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
                if (mService.isPlaying())
                    mService.pause();
                else
                    mService.play();
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
                mService.stop();
            }
        });

        CheckBox rateBox = (CheckBox) findViewById(R.id.checkBoxRate);
        rateBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mService.setRate(isChecked ? 2 : 1);
            }
        });
    }
}
