package hanamuramiyu.pawkin.net.velocity;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class WhitelistManager {
    private final File whitelistFile;
    private boolean whitelistEnabled;
    private Set<String> whitelistedPlayers;
    
    public WhitelistManager(File dataFolder) {
        this.whitelistFile = new File(dataFolder, "whitelist.yml");
        loadWhitelist();
    }
    
    @SuppressWarnings("unchecked")
    public void loadWhitelist() {
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            Map<String, Object> whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = Collections.synchronizedMap(new java.util.HashMap<>());
                whitelistConfig.put("enabled", false);
                whitelistConfig.put("players", Collections.synchronizedMap(new java.util.HashMap<>()));
                saveWhitelistConfig(whitelistConfig);
            }
            
            whitelistEnabled = (Boolean) whitelistConfig.getOrDefault("enabled", false);
            whitelistedPlayers = new HashSet<>();
            
            Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
            if (players != null) {
                whitelistedPlayers.addAll(players.keySet());
            }
        } catch (Exception e) {
            whitelistEnabled = false;
            whitelistedPlayers = new HashSet<>();
        }
    }
    
    private void saveWhitelistConfig(Map<String, Object> whitelistConfig) {
        try (FileWriter writer = new FileWriter(whitelistFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(whitelistConfig, writer);
        } catch (Exception e) {
        }
    }
    
    @SuppressWarnings("unchecked")
    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            Map<String, Object> whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = Collections.synchronizedMap(new java.util.HashMap<>());
            }
            whitelistConfig.put("enabled", enabled);
            saveWhitelistConfig(whitelistConfig);
        } catch (Exception e) {
        }
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public Set<String> getWhitelistedPlayers() {
        return new HashSet<>(whitelistedPlayers);
    }
    
    public boolean isPlayerWhitelisted(String playerName) {
        return whitelistedPlayers.contains(playerName.toLowerCase());
    }
    
    @SuppressWarnings("unchecked")
    public void addPlayerToWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.add(lowerName);
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            Map<String, Object> whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = Collections.synchronizedMap(new java.util.HashMap<>());
            }
            
            Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
            if (players == null) {
                players = Collections.synchronizedMap(new java.util.HashMap<>());
                whitelistConfig.put("players", players);
            }
            
            players.put(lowerName, true);
            saveWhitelistConfig(whitelistConfig);
        } catch (Exception e) {
        }
    }
    
    @SuppressWarnings("unchecked")
    public void removePlayerFromWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.remove(lowerName);
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            Map<String, Object> whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = Collections.synchronizedMap(new java.util.HashMap<>());
            }
            
            Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
            if (players != null) {
                players.remove(lowerName);
                saveWhitelistConfig(whitelistConfig);
            }
        } catch (Exception e) {
        }
    }
}