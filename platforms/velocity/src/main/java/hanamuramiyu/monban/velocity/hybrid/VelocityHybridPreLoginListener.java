package hanamuramiyu.monban.velocity.hybrid;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Objects;

public final class VelocityHybridPreLoginListener {
    private static final Component ACCESS_ERROR_MESSAGE = Component.text(
            "Unable to verify your access. Please try again later."
    );

    private final VelocityHybridIdentitySelector selector;
    private final HybridIdentitySettings settings;
    private final Logger logger;

    public VelocityHybridPreLoginListener(
            VelocityHybridIdentitySelector selector,
            HybridIdentitySettings settings,
            Logger logger
    ) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onPreLogin(PreLoginEvent event) {
        PreLoginEvent.PreLoginComponentResult currentResult = event.getResult();

        if (!settings.enabled() || !currentResult.isAllowed()) {
            return;
        }

        if (currentResult.isOnlineModeAllowed() || currentResult.isForceOfflineMode()) {
            return;
        }

        try {
            HybridIdentitySelection selection = selector.select(
                    event.getUsername(),
                    event.getUniqueId()
            );
            event.setResult(switch (selection) {
                case ONLINE -> PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                case OFFLINE -> PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
            });
        } catch (RuntimeException exception) {
            logger.error(
                    "Unable to select monban hybrid authentication flow for player {} (pre-login UUID: {}).",
                    event.getUsername(),
                    event.getUniqueId(),
                    exception
            );
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(ACCESS_ERROR_MESSAGE));
        }
    }
}
