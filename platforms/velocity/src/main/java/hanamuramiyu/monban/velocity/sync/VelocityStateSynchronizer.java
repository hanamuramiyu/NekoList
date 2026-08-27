package hanamuramiyu.monban.velocity.sync;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.SyncChannel;
import hanamuramiyu.monban.sync.SyncSecret;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicLong;

public final class VelocityStateSynchronizer {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(SyncChannel.ID);

    private final ProxyServer server;
    private final AccessGrantInventory accessGrants;
    private final PlayerGroupRepository groups;
    private final PlayerGroupAssignmentRepository assignments;
    private final PlayerPermissionGrantRepository permissions;
    private final SyncSecret secret;
    private final Logger logger;
    private final VelocityStateRevisionStore revisionStore;
    private final BooleanSupplier networkWhitelistEnabled;
    private final PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
    private final AtomicLong revision;

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger
    ) {
        this(server, accessGrants, groups, assignments, permissions, secret, logger, null);
    }

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger,
            Path revisionFile
    ) {
        this(server, accessGrants, groups, assignments, permissions, secret, logger, revisionFile, () -> true);
    }

    public VelocityStateSynchronizer(
            ProxyServer server,
            AccessGrantInventory accessGrants,
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments,
            PlayerPermissionGrantRepository permissions,
            SyncSecret secret,
            Logger logger,
            Path revisionFile,
            BooleanSupplier networkWhitelistEnabled
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.accessGrants = Objects.requireNonNull(accessGrants, "accessGrants");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.revisionStore = revisionFile == null ? null : new VelocityStateRevisionStore(revisionFile);
        this.revision = new AtomicLong(revisionStore == null ? 0 : revisionStore.load());
        this.networkWhitelistEnabled = Objects.requireNonNull(networkWhitelistEnabled, "networkWhitelistEnabled");
    }

    public void registerChannel() {
        server.getChannelRegistrar().register(CHANNEL);
    }

    public void unregisterChannel() {
        server.getChannelRegistrar().unregister(CHANNEL);
    }

    public void broadcast() {
        byte[] payload = codec.encode(snapshot(), secret);
        for (RegisteredServer registeredServer : server.getAllServers()) {
            send(registeredServer, payload);
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        event.getInitialServer().ifPresent(registeredServer -> send(registeredServer, codec.encode(snapshot(), secret)));
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServer())
                .ifPresent(registeredServer -> send(registeredServer, codec.encode(snapshot(), secret)));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (CHANNEL.equals(event.getIdentifier()) && event.getSource() instanceof Player) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    private synchronized PlayerAccessStateSnapshot snapshot() {
        long nextRevision = revision.incrementAndGet();
        if (revisionStore != null) {
            revisionStore.save(nextRevision);
        }
        return new PlayerAccessStateSnapshot(
                nextRevision,
                networkWhitelistEnabled.getAsBoolean(),
                accessGrants.findAll(),
                groups.findAll(),
                assignments.findAll(),
                permissions.findAll()
        );
    }

    private void send(RegisteredServer registeredServer, byte[] payload) {
        if (!registeredServer.sendPluginMessage(CHANNEL, payload)) {
            logger.debug(
                    "Backend {} did not accept the monban state snapshot.",
                    registeredServer.getServerInfo().getName()
            );
        }
    }
}
