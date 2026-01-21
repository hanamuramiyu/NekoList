package hanamuramiyu.pawkin.net.listeners;

import hanamuramiyu.pawkin.net.core.NekoList;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.entity.Player;

public class PlayerLoginListener implements Listener {
    
    private final NekoList plugin;
    
    public PlayerLoginListener(NekoList plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!plugin.isWhitelistEnabled()) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        boolean allowed = false;
        String playerName = player.getName();
        java.util.UUID playerUUID = player.getUniqueId();
        
        if (plugin.isPlayerWhitelisted(playerName)) {
            allowed = true;
            if (plugin.isOnlineMode()) {
                plugin.updatePlayerData(playerName, playerUUID);
            }
        } else if (plugin.isOnlineMode() && plugin.isPlayerWhitelisted(playerUUID)) {
            allowed = true;
            plugin.updatePlayerData(playerName, playerUUID);
        }
        
        if (!allowed) {
            Component kickMessage = plugin.getMiniMessage("not-whitelisted");
            event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
            event.kickMessage(kickMessage);
        }
    }
}