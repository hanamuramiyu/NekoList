package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoListBase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.util.List;
import java.util.Map;

public class DiscordBot {
    
    private final NekoListBase plugin;
    private JDA jda;
    private boolean enabled;
    
    public DiscordBot(NekoListBase plugin) {
        this.plugin = plugin;
        Map<String, Object> config = plugin.getConfig();
        if (config == null) {
            this.enabled = false;
            return;
        }
        
        Object discordBotObj = config.get("discord-bot");
        if (discordBotObj instanceof Map) {
            Map<String, Object> discordConfig = (Map<String, Object>) discordBotObj;
            Object enabledObj = discordConfig.get("enabled");
            if (enabledObj instanceof Boolean) {
                this.enabled = (Boolean) enabledObj;
            } else {
                this.enabled = false;
            }
        } else {
            try {
                Class<?> configSectionClass = Class.forName("org.bukkit.configuration.ConfigurationSection");
                if (configSectionClass.isInstance(discordBotObj)) {
                    Object enabledObj = configSectionClass.getMethod("getBoolean", String.class).invoke(discordBotObj, "enabled");
                    if (enabledObj instanceof Boolean) {
                        this.enabled = (Boolean) enabledObj;
                    } else {
                        this.enabled = false;
                    }
                } else {
                    this.enabled = false;
                }
            } catch (Exception e) {
                this.enabled = false;
            }
        }
    }
    
    public void startBot() {
        if (!enabled) {
            return;
        }
        
        Map<String, Object> config = plugin.getConfig();
        if (config == null) {
            return;
        }
        
        Object discordBotObj = config.get("discord-bot");
        if (discordBotObj == null) {
            return;
        }
        
        String token = null;
        List<String> allowedRoles = null;
        List<String> allowedUsers = null;
        
        if (discordBotObj instanceof Map) {
            Map<String, Object> discordConfig = (Map<String, Object>) discordBotObj;
            token = (String) discordConfig.get("token");
            allowedRoles = (List<String>) discordConfig.get("allowed-roles");
            allowedUsers = (List<String>) discordConfig.get("allowed-users");
        } else {
            try {
                Class<?> configSectionClass = Class.forName("org.bukkit.configuration.ConfigurationSection");
                if (configSectionClass.isInstance(discordBotObj)) {
                    token = (String) configSectionClass.getMethod("getString", String.class).invoke(discordBotObj, "token");
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
                plugin.getLogger().warn("Failed to read discord-bot config values");
            }
        }
        
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warn("Discord bot token not set in config");
            return;
        }
        
        if ((allowedRoles == null || allowedRoles.isEmpty()) && (allowedUsers == null || allowedUsers.isEmpty())) {
            plugin.getLogger().warn("Discord bot is enabled but no allowed roles or users are configured");
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
            
            plugin.getLogger().info("Discord bot started successfully");
            
        } catch (InvalidTokenException e) {
            plugin.getLogger().warn("Invalid Discord bot token provided");
            jda = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda = null;
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to start Discord bot: " + e.getMessage());
            jda = null;
        }
    }
    
    public void stopBot() {
        if (jda != null) {
            try {
                jda.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warn("Error while stopping Discord bot");
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