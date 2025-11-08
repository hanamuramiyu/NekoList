package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoListBase;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Map;

public class DiscordEventListener extends ListenerAdapter {
    
    private final NekoListBase plugin;
    
    public DiscordEventListener(NekoListBase plugin) {
        this.plugin = plugin;
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
                        if (plugin.isPlayerWhitelisted(playerToAdd)) {
                            event.reply(getCleanMessage("player-already-whitelisted").replace("%player%", playerToAdd)).setEphemeral(true).queue();
                        } else {
                            plugin.addPlayerToWhitelist(playerToAdd);
                            event.reply(getCleanMessage("player-added").replace("%player%", playerToAdd)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "remove":
                        String playerToRemove = event.getOption("player").getAsString();
                        if (!plugin.isPlayerWhitelisted(playerToRemove)) {
                            event.reply(getCleanMessage("player-not-whitelisted").replace("%player%", playerToRemove)).setEphemeral(true).queue();
                        } else {
                            plugin.removePlayerFromWhitelist(playerToRemove);
                            event.reply(getCleanMessage("player-removed").replace("%player%", playerToRemove)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "list":
                        var players = plugin.getWhitelistedPlayers();
                        if (players.isEmpty()) {
                            event.reply(getCleanMessage("whitelist-empty")).setEphemeral(true).queue();
                        } else {
                            String playerList = String.join(", ", players);
                            event.reply(getCleanMessage("whitelist-list").replace("%players%", playerList)).setEphemeral(true).queue();
                        }
                        break;
                        
                    case "status":
                        boolean enabled = plugin.isWhitelistEnabled();
                        String statusMessage = enabled ? getCleanMessage("whitelist-enabled") : getCleanMessage("whitelist-disabled");
                        event.reply(statusMessage).setEphemeral(true).queue();
                        break;
                }
                break;
        }
    }
    
    private boolean hasPermission(SlashCommandInteractionEvent event) {
        Map<String, Object> config = plugin.getConfig();
        Object discordBotObj = config.get("discord-bot");
        
        List<String> allowedRoles = null;
        List<String> allowedUsers = null;
        
        if (discordBotObj instanceof Map) {
            Map<String, Object> discordConfig = (Map<String, Object>) discordBotObj;
            allowedRoles = (List<String>) discordConfig.get("allowed-roles");
            allowedUsers = (List<String>) discordConfig.get("allowed-users");
        } else {
            try {
                Class<?> configSectionClass = Class.forName("org.bukkit.configuration.ConfigurationSection");
                if (configSectionClass.isInstance(discordBotObj)) {
                    Object rolesObj = configSectionClass.getMethod("getStringList", String.class).invoke(discordBotObj, "allowed-roles");
                    Object usersObj = configSectionClass.getMethod("getStringList", String.class).invoke(discordBotObj, "allowed-users");
                    
                    if (rolesObj instanceof List) {
                        allowedRoles = (List<String>) rolesObj;
                    }
                    if (usersObj instanceof List) {
                        allowedUsers = (List<String>) usersObj;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        if (allowedRoles == null || allowedUsers == null) {
            return false;
        }
        
        if (allowedRoles.isEmpty() && allowedUsers.isEmpty()) {
            return false;
        }
        
        String userId = event.getUser().getId();
        
        if (allowedUsers.contains(userId)) {
            return true;
        }
        
        if (event.getMember() != null) {
            List<String> finalAllowedRoles = allowedRoles;
            return event.getMember().getRoles().stream()
                .anyMatch(role -> finalAllowedRoles.contains(role.getId()));
        }
        
        return false;
    }
    
    private String getCleanMessage(String path) {
        String message = plugin.getMessage(path);
        if (message.startsWith("Message not found:")) {
            return getDefaultMessage(path);
        }
        return message.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
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
            Commands.slash("ping", getDefaultMessage("discord.ping-description")),
            Commands.slash("whitelist", getDefaultMessage("discord.whitelist-description"))
                .addSubcommands(
                    new SubcommandData("add", getDefaultMessage("discord.add-description"))
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", getDefaultMessage("discord.player-option-description"), true),
                    new SubcommandData("remove", getDefaultMessage("discord.remove-description"))
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", getDefaultMessage("discord.player-option-description"), true),
                    new SubcommandData("list", getDefaultMessage("discord.list-description")),
                    new SubcommandData("status", getDefaultMessage("discord.status-description"))
                )
        };
    }
}