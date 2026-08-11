package hanamuramiyu.monban.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.monban.access.AccessDecision;
import hanamuramiyu.monban.access.PlayerAccessEvaluation;
import hanamuramiyu.monban.access.PlayerAccessService;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Objects;

public final class VelocityPlayerLoginListener {
    private static final Component NOT_WHITELISTED_MESSAGE = Component.text("You are not whitelisted on this server.");
    private static final Component ACCESS_ERROR_MESSAGE = Component.text("Unable to verify your access. Please try again later.");

    private final PlayerAccessService accessService;
    private final VelocityConnectionIdentityRegistry identityRegistry;
    private final Logger logger;

    public VelocityPlayerLoginListener(
            PlayerAccessService accessService,
            VelocityConnectionIdentityRegistry identityRegistry,
            Logger logger
    ) {
        this.accessService = Objects.requireNonNull(accessService, "accessService");
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public EventTask onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }

        return EventTask.async(() -> evaluateAccess(event));
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        try {
            if (identityRegistry.activate(player).isEmpty()) {
                logger.error(
                        "Missing staged monban identity after Velocity login for player {} ({}). Denying backend access.",
                        player.getUsername(),
                        player.getUniqueId()
                );
                player.disconnect(ACCESS_ERROR_MESSAGE);
            }
        } catch (RuntimeException exception) {
            logger.error(
                    "Unable to activate monban connection identity for player {} ({}).",
                    player.getUsername(),
                    player.getUniqueId(),
                    exception
            );
            player.disconnect(ACCESS_ERROR_MESSAGE);
        }
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onDisconnect(DisconnectEvent event) {
        identityRegistry.remove(event.getPlayer());
    }

    private void evaluateAccess(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }

        Player player = event.getPlayer();
        String username = player.getUsername();

        try {
            PlayerAccessEvaluation evaluation = accessService.evaluate(
                    username,
                    player.getUniqueId(),
                    event.getServerIdHash() != null
            );

            if (evaluation.decision() == AccessDecision.NOT_WHITELISTED) {
                event.setResult(ResultedEvent.ComponentResult.denied(NOT_WHITELISTED_MESSAGE));
                return;
            }

            identityRegistry.stage(player, evaluation.identity());
        } catch (RuntimeException exception) {
            logger.error(
                    "Unable to verify monban access for player {} ({}).",
                    username,
                    player.getUniqueId(),
                    exception
            );
            event.setResult(ResultedEvent.ComponentResult.denied(ACCESS_ERROR_MESSAGE));
        }
    }
}
