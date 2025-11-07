package hanamuramiyu.pawkin.net.command;

import hanamuramiyu.pawkin.net.NekoList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class NekoListCommand implements CommandExecutor, TabCompleter {
    
    private final NekoList plugin;
    
    public NekoListCommand(NekoList plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendPrivateMessage(sender, "usage");
            return true;
        }
        
        if (!sender.hasPermission("nekolist.use") && !sender.isOp()) {
            sendPrivateMessage(sender, "no-permission");
            return true;
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
                    return true;
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
                    return true;
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
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "reload", "on", "off", "list", "add", "remove");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }
    
    private void sendPrivateMessage(CommandSender sender, String path) {
        String message = plugin.getMessage(path).replaceAll("&[0-9a-fk-or]", "");
        sender.sendMessage(message);
    }
    
    private void sendPrivateMessage(CommandSender sender, String path, String playerName) {
        String message = plugin.getMessage(path).replace("%player%", playerName).replaceAll("&[0-9a-fk-or]", "");
        sender.sendMessage(message);
    }
    
    private void sendPrivateRawMessage(CommandSender sender, String message) {
        sender.sendMessage(message.replaceAll("&[0-9a-fk-or]", ""));
    }
}