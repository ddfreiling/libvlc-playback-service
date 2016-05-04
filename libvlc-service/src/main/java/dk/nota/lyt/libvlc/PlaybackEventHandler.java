package dk.nota.lyt.libvlc;

import dk.nota.lyt.libvlc.media.MediaPlayerEvent;
import dk.nota.lyt.libvlc.media.MediaEvent;

/**
 * Created by dfg on 02-05-2016.
 */
public interface PlaybackEventHandler {
    void update();
    void updateProgress();
    void onMediaEvent(MediaEvent event);
    void onMediaPlayerEvent(MediaPlayerEvent event);
}
