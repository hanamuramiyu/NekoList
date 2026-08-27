package hanamuramiyu.monban.paper.permission;

import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class PaperPermissionAttachmentListener implements Listener {
    private final PlayerIdentityResolver identityResolver;
    private final boolean platformAuthenticated;
    private final PaperPermissionAttachmentManager attachmentManager;

    public PaperPermissionAttachmentListener(
            PlayerIdentityResolver identityResolver,
            boolean platformAuthenticated,
            PaperPermissionAttachmentManager attachmentManager
    ) {
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        this.attachmentManager = Objects.requireNonNull(attachmentManager, "attachmentManager");
        this.platformAuthenticated = platformAuthenticated;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        attachmentManager.apply(
                player,
                identityResolver.resolveBackend(
                        player.getName(),
                        player.getUniqueId(),
                        platformAuthenticated
                )
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        attachmentManager.clear(event.getPlayer());
    }
}
