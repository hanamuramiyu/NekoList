package hanamuramiyu.monban.velocity.sync;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityStateSynchronizerTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );

    @Test
    void registersChannelAndSendsSignedSnapshot() {
        AtomicReference<ChannelIdentifier> registered = new AtomicReference<>();
        AtomicReference<byte[]> payload = new AtomicReference<>();
        RegisteredServer backend = registeredServer("survival", payload);
        ProxyServer proxy = proxyServer(List.of(backend), registered);
        VelocityStateSynchronizer synchronizer = synchronizer(proxy);

        synchronizer.registerChannel();
        synchronizer.broadcast();

        assertEquals(VelocityStateSynchronizer.CHANNEL, registered.get());
        assertTrue(payload.get() != null && payload.get().length > 32);
        assertEquals(1, new PlayerAccessStateCodec().decode(payload.get(), SECRET).revision());

        synchronizer.unregisterChannel();
    }

    @Test
    void blocksClientMessagesOnStateChannel() {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        PluginMessageEvent event = new PluginMessageEvent(
                player,
                player,
                VelocityStateSynchronizer.CHANNEL,
                new byte[0]
        );

        synchronizer(proxyServer(List.of(), new AtomicReference<>())).onPluginMessage(event);

        assertFalse(event.getResult().isAllowed());
    }

    @Test
    void preservesRevisionAcrossSynchronizerRestart() throws Exception {
        Path revisionFile = Files.createTempFile("monban-state-revision", ".txt");
        Files.delete(revisionFile);
        AtomicReference<byte[]> firstPayload = new AtomicReference<>();
        AtomicReference<byte[]> secondPayload = new AtomicReference<>();
        RegisteredServer firstBackend = registeredServer("survival", firstPayload);
        RegisteredServer secondBackend = registeredServer("survival", secondPayload);

        synchronizer(proxyServer(List.of(firstBackend), new AtomicReference()), revisionFile).broadcast();
        synchronizer(proxyServer(List.of(secondBackend), new AtomicReference()), revisionFile).broadcast();

        assertEquals(1, new PlayerAccessStateCodec().decode(firstPayload.get(), SECRET).revision());
        assertEquals(2, new PlayerAccessStateCodec().decode(secondPayload.get(), SECRET).revision());
        Files.deleteIfExists(revisionFile);
    }

    private static VelocityStateSynchronizer synchronizer(ProxyServer server) {
        return synchronizer(server, null);
    }

    private static VelocityStateSynchronizer synchronizer(ProxyServer server, Path revisionFile) {
        return new VelocityStateSynchronizer(
                server,
                new InMemoryAccessGrantRepository(),
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository(),
                SECRET,
                logger(),
                revisionFile
        );
    }

    private static RegisteredServer registeredServer(String name, AtomicReference<byte[]> payload) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "sendPluginMessage" -> {
                        payload.set((byte[]) args[1]);
                        yield true;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static ProxyServer proxyServer(
            Collection<RegisteredServer> servers,
            AtomicReference<ChannelIdentifier> registered
    ) {
        ChannelRegistrar registrar = (ChannelRegistrar) Proxy.newProxyInstance(
                ChannelRegistrar.class.getClassLoader(),
                new Class<?>[]{ChannelRegistrar.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("register")) {
                        registered.set(((ChannelIdentifier[]) args[0])[0]);
                    }
                    return null;
                }
        );
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAllServers" -> servers;
                    case "getChannelRegistrar" -> registrar;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Logger logger() {
        return (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
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
