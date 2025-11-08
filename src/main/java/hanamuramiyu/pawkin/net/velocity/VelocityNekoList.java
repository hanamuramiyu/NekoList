package hanamuramiyu.pawkin.net.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.pawkin.net.NekoListBase;
import hanamuramiyu.pawkin.net.velocity.command.VelocityNekoListCommand;
import hanamuramiyu.pawkin.net.velocity.listener.VelocityPlayerListener;
import org.slf4j.Logger;

@Plugin(
    id = "nekolist",
    name = "NekoList",
    version = "1.1.0",
    description = "Advanced whitelist system",
    authors = {"Hanamura Miyu"}
)
public class VelocityNekoList {
    
    private final ProxyServer server;
    private final Logger logger;
    private NekoListBase nekoListBase;
    
    @Inject
    public VelocityNekoList(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.nekoListBase = new NekoListBase();
        nekoListBase.setServer(server);
        nekoListBase.setLogger(logger);
        nekoListBase.setBukkit(false);
        nekoListBase.setDataFolder(new java.io.File("plugins/NekoList"));
        nekoListBase.onEnable();
        
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("whitelist")
                .aliases("wl")
                .plugin(this)
                .build(),
            new VelocityNekoListCommand(nekoListBase)
        );
        
        server.getEventManager().register(this, new VelocityPlayerListener(nekoListBase));
    }
}