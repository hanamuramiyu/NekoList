package hanamuramiyu.pawkin.net.command;

import hanamuramiyu.pawkin.net.core.NekoList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WhitelistCommand implements CommandExecutor, TabCompleter {
    
    private final NekoList plugin;
    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();
    private static final long COMMAND_COOLDOWN_MS = 2000;
    
    public WhitelistCommand(NekoList plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMiniMessage("usage"));
            return true;
        }
        
        if (!sender.hasPermission("nekolist.use") && !sender.isOp()) {
            sender.sendMessage(plugin.getMiniMessage("no-permission"));
            return true;
        }
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            long now = System.currentTimeMillis();
            Long lastUsed = commandCooldowns.get(player.getUniqueId());
            if (lastUsed != null && (now - lastUsed) < COMMAND_COOLDOWN_MS) {
                sender.sendMessage(plugin.getMiniMessage("errors.cooldown"));
                return true;
            }
            commandCooldowns.put(player.getUniqueId(), now);
        }
        
        switch (args[0].toLowerCase()) {
            case "help":
                sender.sendMessage(plugin.getMiniMessage("usage"));
                break;
                
            case "reload":
                plugin.reloadNekoListConfig();
                sender.sendMessage(plugin.getMiniMessage("reload-success"));
                break;
                
            case "on":
                plugin.setWhitelistEnabled(true);
                sender.sendMessage(plugin.getMiniMessage("whitelist-enabled"));
                break;
                
            case "off":
                plugin.setWhitelistEnabled(false);
                sender.sendMessage(plugin.getMiniMessage("whitelist-disabled"));
                break;
                
            case "list":
                Set<String> players = plugin.getWhitelistedPlayers();
                if (players.isEmpty()) {
                    sender.sendMessage(plugin.getMiniMessage("whitelist-empty"));
                } else {
                    String playerList = String.join(", ", players);
                    sender.sendMessage(plugin.getMiniMessage("whitelist-list").replaceText(builder -> 
                        builder.match("%players%").replacement(playerList)));
                }
                break;
                
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMiniMessage("usage"));
                    return true;
                }
                String playerToAdd = args[1];
                if (!isValidPlayerName(playerToAdd)) {
                    sender.sendMessage(plugin.getMiniMessage("errors.invalid-name"));
                    return true;
                }
                if (plugin.isPlayerWhitelisted(playerToAdd)) {
                    sender.sendMessage(plugin.getMiniMessage("player-already-whitelisted").replaceText(builder -> 
                        builder.match("%player%").replacement(playerToAdd)));
                } else {
                    plugin.addPlayerToWhitelist(playerToAdd);
                    sender.sendMessage(plugin.getMiniMessage("player-added").replaceText(builder -> 
                        builder.match("%player%").replacement(playerToAdd)));
                }
                break;
                
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMiniMessage("usage"));
                    return true;
                }
                String playerToRemove = args[1];
                if (!plugin.isPlayerWhitelisted(playerToRemove)) {
                    sender.sendMessage(plugin.getMiniMessage("player-not-whitelisted").replaceText(builder -> 
                        builder.match("%player%").replacement(playerToRemove)));
                } else {
                    plugin.removePlayerFromWhitelist(playerToRemove);
                    sender.sendMessage(plugin.getMiniMessage("player-removed").replaceText(builder -> 
                        builder.match("%player%").replacement(playerToRemove)));
                }
                break;
                
            default:
                sender.sendMessage(plugin.getMiniMessage("usage"));
                break;
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "reload", "on", "off", "list", "add", "remove")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove")) {
                Set<String> players = plugin.getWhitelistedPlayers();
                return players.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("add")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
    
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_]{1,16}$");
    }
}