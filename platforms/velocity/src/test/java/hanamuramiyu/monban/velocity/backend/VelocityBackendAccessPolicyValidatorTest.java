package hanamuramiyu.monban.velocity.backend;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityBackendAccessPolicyValidatorTest {
    @Test void existingGroupIsAccepted() {
        assertDoesNotThrow(() -> validator(proxyWithServers("lobby"), new ServerGroupCatalog(List.of(new ServerGroupDefinition("testing", List.of("lobby")))))
                .validate(catalog(Map.of("testing", BackendAccessMode.GRANT_REQUIRED), Map.of())));
    }
    @Test void missingGroupIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validator(proxyWithServers("lobby"), new ServerGroupCatalog(List.of()))
                .validate(catalog(Map.of("testing", BackendAccessMode.GRANT_REQUIRED), Map.of())));
        assertTrue(e.getMessage().contains("unknown server group: testing"));
    }
    @Test void canonicalServerIsAccepted() {
        assertDoesNotThrow(() -> validator(proxyWithServers("lobby"), new ServerGroupCatalog(List.of()))
                .validate(catalog(Map.of(), Map.of("lobby", BackendAccessMode.GRANT_REQUIRED))));
    }
    @Test void missingServerIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validator(proxyWithServers("lobby"), new ServerGroupCatalog(List.of()))
                .validate(catalog(Map.of(), Map.of("missing", BackendAccessMode.GRANT_REQUIRED))));
        assertTrue(e.getMessage().contains("not registered in Velocity: missing"));
    }
    @Test void wrongCaseServerIsRejectedWithCanonicalExpectedName() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validator(proxyWithServers("lobby"), new ServerGroupCatalog(List.of()))
                .validate(catalog(Map.of(), Map.of("LoBbY", BackendAccessMode.GRANT_REQUIRED))));
        assertTrue(e.getMessage().contains("LoBbY"));
        assertTrue(e.getMessage().contains("expected lobby"));
    }
    @Test void existingUngroupedServerIsAccepted() {
        assertDoesNotThrow(() -> validator(proxyWithServers("orphan"), new ServerGroupCatalog(List.of(new ServerGroupDefinition("public", List.of("lobby")))))
                .validate(catalog(Map.of(), Map.of("orphan", BackendAccessMode.GRANT_REQUIRED))));
    }

    private static BackendAccessPolicyCatalog catalog(Map<String, BackendAccessMode> groups, Map<String, BackendAccessMode> servers) {
        return new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, groups, servers);
    }
    private static VelocityBackendAccessPolicyValidator validator(ProxyServer proxy, ServerGroupCatalog catalog) {
        return new VelocityBackendAccessPolicyValidator(new VelocityBackendScopeValidator(proxy, catalog));
    }
    private static ProxyServer proxyWithServers(String... canonicalNames) {
        Map<String, RegisteredServer> servers = new LinkedHashMap<>();
        for (String name : canonicalNames) servers.put(name, registeredServer(name));
        return (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class}, (proxy, method, args) -> {
            if (method.getName().equals("getServer") && args != null && args.length == 1) {
                String requested = (String) args[0];
                return servers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(requested)).map(Map.Entry::getValue).findFirst();
            }
            if (method.getName().equals("toString")) return "ProxyServerStub";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new UnsupportedOperationException(method.toString());
        });
    }
    private static RegisteredServer registeredServer(String name) {
        ServerInfo info = new ServerInfo(name, InetSocketAddress.createUnresolved("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(), new Class<?>[]{RegisteredServer.class}, (proxy, method, args) -> {
            if (method.getName().equals("getServerInfo")) return info;
            if (method.getName().equals("toString")) return "RegisteredServer[" + name + "]";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new UnsupportedOperationException(method.toString());
        });
    }
}
