package hanamuramiyu.pawkin.net.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Map;

public class DiscordEventListener extends ListenerAdapter {
    
    private final DiscordBot bot;
    
    public DiscordEventListener(DiscordBot bot) {
        this.bot = bot;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!hasPermission(event)) {
            event.reply(getCleanMessage("discord.no-permission")).setEphemeral(true).queue();
            return;
        }
        
        switch (event.getName()) {
            case "ping":
                long time = System.currentTimeMillis();
                event.reply(getCleanMessage("discord.ping")).setEphemeral(true)
                    .flatMap(v ->
                        event.getHook().editOriginalFormat(getCleanMessage("discord.pong"), System.currentTimeMillis() - time)
                    ).queue();
                break;
                
            case "whitelist":
                if (event.getSubcommandName() == null) {
                    event.reply(getCleanMessage("discord.whitelist-usage")).setEphemeral(true).queue();
                    return;
                }
                
                switch (event.getSubcommandName()) {
                    case "add":
                        String playerToAdd = event.getOption("player").getAsString();
                        if (isPlayerWhitelisted(playerToAdd)) {
                            event.reply(getCleanMessage("player-already-whitelisted").replace("%player%", playerToAdd)).setEphemeral(true).queue();
                        } else {
                            addPlayerToWhitelist(playerToAdd);
                            event.reply(getCleanMessage("player-added").replace("%player%", playerToAdd)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "remove":
                        String playerToRemove = event.getOption("player").getAsString();
                        if (!isPlayerWhitelisted(playerToRemove)) {
                            event.reply(getCleanMessage("player-not-whitelisted").replace("%player%", playerToRemove)).setEphemeral(true).queue();
                        } else {
                            removePlayerFromWhitelist(playerToRemove);
                            event.reply(getCleanMessage("player-removed").replace("%player%", playerToRemove)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "list":
                        var players = getWhitelistedPlayers();
                        if (players.isEmpty()) {
                            event.reply(getCleanMessage("whitelist-empty")).setEphemeral(true).queue();
                        } else {
                            String playerList = String.join(", ", players);
                            event.reply(getCleanMessage("whitelist-list").replace("%players%", playerList)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "status":
                        boolean enabled = isWhitelistEnabled();
                        String statusMessage = enabled ? getCleanMessage("whitelist-enabled") : getCleanMessage("whitelist-disabled");
                        event.reply(statusMessage).setEphemeral(true).queue();
                        break;
                }
                break;
        }
    }
    
    @SuppressWarnings("unchecked")
    private boolean hasPermission(SlashCommandInteractionEvent event) {
        try {
            Map<String, Object> config = bot.getPluginConfig();
            if (config == null) {
                return false;
            }
            
            List<String> allowedRoles = (List<String>) config.get("discord-bot.allowed-roles");
            List<String> allowedUsers = (List<String>) config.get("discord-bot.allowed-users");
            
            if (allowedRoles == null) allowedRoles = java.util.Collections.emptyList();
            if (allowedUsers == null) allowedUsers = java.util.Collections.emptyList();
            
            if (allowedRoles.isEmpty() && allowedUsers.isEmpty()) {
                return false;
            }
            
            String userId = event.getUser().getId();
            
            if (allowedUsers.contains(userId)) {
                return true;
            }
            
            if (event.getMember() != null && !allowedRoles.isEmpty()) {
                for (net.dv8tion.jda.api.entities.Role role : event.getMember().getRoles()) {
                    if (allowedRoles.contains(role.getId())) {
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getCleanMessage(String path) {
        String message = getMessage(path);
        if (message.startsWith("Message not found:")) {
            return getDefaultMessage(path);
        }
        return message.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
    }
    
    private String getMessage(String path) {
        Object plugin = bot.getPlugin();
        try {
            Object result = plugin.getClass().getMethod("getMessage", String.class).invoke(plugin, path);
            return result instanceof String ? (String) result : "Message not found: " + path;
        } catch (Exception e) {
            return "Message not found: " + path;
        }
    }
    
    private boolean isPlayerWhitelisted(String playerName) {
        Object plugin = bot.getPlugin();
        try {
            Object result = plugin.getClass().getMethod("isPlayerWhitelisted", String.class).invoke(plugin, playerName);
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void addPlayerToWhitelist(String playerName) {
        Object plugin = bot.getPlugin();
        try {
            plugin.getClass().getMethod("addPlayerToWhitelist", String.class).invoke(plugin, playerName);
        } catch (Exception e) {
        }
    }
    
    private void removePlayerFromWhitelist(String playerName) {
        Object plugin = bot.getPlugin();
        try {
            plugin.getClass().getMethod("removePlayerFromWhitelist", String.class).invoke(plugin, playerName);
        } catch (Exception e) {
        }
    }
    
    @SuppressWarnings("unchecked")
    private java.util.Set<String> getWhitelistedPlayers() {
        Object plugin = bot.getPlugin();
        try {
            Object result = plugin.getClass().getMethod("getWhitelistedPlayers").invoke(plugin);
            return result instanceof java.util.Set ? (java.util.Set<String>) result : java.util.Collections.emptySet();
        } catch (Exception e) {
            return java.util.Collections.emptySet();
        }
    }
    
    private boolean isWhitelistEnabled() {
        Object plugin = bot.getPlugin();
        try {
            Object result = plugin.getClass().getMethod("isWhitelistEnabled").invoke(plugin);
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getDefaultMessage(String path) {
        switch (path) {
            case "discord.ping-description": return "Check bot latency";
            case "discord.whitelist-description": return "Manage server whitelist";
            case "discord.add-description": return "Add player to whitelist";
            case "discord.remove-description": return "Remove player from whitelist";
            case "discord.list-description": return "List whitelisted players";
            case "discord.status-description": return "Check whitelist status";
            case "discord.player-option-description": return "Player username";
            case "discord.ping": return "Pong!";
            case "discord.pong": return "Ping: %dms";
            case "discord.whitelist-usage": return "Use /whitelist add/remove/list/status";
            case "discord.no-permission": return "You don't have permission to use this command";
            default: return "Command executed";
        }
    }
    
    public SlashCommandData[] getSlashCommands() {
        return new SlashCommandData[] {
            Commands.slash("ping", getMessage("discord.ping-description")),
            Commands.slash("whitelist", getMessage("discord.whitelist-description"))
                .addSubcommands(
                    new SubcommandData("add", getMessage("discord.add-description"))
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", getMessage("discord.player-option-description"), true),
                    new SubcommandData("remove", getMessage("discord.remove-description"))
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", getMessage("discord.player-option-description"), true),
                    new SubcommandData("list", getMessage("discord.list-description")),
                    new SubcommandData("status", getMessage("discord.status-description"))
                )
        };
    }
}