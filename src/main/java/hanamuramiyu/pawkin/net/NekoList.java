package hanamuramiyu.pawkin.net;

import hanamuramiyu.pawkin.net.command.NekoListCommand;
import hanamuramiyu.pawkin.net.discord.DiscordBot;
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

public class NekoList extends JavaPlugin implements Listener {
    
    private FileConfiguration config;
    private FileConfiguration languageConfig;
    private File whitelistFile;
    private File languageFile;
    private DiscordBot discordBot;
    private boolean whitelistEnabled;
    private Set<String> whitelistedPlayers;
    
    @Override
    public void onEnable() {
        getLogger().info("NekoList starting on Bukkit platform");
        
        saveDefaultConfig();
        config = getConfig();
        
        createLangFiles();
        createWhitelistConfig();
        loadWhitelist();
        
        if (!validateLanguageFile()) {
            getLogger().severe("================================================");
            getLogger().severe("LANGUAGE FILE VALIDATION FAILED!");
            getLogger().severe("Configured language: " + config.getString("language", "en-US"));
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
        
        boolean discordEnabled = config.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled in config: " + discordEnabled);
        if (discordEnabled) {
            Map<String, Object> configMap = new HashMap<>();
            for (String key : config.getKeys(true)) {
                configMap.put(key, config.get(key));
            }
            
            discordBot = new DiscordBot(this, configMap);
            discordBot.startBot();
        } else {
            getLogger().info("Discord bot is disabled in config");
        }
        
        getLogger().info("NekoList enabled with language: " + config.getString("language"));
    }
    
    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stopBot();
        }
    }
    
    public void reloadNekoListConfig() {
        reloadConfig();
        config = getConfig();
        loadLanguageFile();
        loadWhitelist();
        
        if (discordBot != null) {
            discordBot.stopBot();
            discordBot = null;
        }
        
        boolean discordEnabled = config.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled after reload: " + discordEnabled);
        if (discordEnabled) {
            Map<String, Object> configMap = new HashMap<>();
            for (String key : config.getKeys(true)) {
                configMap.put(key, config.get(key));
            }
            
            discordBot = new DiscordBot(this, configMap);
            discordBot.startBot();
            getLogger().info("Discord bot reloaded successfully for language: " + config.getString("language"));
        }
        
        getLogger().info("Reloaded configuration with language: " + config.getString("language"));
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
        String language = config.getString("language", "en-US");
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
        String language = config.getString("language", "en-US");
        languageFile = new File(getDataFolder(), "lang/" + language + ".yml");
        if (!languageFile.exists()) {
            getLogger().severe("Language file not found: " + languageFile.getPath());
            return;
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        getLogger().info("Loaded language file: " + language + ".yml");
    }
    
    private void loadWhitelist() {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistEnabled = whitelistConfig.getBoolean("enabled", false);
        whitelistedPlayers = new HashSet<>();
        
        if (whitelistConfig.getConfigurationSection("players") != null) {
            whitelistedPlayers.addAll(whitelistConfig.getConfigurationSection("players").getKeys(false));
        }
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("enabled", enabled);
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml");
        }
    }
    
    public Set<String> getWhitelistedPlayers() {
        return new HashSet<>(whitelistedPlayers);
    }
    
    public boolean isPlayerWhitelisted(String playerName) {
        return whitelistedPlayers.contains(playerName.toLowerCase());
    }
    
    public void addPlayerToWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.add(lowerName);
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("players." + lowerName, true);
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml");
        }
    }
    
    public void removePlayerFromWhitelist(String playerName) {
        String lowerName = playerName.toLowerCase();
        whitelistedPlayers.remove(lowerName);
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("players." + lowerName, null);
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml");
        }
    }
    
    public String getMessage(String path) {
        if (languageConfig == null) {
            getLogger().severe("Language config is NULL! Path: " + path);
            return "ERROR: Language config not loaded";
        }
        
        String message = languageConfig.getString(path);
        if (message == null) {
            getLogger().warning("Message not found: " + path + " in language file " + config.getString("language"));
            return "Message not found: " + path;
        }
        return message.replace('&', '§');
    }
    
    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!whitelistEnabled) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        if (!isPlayerWhitelisted(player.getName())) {
            String message = getMessage("not-whitelisted");
            event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
            event.setKickMessage(message);
        }
    }
}