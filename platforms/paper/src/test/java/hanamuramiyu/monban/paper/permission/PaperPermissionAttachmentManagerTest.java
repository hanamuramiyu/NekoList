package hanamuramiyu.monban.paper.permission;

import hanamuramiyu.monban.access.backend.BackendPermissionService;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.SyncSecret;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperPermissionAttachmentManagerTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final PlayerIdentity IDENTITY = PlayerIdentity.online("Miyu", UUID_ONE);

    @Test
    void appliesAndRemovesAttachment() {
        Player player = player();
        PaperPermissionAttachmentManager manager = new PaperPermissionAttachmentManager(
                plugin(),
                new BackendPermissionService(
                        new PlayerAccessResolver(
                                new InMemoryAccessGrantRepository(),
                                new InMemoryPlayerGroupRepository(),
                                new InMemoryPlayerGroupAssignmentRepository(),
                                new InMemoryPlayerPermissionGrantRepository()
                        ),
                        new ServerGroupCatalog(java.util.List.of()),
                        "survival"
                )
        );

        manager.apply(player, IDENTITY);
        assertEquals(1, manager.attachmentCount());
        manager.clear(player);
        assertEquals(0, manager.attachmentCount());
    }

    @Test
    void refreshesExistingAttachmentFromRemoteSnapshot() {
        PlayerAccessStateReceiver receiver = receiver();
        AtomicReference<PermissionAttachment> currentAttachment = new AtomicReference<>();
        Player player = player(currentAttachment);
        PaperPermissionAttachmentManager manager = new PaperPermissionAttachmentManager(
                plugin(),
                new BackendPermissionService(
                        new PlayerAccessResolver(
                                new InMemoryAccessGrantRepository(),
                                new InMemoryPlayerGroupRepository(),
                                new InMemoryPlayerGroupAssignmentRepository(),
                                new InMemoryPlayerPermissionGrantRepository()
                        ),
                        new ServerGroupCatalog(List.of()),
                        "survival",
                        receiver
                )
        );

        manager.apply(player, IDENTITY);
        accept(receiver);
        manager.refreshAll();

        assertEquals(Boolean.TRUE, currentAttachment.get().getPermissions().get("coreprotect.inspect"));
        assertEquals(1, manager.attachmentCount());
    }

    private static Player player() {
        return player(new AtomicReference<>());
    }

    private static Player player(AtomicReference<PermissionAttachment> currentAttachment) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> UUID_ONE;
                    case "addAttachment" -> {
                        PermissionAttachment attachment = new PermissionAttachment(
                                plugin(),
                                (org.bukkit.permissions.Permissible) proxy
                        );
                        currentAttachment.set(attachment);
                        yield attachment;
                    }
                    case "removeAttachment", "recalculatePermissions" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static PlayerAccessStateReceiver receiver() {
        return new PlayerAccessStateReceiver(
                new PlayerAccessStateCodec(),
                secret()
        );
    }

    private static void accept(PlayerAccessStateReceiver receiver) {
        receiver.accept(new PlayerAccessStateCodec().encode(
                new PlayerAccessStateSnapshot(
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new hanamuramiyu.monban.access.permission.PlayerPermissionGrant(
                                IDENTITY,
                                PermissionGrant.server("survival", "coreprotect.inspect")
                        ))
                ),
                secret()
        ));
    }

    private static SyncSecret secret() {
        return SyncSecret.fromBase64(Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        ));
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabled" -> true;
                    case "getDescription" -> new PluginDescriptionFile("monban", "1.0", "test.Plugin");
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
