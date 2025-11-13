package hanamuramiyu.pawkin.net.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.pawkin.net.velocity.VelocityPluginWrapper;
import hanamuramiyu.pawkin.net.velocity.WhitelistManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class VelocityPlayerListener {
    
    private final VelocityPluginWrapper plugin;
    private final WhitelistManager whitelistManager;
    
    public VelocityPlayerListener(VelocityPluginWrapper plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
    }
    
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!whitelistManager.isWhitelistEnabled()) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        boolean allowed = false;
        String playerName = player.getUsername();
        java.util.UUID playerUUID = player.getUniqueId();
        
        if (whitelistManager.isPlayerWhitelisted(playerName)) {
            allowed = true;
            whitelistManager.updatePlayerData(playerName, playerUUID);
        } else if (whitelistManager.isOnlineMode() && whitelistManager.isPlayerWhitelisted(playerUUID)) {
            allowed = true;
            whitelistManager.updatePlayerData(playerName, playerUUID);
        }
        
        if (!allowed) {
            String message = plugin.getMessage("not-whitelisted");
            Component component = LegacyComponentSerializer.legacySection().deserialize(message);
            event.setResult(LoginEvent.ComponentResult.denied(component));
        }
    }
}