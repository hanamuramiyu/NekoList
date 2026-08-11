package hanamuramiyu.monban.velocity.grant;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAccessGrantScopeValidatorTest {
    @Test
    void networkScopeIsAccepted() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        assertDoesNotThrow(() -> validator.validate(AccessScope.network()));
    }

    @Test
    void existingGroupIsAccepted() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of(new ServerGroupDefinition("testing", List.of("lobby"))))
        );

        assertDoesNotThrow(() -> validator.validate(AccessScope.serverGroup("testing")));
    }

    @Test
    void missingGroupIsRejected() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        AccessGrantScopeValidationException exception = assertThrows(
                AccessGrantScopeValidationException.class,
                () -> validator.validate(AccessScope.serverGroup("testing"))
        );

        assertTrue(exception.getMessage().contains("unknown server group: testing"));
    }

    @Test
    void canonicalServerIsAccepted() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        assertDoesNotThrow(() -> validator.validate(AccessScope.server("lobby")));
    }

    @Test
    void unknownServerIsRejected() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        AccessGrantScopeValidationException exception = assertThrows(
                AccessGrantScopeValidationException.class,
                () -> validator.validate(AccessScope.server("missing"))
        );

        assertTrue(exception.getMessage().contains("not registered in Velocity: missing"));
    }

    @Test
    void unexpectedVelocityLookupFailurePropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("proxy lookup failed");
        ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (instance, method, args) -> {
                    if (method.getName().equals("getServer")) {
                        throw failure;
                    }
                    if (method.getName().equals("toString")) {
                        return "FailingProxyServer";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(instance);
                    }
                    if (method.getName().equals("equals")) {
                        return instance == args[0];
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );
        VelocityAccessGrantScopeValidator validator = validator(
                proxy,
                new ServerGroupCatalog(List.of())
        );

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.validate(AccessScope.server("lobby"))
                )
        );
    }

    @Test
    void wrongCaseServerIsRejectedWithCanonicalExpectedName() {
        VelocityAccessGrantScopeValidator validator = validator(
                proxyWithServers("lobby"),
                new ServerGroupCatalog(List.of())
        );

        AccessGrantScopeValidationException exception = assertThrows(
                AccessGrantScopeValidationException.class,
                () -> validator.validate(AccessScope.server("LoBbY"))
        );

        assertTrue(exception.getMessage().contains("LoBbY"));
        assertTrue(exception.getMessage().contains("expected lobby"));
    }

    private static VelocityAccessGrantScopeValidator validator(
            ProxyServer proxy,
            ServerGroupCatalog catalog
    ) {
        return new VelocityAccessGrantScopeValidator(new VelocityBackendScopeValidator(proxy, catalog));
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
