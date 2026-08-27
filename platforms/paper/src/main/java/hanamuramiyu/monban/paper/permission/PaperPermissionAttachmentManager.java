package hanamuramiyu.monban.paper.permission;

import hanamuramiyu.monban.access.backend.BackendPermissionService;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public final class PaperPermissionAttachmentManager {
    private final Plugin plugin;
    private final BackendPermissionService permissionService;
    private final Map<UUID, AttachedPlayer> attachments = new ConcurrentHashMap<>();

    public PaperPermissionAttachmentManager(Plugin plugin, BackendPermissionService permissionService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    public void apply(Player player, PlayerIdentity identity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");

        clear(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String node : permissionService.resolveGrantedNodes(identity)) {
            attachment.setPermission(node, true);
        }
        attachments.put(player.getUniqueId(), new AttachedPlayer(player, identity, attachment));
        player.updateCommands();
    }

    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        AttachedPlayer attached = attachments.remove(player.getUniqueId());
        if (attached != null) {
            attached.attachment().remove();
        }
    }

    public void refreshAll() {
        for (AttachedPlayer attached : List.copyOf(attachments.values())) {
            apply(attached.player(), attached.identity());
        }
    }

    public void clearAll() {
        attachments.values().forEach(attached -> attached.attachment().remove());
        attachments.clear();
    }

    public int attachmentCount() {
        return attachments.size();
    }

    private record AttachedPlayer(
            Player player,
            PlayerIdentity identity,
            PermissionAttachment attachment
    ) {
    }
}
