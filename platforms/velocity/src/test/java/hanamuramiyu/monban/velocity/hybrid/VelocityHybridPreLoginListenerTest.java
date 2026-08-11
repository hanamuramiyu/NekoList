package hanamuramiyu.monban.velocity.hybrid;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityHybridPreLoginListenerTest {
    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void listenerRunsLateWithoutRequestingAsyncDispatch() throws NoSuchMethodException {
        Subscribe subscribe = VelocityHybridPreLoginListener.class
                .getMethod("onPreLogin", PreLoginEvent.class)
                .getAnnotation(Subscribe.class);

        assertEquals((int) Short.MIN_VALUE, (int) subscribe.priority());
        assertFalse(subscribe.async());
    }

    @Test
    void disabledHybridLeavesEventUntouchedWithoutInventoryRead() {
        AccessGrantInventory explodingInventory = () -> {
            throw new AssertionError("Disabled hybrid must not inspect grants.");
        };
        HybridIdentitySettings settings = new HybridIdentitySettings(false, HybridIdentityPreference.ONLINE);
        VelocityHybridPreLoginListener listener = listener(explodingInventory, settings, new RecordingLogger().logger());
        PreLoginEvent event = event("hanamuramiyu", UUID_A);
        PreLoginEvent.PreLoginComponentResult before = event.getResult();

        listener.onPreLogin(event);

        assertSame(before, event.getResult());
    }

    @Test
    void existingDenialIsPreservedWithoutInventoryRead() {
        AccessGrantInventory explodingInventory = () -> {
            throw new AssertionError("Denied pre-login must not inspect grants.");
        };
        HybridIdentitySettings settings = enabledSettings();
        VelocityHybridPreLoginListener listener = listener(explodingInventory, settings, new RecordingLogger().logger());
        PreLoginEvent event = event("hanamuramiyu", UUID_A);
        PreLoginEvent.PreLoginComponentResult denied = PreLoginEvent.PreLoginComponentResult.denied(Component.text("Denied"));
        event.setResult(denied);

        listener.onPreLogin(event);

        assertSame(denied, event.getResult());
    }

    @Test
    void existingForcedOnlineModeIsPreservedWithoutInventoryRead() {
        AccessGrantInventory explodingInventory = () -> {
            throw new AssertionError("Existing forced online mode must not inspect grants.");
        };
        VelocityHybridPreLoginListener listener = listener(
                explodingInventory,
                enabledSettings(),
                new RecordingLogger().logger()
        );
        PreLoginEvent event = event("hanamuramiyu", UUID_A);
        PreLoginEvent.PreLoginComponentResult forcedOnline =
                PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
        event.setResult(forcedOnline);

        listener.onPreLogin(event);

        assertSame(forcedOnline, event.getResult());
    }

    @Test
    void existingForcedOfflineModeIsPreservedWithoutInventoryRead() {
        AccessGrantInventory explodingInventory = () -> {
            throw new AssertionError("Existing forced offline mode must not inspect grants.");
        };
        VelocityHybridPreLoginListener listener = listener(
                explodingInventory,
                enabledSettings(),
                new RecordingLogger().logger()
        );
        PreLoginEvent event = event("hanamuramiyu", UUID_A);
        PreLoginEvent.PreLoginComponentResult forcedOffline =
                PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
        event.setResult(forcedOffline);

        listener.onPreLogin(event);

        assertSame(forcedOffline, event.getResult());
    }

    @Test
    void onlineSelectionForcesOnlineMode() {
        VelocityHybridPreLoginListener listener = listener(
                inventory(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_A))),
                enabledSettings(),
                new RecordingLogger().logger()
        );
        PreLoginEvent event = event("hanamuramiyu", UUID_A);

        listener.onPreLogin(event);

        assertTrue(event.getResult().isAllowed());
        assertTrue(event.getResult().isOnlineModeAllowed());
        assertFalse(event.getResult().isForceOfflineMode());
    }

    @Test
    void offlineSelectionForcesOfflineMode() {
        VelocityHybridPreLoginListener listener = listener(
                inventory(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))),
                enabledSettings(),
                new RecordingLogger().logger()
        );
        PreLoginEvent event = event("hanamuramiyu", UUID_A);

        listener.onPreLogin(event);

        assertTrue(event.getResult().isAllowed());
        assertTrue(event.getResult().isForceOfflineMode());
    }

    @Test
    void selectorFailureFailsClosedAndLogsError() {
        RuntimeException failure = new IllegalStateException("inventory unavailable");
        AccessGrantInventory inventory = () -> {
            throw failure;
        };
        RecordingLogger loggerState = new RecordingLogger();
        VelocityHybridPreLoginListener listener = listener(inventory, enabledSettings(), loggerState.logger());
        PreLoginEvent event = event("hanamuramiyu", UUID_A);

        listener.onPreLogin(event);

        assertFalse(event.getResult().isAllowed());
        assertEquals(1, loggerState.errorCalls);
    }

    private static VelocityHybridPreLoginListener listener(
            AccessGrantInventory inventory,
            HybridIdentitySettings settings,
            Logger logger
    ) {
        return new VelocityHybridPreLoginListener(
                new VelocityHybridIdentitySelector(inventory, settings),
                settings,
                logger
        );
    }

    private static HybridIdentitySettings enabledSettings() {
        return new HybridIdentitySettings(true, HybridIdentityPreference.ONLINE);
    }

    private static AccessGrantInventory inventory(AccessGrant... grants) {
        return () -> List.of(grants);
    }

    private static PreLoginEvent event(String username, UUID uuid) {
        return new PreLoginEvent(inboundConnection(), username, uuid);
    }

    private static InboundConnection inboundConnection() {
        return (InboundConnection) Proxy.newProxyInstance(
                InboundConnection.class.getClassLoader(),
                new Class<?>[]{InboundConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "InboundConnectionStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class RecordingLogger {
        private int errorCalls;

        private Logger logger() {
            return (Logger) Proxy.newProxyInstance(
                    Logger.class.getClassLoader(),
                    new Class<?>[]{Logger.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("error")) {
                            errorCalls++;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }
    }
}
