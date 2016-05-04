package dk.nota.lyt.libvlc.media;

import org.videolan.libvlc.MediaPlayer;

/**
 * Created by dfg on 02-05-2016.
 */
public class MediaPlayerEventType {
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
}
