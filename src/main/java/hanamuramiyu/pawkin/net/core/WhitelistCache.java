package hanamuramiyu.pawkin.net.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class WhitelistCache {
    private final FileStorageManager fileStorageManager;
    private boolean enabled = false;
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Set<String> playersWithoutUuid = ConcurrentHashMap.newKeySet();
    private long lastUpdate = 0;
    private static final long CACHE_TTL = 5000;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final UUID NULL_UUID = new UUID(0L, 0L);
    
    public WhitelistCache(FileStorageManager fileStorageManager) {
        this.fileStorageManager = fileStorageManager;
    }
    
    public void initialize() {
        reloadFromFile();
    }
    
    public void reloadFromFile() {
        lock.writeLock().lock();
        try {
            var config = fileStorageManager.loadConfig();
            enabled = config.getBoolean("enabled", false);
            
            nameToUuid.clear();
            uuidToName.clear();
            playersWithoutUuid.clear();
            
            if (config.getConfigurationSection("players") != null) {
                for (String key : config.getConfigurationSection("players").getKeys(false)) {
                    var playerSection = config.getConfigurationSection("players." + key);
                    if (playerSection != null) {
                        String name = playerSection.getString("name", key);
                        String uuidStr = playerSection.getString("uuid");
                        
                        if (uuidStr != null) {
                            try {
                                UUID uuid = UUID.fromString(uuidStr);
                                nameToUuid.put(name.toLowerCase(), uuid);
                                uuidToName.put(uuid, name.toLowerCase());
                            } catch (IllegalArgumentException ignored) {
                                playersWithoutUuid.add(name.toLowerCase());
                            }
                        } else {
                            playersWithoutUuid.add(name.toLowerCase());
                        }
                    }
                }
            }
            
            lastUpdate = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void checkCache() {
        lock.readLock().lock();
        try {
            if (System.currentTimeMillis() - lastUpdate > CACHE_TTL) {
                lock.readLock().unlock();
                reloadFromFile();
                lock.readLock().lock();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isWhitelistEnabled() {
        checkCache();
        lock.readLock().lock();
        try {
            return enabled;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        lock.writeLock().lock();
        try {
            this.enabled = enabled;
            saveToFile();
            lastUpdate = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public Set<String> getWhitelistedPlayers() {
        checkCache();
        lock.readLock().lock();
        try {
            Set<String> players = new HashSet<>();
            players.addAll(nameToUuid.keySet());
            players.addAll(playersWithoutUuid);
            return players;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean isPlayerWhitelisted(String playerName, UUID playerUUID) {
        checkCache();
        lock.readLock().lock();
        try {
            if (playerName != null) {
                String nameLower = playerName.toLowerCase();
                if (nameToUuid.containsKey(nameLower) || playersWithoutUuid.contains(nameLower)) {
                    return true;
                }
            }
            
            if (playerUUID != null) {
                return uuidToName.containsKey(playerUUID);
            }
            
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void addPlayer(String playerName, UUID uuid) {
        lock.writeLock().lock();
        try {
            String nameLower = playerName.toLowerCase();
            
            if (uuid != null) {
                nameToUuid.put(nameLower, uuid);
                uuidToName.put(uuid, nameLower);
                playersWithoutUuid.remove(nameLower);
            } else {
                playersWithoutUuid.add(nameLower);
                UUID existingUuid = nameToUuid.remove(nameLower);
                if (existingUuid != null) {
                    uuidToName.remove(existingUuid);
                }
            }
            
            saveToFile();
            lastUpdate = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void removePlayer(String playerName) {
        lock.writeLock().lock();
        try {
            String nameLower = playerName.toLowerCase();
            
            UUID uuid = nameToUuid.remove(nameLower);
            if (uuid != null) {
                uuidToName.remove(uuid);
            }
            playersWithoutUuid.remove(nameLower);
            
            saveToFile();
            lastUpdate = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void saveToFile() {
        var config = fileStorageManager.loadConfig();
        config.set("enabled", enabled);
        
        Map<String, Map<String, Object>> playersMap = new HashMap<>();
        
        lock.readLock().lock();
        try {
            for (var entry : nameToUuid.entrySet()) {
                String name = entry.getKey();
                UUID uuid = entry.getValue();
                
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("name", name);
                if (uuid != null && !NULL_UUID.equals(uuid)) {
                    playerData.put("uuid", uuid.toString());
                }
                
                String key = sanitizeKey(name);
                playersMap.put(key, playerData);
            }
            
            for (String name : playersWithoutUuid) {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("name", name);
                
                String key = sanitizeKey(name);
                playersMap.put(key, playerData);
            }
        } finally {
            lock.readLock().unlock();
        }
        
        config.set("players", playersMap);
        fileStorageManager.saveConfigAsync(config);
    }
    
    private String sanitizeKey(String name) {
        return name.toLowerCase().replace(".", "_dot_").replace(" ", "_space_");
    }
    
    public void clear() {
        lock.writeLock().lock();
        try {
            nameToUuid.clear();
            uuidToName.clear();
            playersWithoutUuid.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}