package hanamuramiyu.monban.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.function.BiConsumer;

public final class PaperStartupAccessGuard implements Listener {
    private static final Component NOT_READY_MESSAGE = Component.text(
            "monban is not ready to verify access. Please try again later."
    );

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        enforce(event.getLoginResult(), event::disallow);
    }

    static void enforce(
            AsyncPlayerPreLoginEvent.Result currentResult,
            BiConsumer<AsyncPlayerPreLoginEvent.Result, Component> disallow
    ) {
        if (currentResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        disallow.accept(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, NOT_READY_MESSAGE);
    }
}
