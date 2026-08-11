package hanamuramiyu.monban.bukkit.whitelist;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.Set;

public final class BukkitNativeWhitelistGuard implements Listener {
    private static final Set<String> NATIVE_LABELS = Set.of(
            "whitelist",
            "minecraft:whitelist",
            "bukkit:whitelist"
    );

    public static void requireDisabledAtStartup(Server server) {
        if (server.hasWhitelist()) {
            throw new IllegalStateException(
                    "The vanilla Minecraft whitelist is enabled.\n"
                            + "monban must be the only whitelist authority on this server.\n\n"
                            + "Set:\nwhite-list=false\n\n"
                            + "Then configure monban through:\nplugins/monban/config.yml\n\n"
                            + "Use /monban whitelist ... for whitelist administration."
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isNativeWhitelistCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        sendBlockedMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!isNativeWhitelistCommand(event.getCommand())) {
            return;
        }
        event.setCancelled(true);
        sendBlockedMessage(event.getSender());
    }

    static boolean isNativeWhitelistCommand(String commandLine) {
        if (commandLine == null) {
            return false;
        }
        String value = commandLine.stripLeading();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int separator = value.indexOf(' ');
        String label = separator < 0 ? value : value.substring(0, separator);
        return NATIVE_LABELS.contains(label.toLowerCase(Locale.ROOT));
    }

    static void sendBlockedMessage(CommandSender sender) {
        sender.sendMessage("The vanilla Minecraft whitelist is disabled while monban is active.");
        sender.sendMessage("Use monban instead:");
        sender.sendMessage("/monban whitelist add offline <name>");
        sender.sendMessage("/monban whitelist add online <name> <uuid>");
        sender.sendMessage("/monban whitelist remove offline <name>");
        sender.sendMessage("/monban whitelist remove online <name> <uuid>");
        sender.sendMessage("/monban whitelist list");
        sender.sendMessage("Whitelist enable/disable is currently configured in:");
        sender.sendMessage("plugins/monban/config.yml (restart required)");
    }
}
