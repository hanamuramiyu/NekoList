package hanamuramiyu.monban.velocity.group;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityServerGroupCatalogResolverTest {
    @Test
    void resolvesConfiguredNameToVelocityCanonicalName() {
        ProxyServer proxy = proxyWithServers("lobby", "survival");
        ServerGroupCatalog configured = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("public", List.of("LoBbY", "SURVIVAL"))
        ));

        ServerGroupCatalog resolved = new VelocityServerGroupCatalogResolver(proxy).resolve(configured);

        assertEquals(List.of("lobby", "survival"), resolved.findById("public").orElseThrow().servers());
        assertEquals("public", resolved.findForServer("lobby").orElseThrow().id());
        assertTrue(resolved.findForServer("LoBbY").isEmpty());
    }

    @Test
    void rejectsUnknownConfiguredBackend() {
        ProxyServer proxy = proxyWithServers("test-lobby");
        ServerGroupCatalog configured = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("testing", List.of("test-loby"))
        ));

        assertThrows(
                IllegalStateException.class,
                () -> new VelocityServerGroupCatalogResolver(proxy).resolve(configured)
        );
    }

    @Test
    void rejectsDuplicateMembershipCreatedByCaseInsensitiveCanonicalization() {
        ProxyServer proxy = proxyWithServers("lobby");
        ServerGroupCatalog configured = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("public", List.of("lobby")),
                new ServerGroupDefinition("testing", List.of("LoBbY"))
        ));

        assertThrows(
                IllegalStateException.class,
                () -> new VelocityServerGroupCatalogResolver(proxy).resolve(configured)
        );
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
