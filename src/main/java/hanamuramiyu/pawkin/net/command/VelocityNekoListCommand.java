package hanamuramiyu.pawkin.net.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import hanamuramiyu.pawkin.net.velocity.VelocityNekoList;
import hanamuramiyu.pawkin.net.velocity.WhitelistManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VelocityNekoListCommand implements SimpleCommand {
    
    private final VelocityNekoList plugin;
    private final WhitelistManager whitelistManager;
    
    public VelocityNekoListCommand(VelocityNekoList plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
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
                whitelistManager.setWhitelistEnabled(true);
                sendPrivateMessage(sender, "whitelist-enabled");
                break;
                
            case "off":
                whitelistManager.setWhitelistEnabled(false);
                sendPrivateMessage(sender, "whitelist-disabled");
                break;
                
            case "list":
                Set<String> players = whitelistManager.getWhitelistedPlayers();
                if (players.isEmpty()) {
                    sendPrivateMessage(sender, "whitelist-empty");
                } else {
                    String playerList = String.join(", ", players);
                    String message = getMessage("whitelist-list").replace("%players%", playerList);
                    sendPrivateRawMessage(sender, message);
                }
                break;
                
            case "add":
                if (args.length < 2) {
                    sendPrivateMessage(sender, "usage");
                    return;
                }
                String playerToAdd = args[1];
                if (whitelistManager.isPlayerWhitelisted(playerToAdd)) {
                    sendPrivateMessage(sender, "player-already-whitelisted", playerToAdd);
                } else {
                    whitelistManager.addPlayerToWhitelist(playerToAdd);
                    sendPrivateMessage(sender, "player-added", playerToAdd);
                }
                break;
                
            case "remove":
                if (args.length < 2) {
                    sendPrivateMessage(sender, "usage");
                    return;
                }
                String playerToRemove = args[1];
                if (!whitelistManager.isPlayerWhitelisted(playerToRemove)) {
                    sendPrivateMessage(sender, "player-not-whitelisted", playerToRemove);
                } else {
                    whitelistManager.removePlayerFromWhitelist(playerToRemove);
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
    
    private String getMessage(String path) {
        Map<String, Object> languageConfig = plugin.getLanguageConfig();
        if (languageConfig == null) {
            return "Message not found: " + path;
        }
        
        String[] parts = path.split("\\.");
        Map<String, Object> current = languageConfig;
        
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return "Message not found: " + path;
            }
        }
        
        String message = (String) current.get(parts[parts.length - 1]);
        if (message == null) {
            return "Message not found: " + path;
        }
        return message.replace('&', '§');
    }
    
    private void sendPrivateMessage(CommandSource sender, String path) {
        String message = getMessage(path);
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        sender.sendMessage(component);
    }
    
    private void sendPrivateMessage(CommandSource sender, String path, String playerName) {
        String message = getMessage(path).replace("%player%", playerName);
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        sender.sendMessage(component);
    }
    
    private void sendPrivateRawMessage(CommandSource sender, String message) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        sender.sendMessage(component);
    }
}