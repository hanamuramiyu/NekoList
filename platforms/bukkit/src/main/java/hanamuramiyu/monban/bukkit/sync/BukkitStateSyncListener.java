package hanamuramiyu.monban.bukkit.sync;

import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.SyncChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Objects;

public final class BukkitStateSyncListener implements PluginMessageListener, Listener {
    private final PlayerAccessStateReceiver receiver;
    private final Runnable stateChanged;

    public BukkitStateSyncListener(PlayerAccessStateReceiver receiver) {
        this(receiver, () -> {
        });
    }

    public BukkitStateSyncListener(PlayerAccessStateReceiver receiver, Runnable stateChanged) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (SyncChannel.ID.equals(channel)) {
            try {
                if (receiver.accept(message)) {
                    stateChanged.run();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public PlayerAccessStateReceiver receiver() {
        return receiver;
    }
}
