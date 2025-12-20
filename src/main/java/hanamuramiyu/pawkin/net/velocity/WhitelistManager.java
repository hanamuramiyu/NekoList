package hanamuramiyu.pawkin.net.velocity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.UUID;
import java.util.HashMap;

public class WhitelistManager {
    private final File whitelistFile;
    private boolean whitelistEnabled;
    private Map<String, PlayerData> whitelistedPlayers;
    private final boolean onlineMode;
    private static final Logger logger = LoggerFactory.getLogger(WhitelistManager.class);
    
    public WhitelistManager(File dataFolder, boolean onlineMode) {
        this.whitelistFile = new File(dataFolder, "whitelist.yml");
        this.onlineMode = onlineMode;
        loadWhitelist();
    }
    
    @SuppressWarnings("unchecked")
    public synchronized void loadWhitelist() {
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            Map<String, Object> whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = Collections.synchronizedMap(new HashMap<>());
                whitelistConfig.put("enabled", false);
                whitelistConfig.put("players", Collections.synchronizedMap(new HashMap<>()));
                saveWhitelistConfig(whitelistConfig);
            }
            
            whitelistEnabled = (Boolean) whitelistConfig.getOrDefault("enabled", false);
            whitelistedPlayers = new HashMap<>();
            
            int migratedCount = 0;
            int noUUIDCount = 0;
            
            Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
            if (players != null) {
                for (Map.Entry<String, Object> entry : players.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (value instanceof Boolean) {
                        whitelistedPlayers.put(key.toLowerCase(), new PlayerData(key, null));
                        migratedCount++;
                        if (onlineMode) {
                            noUUIDCount++;
                        }
                    } else if (value instanceof Map) {
                        Map<String, Object> playerMap = (Map<String, Object>) value;
                        String name = (String) playerMap.get("name");
                        String uuidStr = (String) playerMap.get("uuid");
                        
                        UUID uuid = null;
                        if (uuidStr != null) {
                            try {
                                uuid = UUID.fromString(uuidStr);
                            } catch (IllegalArgumentException e) {
                                logger.warn("Invalid UUID format for player {}: {}", key, uuidStr);
                            }
                        }
                        
                        if (name == null) {
                            name = key;
                        }
                        
                        whitelistedPlayers.put(key.toLowerCase(), new PlayerData(name, uuid));
                        
                        if (onlineMode && uuid == null) {
                            noUUIDCount++;
                        }
                    }
                }
            }
            
            if (migratedCount > 0) {
                logger.info("Migrated {} legacy entries to new format", migratedCount);
                saveWhitelist();
            }
            
            if (noUUIDCount > 0 && onlineMode) {
                logger.warn("Found {} entries without UUID in online-mode server", noUUIDCount);
            }
            
            logger.info("Loaded {} players: {}", whitelistedPlayers.size(), whitelistedPlayers.keySet());
            
        } catch (Exception e) {
            logger.error("CRITICAL ERROR: Could not load whitelist.yml: {}", e.getMessage());
            e.printStackTrace();
            whitelistEnabled = false;
            whitelistedPlayers = new HashMap<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    private synchronized void saveWhitelistConfig(Map<String, Object> whitelistConfig) {
        try (FileWriter writer = new FileWriter(whitelistFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(whitelistConfig, writer);
        } catch (Exception e) {
            logger.error("Could not save whitelist.yml: {}", e.getMessage());
        }
    }
    
    private synchronized void saveWhitelist() {
        Map<String, Object> whitelistConfig = new HashMap<>();
        whitelistConfig.put("enabled", whitelistEnabled);
        
        Map<String, Object> players = new HashMap<>();
        for (PlayerData data : whitelistedPlayers.values()) {
            String key = data.getName().toLowerCase();
            Map<String, Object> playerMap = new HashMap<>();
            playerMap.put("name", data.getName());
            if (data.getUuid() != null) {
                playerMap.put("uuid", data.getUuid().toString());
            }
            players.put(key, playerMap);
        }
        
        whitelistConfig.put("players", players);
        saveWhitelistConfig(whitelistConfig);
    }
    
    public synchronized void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        saveWhitelist();
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public Set<String> getWhitelistedPlayers() {
        Set<String> names = new HashSet<>();
        for (PlayerData data : whitelistedPlayers.values()) {
            names.add(data.getName());
        }
        return names;
    }
    
    public boolean isPlayerWhitelisted(String playerName) {
        String lowerName = playerName.toLowerCase();
        boolean found = whitelistedPlayers.containsKey(lowerName);
        return found;
    }
    
    public boolean isPlayerWhitelisted(UUID playerUUID) {
        for (PlayerData data : whitelistedPlayers.values()) {
            if (playerUUID.equals(data.getUuid())) {
                return true;
            }
        }
        return false;
    }
    
    public void addPlayerToWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.put(lowerName, new PlayerData(playerName, null));
        saveWhitelist();
    }
    
    public void addPlayerToWhitelist(String playerName, UUID uuid) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.put(lowerName, new PlayerData(playerName, uuid));
        saveWhitelist();
    }
    
    public void removePlayerFromWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.remove(lowerName);
        saveWhitelist();
    }
    
    public void updatePlayerData(String playerName, UUID uuid) {
        String lowerName = playerName.toLowerCase();
        PlayerData existing = whitelistedPlayers.get(lowerName);
        if (existing != null) {
            whitelistedPlayers.put(lowerName, new PlayerData(playerName, uuid));
            saveWhitelist();
        }
    }
    
    public boolean isOnlineMode() {
        return onlineMode;
    }
    
    private static class PlayerData {
        private final String name;
        private final UUID uuid;
        
        public PlayerData(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
        
        public String getName() {
            return name;
        }
        
        public UUID getUuid() {
            return uuid;
        }
    }
}