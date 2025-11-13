package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.NekoListBase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordBot {
    
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
            if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
                getLogger().severe("Discord bot token not configured!");
                starting = false;
                return false;
            }
            
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(new DiscordEventListener(plugin))
                .build();
            
            jda.awaitReady();
            
            jda.updateCommands().addCommands(new DiscordEventListener(plugin).getSlashCommands()).queue();
            
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
}