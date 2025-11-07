package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoList;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

public class DiscordEventListener extends ListenerAdapter {
    
    private final NekoList plugin;
    
    public DiscordEventListener(NekoList plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!hasPermission(event)) {
            event.reply(getMessage("discord.no-permission")).setEphemeral(true).queue();
            return;
        }
        
        switch (event.getName()) {
            case "ping":
                long time = System.currentTimeMillis();
                event.reply(getMessage("discord.ping")).setEphemeral(true)
                    .flatMap(v ->
                        event.getHook().editOriginalFormat(getMessage("discord.pong"), System.currentTimeMillis() - time)
                    ).queue();
                break;
                
            case "whitelist":
                if (event.getSubcommandName() == null) {
                    event.reply(getMessage("discord.whitelist-usage")).setEphemeral(true).queue();
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
        List<String> allowedRoles = plugin.getConfig().getStringList("discord-bot.allowed-roles");
        List<String> allowedUsers = plugin.getConfig().getStringList("discord-bot.allowed-users");
        
        if (allowedRoles.isEmpty() && allowedUsers.isEmpty()) {
            return false;
        }
        
        String userId = event.getUser().getId();
        
        if (allowedUsers.contains(userId)) {
            return true;
        }
        
        if (event.getMember() != null) {
            return event.getMember().getRoles().stream()
                .anyMatch(role -> allowedRoles.contains(role.getId()));
        }
        
        return false;
    }
    
    private String getMessage(String path) {
        return plugin.getMessage(path).replaceAll("&[0-9a-fk-or]", "");
    }
    
    private String getCleanMessage(String path) {
        return plugin.getMessage(path).replaceAll("§[0-9a-fk-or]", "");
    }
    
    public SlashCommandData[] getSlashCommands() {
        String pingDesc = getMessage("discord.ping-description");
        String whitelistDesc = getMessage("discord.whitelist-description");
        String addDesc = getMessage("discord.add-description");
        String removeDesc = getMessage("discord.remove-description");
        String listDesc = getMessage("discord.list-description");
        String statusDesc = getMessage("discord.status-description");
        String playerOptionDesc = getMessage("discord.player-option-description");
        
        return new SlashCommandData[] {
            Commands.slash("ping", pingDesc),
            Commands.slash("whitelist", whitelistDesc)
                .addSubcommands(
                    new SubcommandData("add", addDesc)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", playerOptionDesc, true),
                    new SubcommandData("remove", removeDesc)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", playerOptionDesc, true),
                    new SubcommandData("list", listDesc),
                    new SubcommandData("status", statusDesc)
                )
        };
    }
}