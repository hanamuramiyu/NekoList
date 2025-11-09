package hanamuramiyu.pawkin.net.velocity;

import org.slf4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class VelocityPluginWrapper {
    private final File dataFolder;
    private final Map<String, Object> config;
    private final Map<String, Object> languageConfig;
    private final WhitelistManager whitelistManager;
    private final Logger logger;
    
    public VelocityPluginWrapper(File dataFolder, Map<String, Object> config, 
                               Map<String, Object> languageConfig, WhitelistManager whitelistManager, Logger logger) {
        this.dataFolder = dataFolder;
        this.config = config;
        this.languageConfig = languageConfig;
        this.whitelistManager = whitelistManager;
        this.logger = logger;
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public Map<String, Object> getLanguageConfig() {
        return languageConfig;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
    
    public String getMessage(String path) {
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
    
    public boolean isPlayerWhitelisted(String playerName) {
        return whitelistManager.isPlayerWhitelisted(playerName);
    }
    
    public void addPlayerToWhitelist(String playerName) {
        whitelistManager.addPlayerToWhitelist(playerName);
    }
    
    public void removePlayerFromWhitelist(String playerName) {
        whitelistManager.removePlayerFromWhitelist(playerName);
    }
    
    public Set<String> getWhitelistedPlayers() {
        return whitelistManager.getWhitelistedPlayers();
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistManager.isWhitelistEnabled();
    }
}