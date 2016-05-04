package dk.nota.lyt.libvlc;

/**
 * Created by dfg on 02-05-2016.
 */
public interface ConnectionCallback {
    void onConnected(PlaybackService service);
    void onDisconnected();
}
