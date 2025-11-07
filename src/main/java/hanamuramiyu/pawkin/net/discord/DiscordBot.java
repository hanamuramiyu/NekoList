package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoList;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.util.List;

public class DiscordBot {
    
    private final NekoList plugin;
    private JDA jda;
    private boolean enabled;
    
    public DiscordBot(NekoList plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord-bot.enabled", false);
    }
    
    public void startBot() {
        if (!enabled) {
            plugin.getLogger().info("Discord bot is disabled in config");
            return;
        }
        
        String token = plugin.getConfig().getString("discord-bot.token");
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord bot token not set in config");
            return;
        }
        
        List<String> allowedRoles = plugin.getConfig().getStringList("discord-bot.allowed-roles");
        List<String> allowedUsers = plugin.getConfig().getStringList("discord-bot.allowed-users");
        
        if (allowedRoles.isEmpty() && allowedUsers.isEmpty()) {
            plugin.getLogger().warning("Discord bot is enabled but no allowed roles or users are configured.");
            plugin.getLogger().warning("Please configure at least one role ID or user ID in discord-bot.allowed-roles or discord-bot.allowed-users");
            return;
        }
        
        try {
            DiscordEventListener eventListener = new DiscordEventListener(plugin);
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .setActivity(Activity.playing("Minecraft"))
                .addEventListeners(eventListener)
                .build();
            
            jda.awaitReady();
            
            jda.updateCommands().addCommands(eventListener.getSlashCommands()).queue();
            
            plugin.getLogger().info("Discord bot started successfully with language: " + plugin.getConfig().getString("language"));
            
        } catch (InvalidTokenException e) {
            plugin.getLogger().warning("Invalid Discord bot token provided. Discord bot will not start.");
            plugin.getLogger().warning("Please check your token in config.yml and ensure it's a valid bot token.");
            jda = null;
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Discord bot startup was interrupted");
            Thread.currentThread().interrupt();
            jda = null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start Discord bot: " + e.getMessage());
            plugin.getLogger().warning("This might be due to an invalid token or network issues.");
            plugin.getLogger().warning("Plugin will continue to work without Discord bot.");
            jda = null;
        }
    }
    
    public void stopBot() {
        if (jda != null) {
            try {
                jda.shutdown();
                plugin.getLogger().info("Discord bot stopped");
            } catch (Exception e) {
                plugin.getLogger().warning("Error while stopping Discord bot: " + e.getMessage());
            }
            jda = null;
        }
    }
    
    public boolean isEnabled() {
        return enabled && jda != null;
    }
    
    public JDA getJDA() {
        return jda;
    }
}