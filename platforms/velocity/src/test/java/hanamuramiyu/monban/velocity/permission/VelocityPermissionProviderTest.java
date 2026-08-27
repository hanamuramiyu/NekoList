package hanamuramiyu.monban.velocity.permission;

import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VelocityPermissionProviderTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void grantsNetworkServerGroupAndServerPermissions() {
        PlayerIdentity identity = PlayerIdentity.online("Miyu", UUID_ONE);
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(),
                List.of(
                        PermissionGrant.network("proxy.node"),
                        PermissionGrant.serverGroup("public", "group.node"),
                        PermissionGrant.server("survival", "server.node")
                )
        ));
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));

        PlayerAccessResolver resolver = new PlayerAccessResolver(
                new InMemoryAccessGrantRepository(),
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        AtomicReference<Optional<ServerConnection>> currentServer = new AtomicReference<>(
                Optional.of(serverConnection("survival"))
        );
        Player player = player(identity, currentServer);
        registry.stage(player, identity);
        registry.activate(player).orElseThrow();

        PermissionFunction function = new VelocityPermissionProvider(
                delegatedProvider(),
                resolver,
                registry,
                new ServerGroupCatalog(List.of(new ServerGroupDefinition("public", List.of("survival"))))
        ).createFunction(player);

        assertEquals(Tristate.TRUE, function.getPermissionValue("proxy.node"));
        assertEquals(Tristate.TRUE, function.getPermissionValue("group.node"));
        assertEquals(Tristate.TRUE, function.getPermissionValue("server.node"));
        assertEquals(Tristate.UNDEFINED, function.getPermissionValue("missing.node"));
    }

    @Test
    void serverScopedPermissionChangesWithCurrentServer() {
        PlayerIdentity identity = PlayerIdentity.online("Miyu", UUID_ONE);
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(),
                List.of(PermissionGrant.server("survival", "server.node"))
        ));
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                new InMemoryAccessGrantRepository(),
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        AtomicReference<Optional<ServerConnection>> currentServer = new AtomicReference<>(
                Optional.of(serverConnection("survival"))
        );
        Player player = player(identity, currentServer);
        registry.stage(player, identity);
        registry.activate(player).orElseThrow();
        PermissionFunction function = new VelocityPermissionProvider(
                delegatedProvider(),
                resolver,
                registry,
                new ServerGroupCatalog(List.of())
        ).createFunction(player);

        assertEquals(Tristate.TRUE, function.getPermissionValue("server.node"));
        currentServer.set(Optional.of(serverConnection("lobby")));
        assertEquals(Tristate.UNDEFINED, function.getPermissionValue("server.node"));
    }

    @Test
    void preservesDelegatedPermissionsAndDoesNotGrantBeforeIdentityIsKnown() {
        PlayerIdentity identity = PlayerIdentity.online("Miyu", UUID_ONE);
        Player player = player(identity, new AtomicReference<>(Optional.empty()));
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        PermissionFunction function = new VelocityPermissionProvider(
                delegatedProvider(),
                new PlayerAccessResolver(
                        new InMemoryAccessGrantRepository(),
                        new InMemoryPlayerGroupRepository(),
                        new InMemoryPlayerGroupAssignmentRepository(),
                        new InMemoryPlayerPermissionGrantRepository()
                ),
                registry,
                new ServerGroupCatalog(List.of())
        ).createFunction(player);

        assertEquals(Tristate.TRUE, function.getPermissionValue("existing.node"));
        assertEquals(Tristate.UNDEFINED, function.getPermissionValue("monban.node"));
    }

    private static PermissionProvider delegatedProvider() {
        return subject -> node -> node.equals("existing.node")
                ? Tristate.TRUE
                : Tristate.UNDEFINED;
    }

    private static Player player(
            PlayerIdentity identity,
            AtomicReference<Optional<ServerConnection>> currentServer
    ) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUsername" -> identity.name();
                    case "getUniqueId" -> identity.technicalUuid().orElseThrow();
                    case "getCurrentServer" -> currentServer.get();
                    case "toString" -> "PlayerStub[" + identity.name() + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static ServerConnection serverConnection(String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return (ServerConnection) Proxy.newProxyInstance(
                ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "toString" -> "ServerConnectionStub[" + name + "]";
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
