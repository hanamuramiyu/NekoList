package hanamuramiyu.monban.velocity.backend;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import hanamuramiyu.monban.access.backend.BackendAdmissionDecision;
import hanamuramiyu.monban.access.backend.BackendAdmissionService;
import hanamuramiyu.monban.access.backend.BackendTarget;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Objects;

public final class VelocityBackendAdmissionListener {
    private static final Component NOT_GRANTED_MESSAGE = Component.text("You do not have access to this server.");
    private static final Component ACCESS_ERROR_MESSAGE = Component.text(
            "Unable to verify your access. Please try again later."
    );

    private final BackendAdmissionService backendAdmissionService;
    private final ServerGroupCatalog serverGroupCatalog;
    private final VelocityConnectionIdentityRegistry identityRegistry;
    private final Logger logger;

    public VelocityBackendAdmissionListener(
            BackendAdmissionService backendAdmissionService,
            ServerGroupCatalog serverGroupCatalog,
            VelocityConnectionIdentityRegistry identityRegistry,
            Logger logger
    ) {
        this.backendAdmissionService = Objects.requireNonNull(backendAdmissionService, "backendAdmissionService");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        ServerPreConnectEvent.ServerResult currentResult = event.getResult();
        if (!currentResult.isAllowed()) {
            return;
        }

        Player player = event.getPlayer();

        try {
            RegisteredServer targetServer = currentResult.getServer().orElseThrow(() ->
                    new IllegalStateException("Allowed ServerPreConnectEvent result has no target server."));

            PlayerIdentity identity = identityRegistry.findActive(player).orElseThrow(() ->
                    new IllegalStateException("Missing active monban connection identity."));

            String serverName = targetServer.getServerInfo().getName();
            BackendTarget target = serverGroupCatalog.findForServer(serverName)
                    .map(group -> BackendTarget.grouped(serverName, group.id()))
                    .orElseGet(() -> BackendTarget.ungrouped(serverName));

            BackendAdmissionDecision decision = backendAdmissionService.evaluate(target, identity);
            if (decision == BackendAdmissionDecision.NOT_GRANTED) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());

                if (event.getPreviousServer() == null) {
                    player.disconnect(NOT_GRANTED_MESSAGE);
                } else {
                    player.sendMessage(NOT_GRANTED_MESSAGE);
                }
            }
        } catch (RuntimeException exception) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            logger.error(
                    "Unable to verify monban backend access for player {} ({}).",
                    player.getUsername(),
                    player.getUniqueId(),
                    exception
            );
            player.disconnect(ACCESS_ERROR_MESSAGE);
        }
    }
}
