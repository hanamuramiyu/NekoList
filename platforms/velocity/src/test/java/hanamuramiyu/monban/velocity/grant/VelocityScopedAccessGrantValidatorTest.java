package hanamuramiyu.monban.velocity.grant;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityScopedAccessGrantValidatorTest {
    private static final PlayerIdentity IDENTITY = PlayerIdentity.offline("hanamuramiyu");

    @Test
    void existingGroupIsAccepted() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of(
                        new ServerGroupDefinition("testing", List.of("lobby"))
                ))
        );

        assertDoesNotThrow(() -> validator.validate(List.of(
                new AccessGrant(AccessScope.serverGroup("testing"), IDENTITY)
        )));
    }

    @Test
    void missingGroupIsRejected() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validate(List.of(
                        new AccessGrant(AccessScope.serverGroup("testing"), IDENTITY)
                ))
        );

        assertTrue(exception.getMessage().contains("unknown server group: testing"));
    }

    @Test
    void canonicalServerIsAccepted() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        assertDoesNotThrow(() -> validator.validate(List.of(
                new AccessGrant(AccessScope.server("lobby"), IDENTITY)
        )));
    }

    @Test
    void missingServerIsRejected() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validate(List.of(
                        new AccessGrant(AccessScope.server("missing"), IDENTITY)
                ))
        );

        assertTrue(exception.getMessage().contains("not registered in Velocity: missing"));
    }

    @Test
    void wrongCaseServerIsRejectedWithCanonicalExpectedName() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validate(List.of(
                        new AccessGrant(AccessScope.server("LoBbY"), IDENTITY)
                ))
        );

        assertTrue(exception.getMessage().contains("LoBbY"));
        assertTrue(exception.getMessage().contains("expected lobby"));
    }

    @Test
    void existingUngroupedServerIsAccepted() {
        VelocityScopedAccessGrantValidator validator = validator(
                proxyWithServers("orphan"),
                new ServerGroupCatalog(List.of(
                        new ServerGroupDefinition("public", List.of("lobby"))
                ))
        );

        assertDoesNotThrow(() -> validator.validate(List.of(
                new AccessGrant(AccessScope.server("orphan"), IDENTITY)
        )));
    }

    private static VelocityScopedAccessGrantValidator validator(
            ProxyServer proxy,
            ServerGroupCatalog catalog
    ) {
        return new VelocityScopedAccessGrantValidator(new VelocityBackendScopeValidator(proxy, catalog));
    }

    private static ProxyServer proxyWithServers(String... canonicalNames) {
        Map<String, RegisteredServer> servers = new LinkedHashMap<>();
        for (String canonicalName : canonicalNames) {
            servers.put(canonicalName, registeredServer(canonicalName));
        }

        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer") && args != null && args.length == 1) {
                        String requested = (String) args[0];
                        return servers.entrySet().stream()
                                .filter(entry -> entry.getKey().equalsIgnoreCase(requested))
                                .map(Map.Entry::getValue)
                                .findFirst();
                    }
                    if (method.getName().equals("toString")) {
                        return "ProxyServerStub";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );
    }

    private static RegisteredServer registeredServer(String canonicalName) {
        ServerInfo info = new ServerInfo(
                canonicalName,
                InetSocketAddress.createUnresolved("127.0.0.1", 25565)
        );

        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServerInfo")) {
                        return info;
                    }
                    if (method.getName().equals("toString")) {
                        return "RegisteredServer[" + canonicalName + "]";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );
    }
}
