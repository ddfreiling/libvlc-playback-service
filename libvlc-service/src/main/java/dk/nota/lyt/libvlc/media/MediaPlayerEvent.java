package dk.nota.lyt.libvlc.media;

import org.videolan.libvlc.MediaPlayer;

/**
 * Created by dfg on 02-05-2016.
 */
public class MediaPlayerEvent {

    public static final int MediaChanged        = 0x100;
    //public static final int NothingSpecial      = 0x101;
    public static final int Opening             = 0x102;
    //public static final int Buffering           = 0x103;
    public static final int Playing             = 0x104;
    public static final int Paused              = 0x105;
    public static final int Stopped             = 0x106;
    //public static final int Forward             = 0x107;
    //public static final int Backward            = 0x108;
    public static final int EndReached          = 0x109;
    public static final int EncounteredError    = 0x10a;
    public static final int TimeChanged         = 0x10b;
    public static final int PositionChanged     = 0x10c;
    public static final int SeekableChanged     = 0x10d;
    public static final int PausableChanged     = 0x10e;
    //public static final int TitleChanged        = 0x10f;
    //public static final int SnapshotTaken       = 0x110;
    //public static final int LengthChanged       = 0x111;
    public static final int Vout                = 0x112;
    //public static final int ScrambledChanged    = 0x113;
    public static final int ESAdded             = 0x114;
    public static final int ESDeleted           = 0x115;

    // Custom non-VLC events
    public static final int SleepTimerReached   = 0x200;

    public final int type;
    private long arg1 = 0;
    private float arg2 = 0;

    public MediaPlayerEvent(MediaPlayer.Event event) {
        this.type = event.type;
        this.arg1 = event.getTimeChanged();
        this.arg2 = event.getPositionChanged();
    }

    public MediaPlayerEvent(int type, long arg1, float arg2) {
        this.type = type;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public MediaPlayerEvent(int type) {
        this.type = type;
    }

    public long getTimeChanged() {
        return arg1;
    }
    public float getPositionChanged() {
        return arg2;
    }
    public int getVoutCount() {
        return (int) arg1;
    }
    public int getEsChangedType() {
        return (int) arg1;
    }
    public boolean getPausable() {
        return arg1 != 0;
    }
    public boolean getSeekable() {
        return arg1 != 0;
    }
}
