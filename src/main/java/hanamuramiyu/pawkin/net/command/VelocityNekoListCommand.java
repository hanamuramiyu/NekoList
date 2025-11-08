package hanamuramiyu.pawkin.net.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.pawkin.net.NekoListBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VelocityNekoListCommand implements SimpleCommand {
    
    private final NekoListBase plugin;
    
    public VelocityNekoListCommand(NekoListBase plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            sendPrivateMessage(sender, "usage");
            return;
        }
        
        if (!sender.hasPermission("nekolist.use")) {
            sendPrivateMessage(sender, "no-permission");
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "help":
                sendPrivateMessage(sender, "usage");
                break;
                
            case "reload":
                plugin.reloadNekoListConfig();
                sendPrivateMessage(sender, "reload-success");
                break;
                
            case "on":
                plugin.setWhitelistEnabled(true);
                sendPrivateMessage(sender, "whitelist-enabled");
                break;
                
            case "off":
                plugin.setWhitelistEnabled(false);
                sendPrivateMessage(sender, "whitelist-disabled");
                break;
                
            case "list":
                Set<String> players = plugin.getWhitelistedPlayers();
                if (players.isEmpty()) {
                    sendPrivateMessage(sender, "whitelist-empty");
                } else {
                    String playerList = String.join(", ", players);
                    String message = plugin.getMessage("whitelist-list").replace("%players%", playerList);
                    sendPrivateRawMessage(sender, message);
                }
                break;
                
            case "add":
                if (args.length < 2) {
                    sendPrivateMessage(sender, "usage");
                    return;
                }
                String playerToAdd = args[1];
                if (plugin.isPlayerWhitelisted(playerToAdd)) {
                    sendPrivateMessage(sender, "player-already-whitelisted", playerToAdd);
                } else {
                    plugin.addPlayerToWhitelist(playerToAdd);
                    sendPrivateMessage(sender, "player-added", playerToAdd);
                }
                break;
                
            case "remove":
                if (args.length < 2) {
                    sendPrivateMessage(sender, "usage");
                    return;
                }
                String playerToRemove = args[1];
                if (!plugin.isPlayerWhitelisted(playerToRemove)) {
                    sendPrivateMessage(sender, "player-not-whitelisted", playerToRemove);
                } else {
                    plugin.removePlayerFromWhitelist(playerToRemove);
                    sendPrivateMessage(sender, "player-removed", playerToRemove);
                }
                break;
                
            default:
                sendPrivateMessage(sender, "usage");
                break;
        }
    }
    
    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 1) {
            return CompletableFuture.completedFuture(Arrays.asList("help", "reload", "on", "off", "list", "add", "remove"));
        }
        
        return CompletableFuture.completedFuture(Arrays.asList());
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("nekolist.use");
    }
    
    private void sendPrivateMessage(CommandSource sender, String path) {
        String message = plugin.getMessage(path).replaceAll("&([0-9a-fk-or])", "§$1");
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        sender.sendMessage(component);
    }
    
    private void sendPrivateMessage(CommandSource sender, String path, String playerName) {
        String message = plugin.getMessage(path).replace("%player%", playerName).replaceAll("&([0-9a-fk-or])", "§$1");
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        sender.sendMessage(component);
    }
    
    private void sendPrivateRawMessage(CommandSource sender, String message) {
        String formattedMessage = message.replaceAll("&([0-9a-fk-or])", "§$1");
        Component component = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);
        sender.sendMessage(component);
    }
}