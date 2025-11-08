package hanamuramiyu.pawkin.net.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.pawkin.net.NekoListBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class VelocityPlayerListener {
    
    private final NekoListBase plugin;
    
    public VelocityPlayerListener(NekoListBase plugin) {
        this.plugin = plugin;
    }
    
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        if (!plugin.isWhitelistEnabled()) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        if (!plugin.isPlayerWhitelisted(player.getUsername())) {
            String message = plugin.getMessage("not-whitelisted").replace("&", "§");
            Component component = LegacyComponentSerializer.legacySection().deserialize(message);
            event.setResult(LoginEvent.ComponentResult.denied(component));
        }
    }
}