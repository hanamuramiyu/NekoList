package hanamuramiyu.pawkin.net.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.pawkin.net.velocity.VelocityNekoList;
import hanamuramiyu.pawkin.net.velocity.WhitelistManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

public class VelocityPlayerListener {
    
    private final VelocityNekoList plugin;
    private final WhitelistManager whitelistManager;
    
    public VelocityPlayerListener(VelocityNekoList plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
    }
    
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!whitelistManager.isWhitelistEnabled()) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        if (!whitelistManager.isPlayerWhitelisted(player.getUsername())) {
            String message = getMessage("not-whitelisted").replace("&", "§");
            Component component = LegacyComponentSerializer.legacySection().deserialize(message);
            event.setResult(LoginEvent.ComponentResult.denied(component));
        }
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
}