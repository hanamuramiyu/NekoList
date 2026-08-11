package hanamuramiyu.monban.paper;

import hanamuramiyu.monban.access.AccessDecision;
import hanamuramiyu.monban.access.PlayerAccessEvaluation;
import hanamuramiyu.monban.access.PlayerAccessService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Objects;
import java.util.logging.Level;

public final class PaperPlayerLoginListener implements Listener {
    private static final Component NOT_WHITELISTED_MESSAGE = Component.text("You are not whitelisted on this server.");
    private static final Component ACCESS_ERROR_MESSAGE = Component.text("Unable to verify your access. Please try again later.");

    private final MonbanPaperPlugin plugin;
    private final PlayerAccessService accessService;

    public PaperPlayerLoginListener(MonbanPaperPlugin plugin, PlayerAccessService accessService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.accessService = Objects.requireNonNull(accessService, "accessService");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        try {
            PlayerAccessEvaluation evaluation = accessService.evaluate(
                    event.getName(),
                    event.getUniqueId(),
                    plugin.getServer().getOnlineMode()
            );

            if (evaluation.decision() == AccessDecision.NOT_WHITELISTED) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, NOT_WHITELISTED_MESSAGE);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Failed to verify access for player " + event.getName() + " (" + event.getUniqueId() + ").",
                    exception
            );
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, ACCESS_ERROR_MESSAGE);
        }
    }
}
