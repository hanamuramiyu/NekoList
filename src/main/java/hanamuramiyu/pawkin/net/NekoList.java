package hanamuramiyu.pawkin.net;

import hanamuramiyu.pawkin.net.command.NekoListCommand;
import hanamuramiyu.pawkin.net.discord.DiscordBot;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NekoList extends JavaPlugin implements Listener, NekoListBase {
    
    private FileConfiguration bukkitConfig;
    private FileConfiguration languageConfig;
    private File whitelistFile;
    private File languageFile;
    private DiscordBot discordBot;
    private boolean whitelistEnabled;
    private Map<String, PlayerData> whitelistedPlayers;
    private boolean onlineMode;
    private Map<String, Object> configMap;
    
    @Override
    public void onEnable() {
        getLogger().info("NekoList starting on Bukkit platform");
        
        this.onlineMode = Bukkit.getServer().getOnlineMode();
        getLogger().info("Server online-mode: " + onlineMode);
        
        saveDefaultConfig();
        bukkitConfig = super.getConfig();
        loadConfigMap();
        
        createLangFiles();
        createWhitelistConfig();
        loadWhitelist();
        
        if (!validateLanguageFile()) {
            getLogger().severe("================================================");
            getLogger().severe("LANGUAGE FILE VALIDATION FAILED!");
            getLogger().severe("Configured language: " + bukkitConfig.getString("language", "en-US"));
            getLogger().severe("The specified language file does not exist in the lang folder.");
            getLogger().severe("Available languages: en-US, en-GB, es-ES, es-MX, es-AR, es-CL, es-CO, es-PE, ja-JP, ru-RU, uk-UA, zh-CN, zh-TW");
            getLogger().severe("Please check your config.yml and ensure the language file exists.");
            getLogger().severe("Plugin will now disable.");
            getLogger().severe("================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        loadLanguageFile();
        
        getCommand("whitelist").setExecutor(new NekoListCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        
        boolean discordEnabled = bukkitConfig.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled in config: " + discordEnabled);
        if (discordEnabled) {
            discordBot = new DiscordBot(this, configMap);
            boolean started = discordBot.startBot();
            if (!started) {
                getLogger().warning("Failed to start Discord bot on first attempt, retrying...");
                started = discordBot.startBot();
                if (!started) {
                    getLogger().severe("Failed to start Discord bot after second attempt. Check your bot token and configuration.");
                }
            }
        } else {
            getLogger().info("Discord bot is disabled in config");
        }
        
        getLogger().info("NekoList enabled with language: " + bukkitConfig.getString("language"));
    }
    
    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stopBot();
        }
    }
    
    public void reloadNekoListConfig() {
        reloadConfig();
        bukkitConfig = super.getConfig();
        loadConfigMap();
        loadLanguageFile();
        
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {}
        
        loadWhitelist();
        
        if (discordBot != null) {
            discordBot.stopBot();
            discordBot = null;
        }
        
        boolean discordEnabled = bukkitConfig.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled after reload: " + discordEnabled);
        if (discordEnabled) {
            discordBot = new DiscordBot(this, configMap);
            boolean started = discordBot.startBot();
            if (!started) {
                getLogger().warning("Failed to start Discord bot on first attempt after reload, retrying...");
                started = discordBot.startBot();
                if (!started) {
                    getLogger().severe("Failed to start Discord bot after second attempt. Check your bot token and configuration.");
                }
            }
            getLogger().info("Discord bot reloaded successfully for language: " + bukkitConfig.getString("language"));
        }
        
        getLogger().info("Reloaded configuration with language: " + bukkitConfig.getString("language"));
    }
    
    private void loadConfigMap() {
        configMap = new HashMap<>();
        for (String key : bukkitConfig.getKeys(true)) {
            configMap.put(key, bukkitConfig.get(key));
        }
    }
    
    private void createLangFiles() {
        String[] languages = {"en-US", "en-GB", "es-ES", "es-MX", "es-AR", "es-CL", "es-CO", "es-PE", "ja-JP", "ru-RU", "uk-UA", "zh-CN", "zh-TW"};
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        for (String lang : languages) {
            File langFile = new File(langDir, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = getResource("lang/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Created language file: " + lang + ".yml");
                    }
                } catch (Exception e) {
                    getLogger().warning("Could not create language file: " + lang + ".yml");
                }
            }
        }
    }
    
    private boolean validateLanguageFile() {
        String language = bukkitConfig.getString("language", "en-US");
        File langFile = new File(getDataFolder(), "lang/" + language + ".yml");
        return langFile.exists();
    }
    
    private void createWhitelistConfig() {
        whitelistFile = new File(getDataFolder(), "whitelist.yml");
        if (!whitelistFile.exists()) {
            try (InputStream in = getResource("whitelist.yml")) {
                if (in != null) {
                    Files.copy(in, whitelistFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("Created whitelist.yml");
                }
            } catch (Exception e) {
                getLogger().warning("Could not create whitelist.yml");
            }
        }
    }
    
    private void loadLanguageFile() {
        String language = bukkitConfig.getString("language", "en-US");
        languageFile = new File(getDataFolder(), "lang/" + language + ".yml");
        if (!languageFile.exists()) {
            getLogger().severe("Language file not found: " + languageFile.getPath());
            return;
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        getLogger().info("Loaded language file: " + language + ".yml");
    }
    
    @SuppressWarnings("unchecked")
    private synchronized void loadWhitelist() {
        YamlConfiguration whitelistConfig = new YamlConfiguration();
        try {
            whitelistConfig.load(whitelistFile);
            getLogger().info("Successfully loaded whitelist.yml");
        } catch (Exception e) {
            getLogger().severe("CRITICAL ERROR: Could not load whitelist.yml: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        whitelistEnabled = whitelistConfig.getBoolean("enabled", false);
        whitelistedPlayers = new HashMap<>();
        
        int migratedCount = 0;
        int noUUIDCount = 0;
        
        if (whitelistConfig.getConfigurationSection("players") != null) {
            for (String key : whitelistConfig.getConfigurationSection("players").getKeys(false)) {
                Object value = whitelistConfig.get("players." + key);
                
                if (value instanceof Boolean && (Boolean) value) {
                    whitelistedPlayers.put(key.toLowerCase(), new PlayerData(key, null));
                    migratedCount++;
                    if (onlineMode) {
                        noUUIDCount++;
                    }
                } else {
                    org.bukkit.configuration.ConfigurationSection playerSection = whitelistConfig.getConfigurationSection("players." + key);
                    if (playerSection != null) {
                        String name = playerSection.getString("name", key);
                        String uuidStr = playerSection.getString("uuid");
                        
                        UUID uuid = null;
                        if (uuidStr != null) {
                            try {
                                uuid = UUID.fromString(uuidStr);
                            } catch (IllegalArgumentException e) {
                                getLogger().warning("Invalid UUID format for player " + key + ": " + uuidStr);
                            }
                        }
                        
                        whitelistedPlayers.put(key.toLowerCase(), new PlayerData(name, uuid));
                        
                        if (onlineMode && uuid == null) {
                            noUUIDCount++;
                        }
                    }
                }
            }
        }
        
        if (migratedCount > 0) {
            getLogger().info("Migrated " + migratedCount + " legacy entries to new format");
            saveWhitelist();
        }
        
        if (noUUIDCount > 0 && onlineMode) {
            getLogger().warning("Found " + noUUIDCount + " entries without UUID in online-mode server");
        }
        
        getLogger().info("Loaded " + whitelistedPlayers.size() + " players: " + whitelistedPlayers.keySet());
    }
    
    private synchronized void saveWhitelist() {
        YamlConfiguration whitelistConfig = new YamlConfiguration();
        
        whitelistConfig.set("enabled", whitelistEnabled);
        
        for (PlayerData data : whitelistedPlayers.values()) {
            String key = data.getName().toLowerCase();
            Map<String, Object> playerMap = new HashMap<>();
            playerMap.put("name", data.getName());
            if (data.getUuid() != null) {
                playerMap.put("uuid", data.getUuid().toString());
            }
            whitelistConfig.set("players." + key, playerMap);
        }
        
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml: " + e.getMessage());
        }
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        saveWhitelist();
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
    
    public String getMessage(String path) {
        if (languageConfig == null) {
            getLogger().severe("Language config is NULL! Path: " + path);
            return "ERROR: Language config not loaded";
        }
        
        String message = languageConfig.getString(path);
        if (message == null) {
            getLogger().warning("Message not found: " + path + " in language file " + bukkitConfig.getString("language"));
            return "Message not found: " + path;
        }
        return message.replace('&', '§');
    }
    
    public Map<String, Object> getPluginConfig() {
        return configMap;
    }
    
    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    public boolean isOnlineMode() {
        return onlineMode;
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!whitelistEnabled) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        boolean allowed = false;
        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();
        
        if (isPlayerWhitelisted(playerName)) {
            allowed = true;
            updatePlayerData(playerName, playerUUID);
        } else if (onlineMode && isPlayerWhitelisted(playerUUID)) {
            allowed = true;
            updatePlayerData(playerName, playerUUID);
        }
        
        if (!allowed) {
            String message = getMessage("not-whitelisted");
            event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
            event.setKickMessage(message);
        }
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