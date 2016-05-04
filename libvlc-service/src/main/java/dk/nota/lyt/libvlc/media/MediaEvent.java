package dk.nota.lyt.libvlc.media;

import org.videolan.libvlc.Media;

/**
 * Created by dfg on 02-05-2016.
 */
public class MediaEvent {

    public static final int MetaChanged = 0;
    public static final int SubItemAdded = 1;
    public static final int DurationChanged = 2;
    public static final int ParsedChanged = 3;
    //public static final int Freed                      = 4;
    public static final int StateChanged = 5;
    public static final int SubItemTreeAdded = 6;
    public static final int ParsedStatus = 7;

    public final int type;
    public final int metaId;

    public MediaEvent(Media.Event event) {
        type = event.type;
        metaId = event.getMetaId();
    }
}
