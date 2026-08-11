package hanamuramiyu.monban.velocity.backend;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.backend.BackendAdmissionService;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityBackendAdmissionListenerTest {
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void listenerRunsAtLatestPriorityWithoutRequestingAsyncDispatch() throws NoSuchMethodException {
        Subscribe subscribe = VelocityBackendAdmissionListener.class
                .getMethod("onServerPreConnect", ServerPreConnectEvent.class)
                .getAnnotation(Subscribe.class);

        assertEquals((int) Short.MIN_VALUE, (int) subscribe.priority());
        assertFalse(subscribe.async());
    }

    @Test
    void alreadyDeniedEventIsPreservedWithoutAdmission() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        RecordingLogger loggerState = new RecordingLogger();
        AccessGrantLookup explodingLookup = (scope, identity) -> {
            throw new AssertionError("Grant lookup must not run for an already denied event.");
        };
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                explodingLookup,
                new ServerGroupCatalog(List.of()),
                new VelocityConnectionIdentityRegistry(),
                loggerState.logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("lobby"));
        ServerPreConnectEvent.ServerResult denied = ServerPreConnectEvent.ServerResult.denied();
        event.setResult(denied);

        listener.onServerPreConnect(event);

        assertSame(denied, event.getResult());
        assertEquals(0, playerState.disconnectCalls);
        assertEquals(0, playerState.messageCalls);
        assertEquals(0, loggerState.errorCalls);
    }

    @Test
    void redirectedCurrentResultIsEvaluatedInsteadOfOriginalServer() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        AtomicReference<AccessScope> observedScope = new AtomicReference<>();
        AtomicReference<PlayerIdentity> observedIdentity = new AtomicReference<>();
        AccessGrantLookup grants = (scope, requestedIdentity) -> {
            observedScope.set(scope);
            observedIdentity.set(requestedIdentity);
            if (scope.equals(AccessScope.server("maintenance"))) {
                return Optional.of(new AccessGrant(scope, requestedIdentity));
            }
            return Optional.empty();
        };
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                grants,
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        RegisteredServer survival = registeredServer("survival");
        RegisteredServer maintenance = registeredServer("maintenance");
        ServerPreConnectEvent event = event(player, survival);
        ServerPreConnectEvent.ServerResult redirected = ServerPreConnectEvent.ServerResult.allowed(maintenance);
        event.setResult(redirected);

        listener.onServerPreConnect(event);

        assertSame(redirected, event.getResult());
        assertEquals(AccessScope.server("maintenance"), observedScope.get());
        assertSame(identity, observedIdentity.get());
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void openTargetPreservesCurrentAllowedResultAndPerformsNoGrantLookup() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        VelocityConnectionIdentityRegistry registry = activeRegistry(
                player,
                PlayerIdentity.online("hanamuramiyu", PLAYER_UUID)
        );
        AccessGrantLookup explodingLookup = (scope, identity) -> {
            throw new AssertionError("OPEN target must not consult grants.");
        };
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.OPEN, Map.of(), Map.of()),
                explodingLookup,
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("lobby"));
        ServerPreConnectEvent.ServerResult before = event.getResult();

        listener.onServerPreConnect(event);

        assertSame(before, event.getResult());
        assertEquals(0, playerState.messageCalls);
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void restrictedTargetAllowsDirectServerGrant() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        AccessGrantLookup grants = grantLookup(identity, AccessScope.server("testing-lobby"));
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                grants,
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("testing-lobby"));
        ServerPreConnectEvent.ServerResult before = event.getResult();

        listener.onServerPreConnect(event);

        assertSame(before, event.getResult());
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void restrictedGroupedTargetAllowsServerGroupGrant() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        AccessGrantLookup grants = grantLookup(identity, AccessScope.serverGroup("testing"));
        ServerGroupCatalog groups = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("testing", List.of("testing-lobby"))
        ));
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                grants,
                groups,
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("testing-lobby"));
        ServerPreConnectEvent.ServerResult before = event.getResult();

        listener.onServerPreConnect(event);

        assertSame(before, event.getResult());
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void initialRestrictedTargetWithoutGrantDeniesAndDisconnectsProxySession() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                (scope, requestedIdentity) -> Optional.empty(),
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("private"));

        listener.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        assertEquals(0, playerState.messageCalls);
        assertEquals(1, playerState.disconnectCalls);
    }

    @Test
    void restrictedServerSwitchWithoutGrantKeepsPreviousBackendSession() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                (scope, requestedIdentity) -> Optional.empty(),
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(
                player,
                registeredServer("private"),
                registeredServer("lobby")
        );

        listener.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        assertEquals(1, playerState.messageCalls);
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void explicitServerOpenOverridesRestrictedGroup() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        VelocityConnectionIdentityRegistry registry = activeRegistry(
                player,
                PlayerIdentity.online("hanamuramiyu", PLAYER_UUID)
        );
        ServerGroupCatalog groups = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("testing", List.of("testing-lobby"))
        ));
        AccessGrantLookup explodingLookup = (scope, identity) -> {
            throw new AssertionError("Explicit OPEN server policy must not consult grants.");
        };
        VelocityBackendAdmissionListener listener = listener(
                policies(
                        BackendAccessMode.OPEN,
                        Map.of("testing", BackendAccessMode.GRANT_REQUIRED),
                        Map.of("testing-lobby", BackendAccessMode.OPEN)
                ),
                explodingLookup,
                groups,
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("testing-lobby"));
        ServerPreConnectEvent.ServerResult before = event.getResult();

        listener.onServerPreConnect(event);

        assertSame(before, event.getResult());
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void ungroupedRestrictedServerAllowsDirectGrant() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", PLAYER_UUID);
        VelocityConnectionIdentityRegistry registry = activeRegistry(player, identity);
        VelocityBackendAdmissionListener listener = listener(
                policies(
                        BackendAccessMode.OPEN,
                        Map.of(),
                        Map.of("dev", BackendAccessMode.GRANT_REQUIRED)
                ),
                grantLookup(identity, AccessScope.server("dev")),
                new ServerGroupCatalog(List.of()),
                registry,
                new RecordingLogger().logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("dev"));
        ServerPreConnectEvent.ServerResult before = event.getResult();

        listener.onServerPreConnect(event);

        assertSame(before, event.getResult());
        assertEquals(0, playerState.disconnectCalls);
    }

    @Test
    void missingActiveConnectionIdentityFailsClosedAndDisconnects() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        RecordingLogger loggerState = new RecordingLogger();
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.OPEN, Map.of(), Map.of()),
                (scope, identity) -> Optional.empty(),
                new ServerGroupCatalog(List.of()),
                new VelocityConnectionIdentityRegistry(),
                loggerState.logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("lobby"));

        listener.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        assertEquals(1, playerState.disconnectCalls);
        assertEquals(0, playerState.messageCalls);
        assertEquals(1, loggerState.errorCalls);
        assertTrue(loggerState.lastErrorThrowable instanceof IllegalStateException);
        assertTrue(loggerState.lastErrorThrowable.getMessage().contains("Missing active monban connection identity"));
    }

    @Test
    void backendAdmissionFailureFailsClosedAndDisconnects() {
        RecordingPlayer playerState = new RecordingPlayer();
        Player player = playerState.player();
        RecordingLogger loggerState = new RecordingLogger();
        VelocityConnectionIdentityRegistry registry = activeRegistry(
                player,
                PlayerIdentity.online("hanamuramiyu", PLAYER_UUID)
        );
        AccessGrantLookup explodingLookup = (scope, identity) -> {
            throw new IllegalStateException("boom");
        };
        VelocityBackendAdmissionListener listener = listener(
                policies(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                explodingLookup,
                new ServerGroupCatalog(List.of()),
                registry,
                loggerState.logger()
        );
        ServerPreConnectEvent event = event(player, registeredServer("private"));

        listener.onServerPreConnect(event);

        assertFalse(event.getResult().isAllowed());
        assertEquals(1, playerState.disconnectCalls);
        assertEquals(1, loggerState.errorCalls);
        assertEquals("boom", loggerState.lastErrorThrowable.getMessage());
    }

    private static VelocityBackendAdmissionListener listener(
            BackendAccessPolicyCatalog policies,
            AccessGrantLookup grants,
            ServerGroupCatalog groups,
            VelocityConnectionIdentityRegistry registry,
            Logger logger
    ) {
        return new VelocityBackendAdmissionListener(
                new BackendAdmissionService(policies, grants),
                groups,
                registry,
                logger
        );
    }

    private static BackendAccessPolicyCatalog policies(
            BackendAccessMode defaultMode,
            Map<String, BackendAccessMode> groupPolicies,
            Map<String, BackendAccessMode> serverPolicies
    ) {
        return new BackendAccessPolicyCatalog(defaultMode, groupPolicies, serverPolicies);
    }

    private static AccessGrantLookup grantLookup(PlayerIdentity identity, AccessScope grantedScope) {
        return (scope, requestedIdentity) -> {
            if (scope.equals(grantedScope) && requestedIdentity.equals(identity)) {
                return Optional.of(new AccessGrant(scope, requestedIdentity));
            }
            return Optional.empty();
        };
    }

    private static VelocityConnectionIdentityRegistry activeRegistry(Player player, PlayerIdentity identity) {
        VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();
        registry.stage(player, identity);
        registry.activate(player).orElseThrow();
        return registry;
    }

    private static ServerPreConnectEvent event(Player player, RegisteredServer originalServer) {
        return event(player, originalServer, null);
    }

    private static ServerPreConnectEvent event(
            Player player,
            RegisteredServer originalServer,
            RegisteredServer previousServer
    ) {
        return new ServerPreConnectEvent(player, originalServer, previousServer);
    }

    private static RegisteredServer registeredServer(String name) {
        ServerInfo info = new ServerInfo(name, InetSocketAddress.createUnresolved("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "toString" -> "RegisteredServer[" + name + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static final class RecordingPlayer {
        private int messageCalls;
        private int disconnectCalls;

        private Player player() {
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUsername" -> "hanamuramiyu";
                        case "getUniqueId" -> PLAYER_UUID;
                        case "sendMessage" -> {
                            messageCalls++;
                            yield null;
                        }
                        case "disconnect" -> {
                            disconnectCalls++;
                            yield null;
                        }
                        case "toString" -> "Player[hanamuramiyu]";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static final class RecordingLogger {
        private int errorCalls;
        private Throwable lastErrorThrowable;

        private Logger logger() {
            return (Logger) Proxy.newProxyInstance(
                    Logger.class.getClassLoader(),
                    new Class<?>[]{Logger.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("error")) {
                            errorCalls++;
                            if (args != null) {
                                for (Object arg : args) {
                                    captureThrowable(arg);
                                }
                            }
                        }
                        if (method.getName().equals("toString")) {
                            return "RecordingLogger";
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private void captureThrowable(Object value) {
            if (value instanceof Throwable throwable) {
                lastErrorThrowable = throwable;
                return;
            }
            if (value instanceof Object[] nested) {
                for (Object item : nested) {
                    captureThrowable(item);
                }
            }
        }
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
            return 0.0f;
        }
        return 0.0d;
    }
}
