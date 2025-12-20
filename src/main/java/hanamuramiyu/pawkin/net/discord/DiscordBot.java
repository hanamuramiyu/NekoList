package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoListBase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter {
    private final NekoListBase plugin;
    private final Map<String, Object> config;
    private JDA jda;
    private boolean starting = false;
    
    public DiscordBot(NekoListBase plugin, Map<String, Object> config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    public boolean startBot() {
        if (starting) {
            return false;
        }
        
        starting = true;
        try {
            String token = (String) config.get("discord-bot.token");
            if (token == null || token.trim().isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE") || token.length() < 50 || !token.matches("[A-Za-z0-9_.-]+")) {
                getLogger().severe("Invalid Discord bot token!");
                starting = false;
                return false;
            }
            
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();
            
            jda.awaitReady();
            
            jda.updateCommands().addCommands(getSlashCommands()).queue();
            
            getLogger().info("Discord bot started successfully!");
            starting = false;
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            starting = false;
            return false;
        }
    }
    
    public void stopBot() {
        if (jda != null) {
            try {
                jda.shutdown();
                getLogger().info("Discord bot stopped");
            } catch (Exception e) {
                getLogger().severe("Error stopping Discord bot: " + e.getMessage());
            }
            jda = null;
        }
    }
    
    public NekoListBase getPlugin() {
        return plugin;
    }
    
    public Map<String, Object> getPluginConfig() {
        return config;
    }
    
    private Logger getLogger() {
        return Logger.getLogger("NekoList");
    }
    
    private List<CommandData> getSlashCommands() {
        String pingDescription = plugin.getMessage("discord.ping-description");
        String whitelistDescription = plugin.getMessage("discord.whitelist-description");
        String addDescription = plugin.getMessage("discord.add-description");
        String removeDescription = plugin.getMessage("discord.remove-description");
        String listDescription = plugin.getMessage("discord.list-description");
        String statusDescription = plugin.getMessage("discord.status-description");
        
        return List.of(
            Commands.slash("ping", pingDescription),
            Commands.slash("whitelist", whitelistDescription)
                .addSubcommands(
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("add", addDescription)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", plugin.getMessage("discord.player-option-description"), true),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("remove", removeDescription)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", plugin.getMessage("discord.player-option-description"), true),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("list", listDescription),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("status", statusDescription)
                )
        );
    }
    
    private String cleanMessage(String message) {
        if (message == null) return "";
        return message.replace("§a", "")
                     .replace("§c", "")
                     .replace("§e", "")
                     .replace("§6", "")
                     .replace("§7", "")
                     .replace("&a", "")
                     .replace("&c", "")
                     .replace("&e", "")
                     .replace("&6", "")
                     .replace("&7", "");
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        
        String userId = event.getUser().getId();
        
        List<String> allowedUsers = new ArrayList<>();
        List<String> allowedRoles = new ArrayList<>();
        
        try {
            Object usersObj = config.get("discord-bot.allowed-users");
            Object rolesObj = config.get("discord-bot.allowed-roles");
            
            if (usersObj instanceof List) {
                for (Object item : (List<?>) usersObj) {
                    if (item != null) {
                        allowedUsers.add(String.valueOf(item));
                    }
                }
            }
            
            if (rolesObj instanceof List) {
                for (Object item : (List<?>) rolesObj) {
                    if (item != null) {
                        allowedRoles.add(String.valueOf(item));
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error parsing permission lists: " + e.getMessage());
        }
        
        boolean hasPermission = false;
        
        if (allowedUsers.contains(userId)) {
            hasPermission = true;
        }
        
        if (!hasPermission && event.getMember() != null) {
            for (String roleId : allowedRoles) {
                if (event.getMember().getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                    hasPermission = true;
                    break;
                }
            }
        }
        
        if (!hasPermission) {
            event.reply(cleanMessage(plugin.getMessage("discord.no-permission"))).setEphemeral(true).queue();
            return;
        }
        
        if (event.getName().equals("ping")) {
            long time = System.currentTimeMillis();
            event.reply(cleanMessage(plugin.getMessage("discord.ping"))).setEphemeral(true).queue(response -> {
                response.editOriginal(cleanMessage(plugin.getMessage("discord.pong").replace("%d", String.valueOf(System.currentTimeMillis() - time)))).queue();
            });
        } else if (event.getName().equals("whitelist")) {
            if (event.getSubcommandName() == null) {
                event.reply(cleanMessage(plugin.getMessage("discord.whitelist-usage"))).setEphemeral(true).queue();
                return;
            }
            
            switch (event.getSubcommandName()) {
                case "add":
                    String playerToAdd = event.getOption("player").getAsString();
                    if (plugin.isPlayerWhitelisted(playerToAdd)) {
                        event.reply(cleanMessage(plugin.getMessage("player-already-whitelisted").replace("%player%", playerToAdd))).setEphemeral(true).queue();
                    } else {
                        plugin.addPlayerToWhitelist(playerToAdd);
                        event.reply(cleanMessage(plugin.getMessage("player-added").replace("%player%", playerToAdd))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "remove":
                    String playerToRemove = event.getOption("player").getAsString();
                    if (!plugin.isPlayerWhitelisted(playerToRemove)) {
                        event.reply(cleanMessage(plugin.getMessage("player-not-whitelisted").replace("%player%", playerToRemove))).setEphemeral(true).queue();
                    } else {
                        plugin.removePlayerFromWhitelist(playerToRemove);
                        event.reply(cleanMessage(plugin.getMessage("player-removed").replace("%player%", playerToRemove))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "list":
                    java.util.Set<String> players = plugin.getWhitelistedPlayers();
                    if (players.isEmpty()) {
                        event.reply(cleanMessage(plugin.getMessage("whitelist-empty"))).setEphemeral(true).queue();
                    } else {
                        String playerList = String.join(", ", players);
                        event.reply(cleanMessage(plugin.getMessage("whitelist-list").replace("%players%", playerList))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "status":
                    String statusMessage = plugin.isWhitelistEnabled() ? 
                        cleanMessage(plugin.getMessage("whitelist-enabled")) : 
                        cleanMessage(plugin.getMessage("whitelist-disabled"));
                    event.reply(statusMessage).setEphemeral(true).queue();
                    break;
            }
        }
    }
}