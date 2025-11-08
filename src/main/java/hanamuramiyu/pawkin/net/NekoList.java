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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NekoList extends JavaPlugin implements Listener {
    
    private FileConfiguration config;
    private FileConfiguration languageConfig;
    private File whitelistFile;
    private File languageFile;
    private DiscordBot discordBot;
    private NekoListBase nekoListBase;
    
    @Override
    public void onEnable() {
        getLogger().info("NekoList starting on Bukkit platform");
        
        saveDefaultConfig();
        config = getConfig();
        
        createLangFiles();
        createWhitelistConfig();
        
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
        
        nekoListBase = new NekoListBase(this, getSLF4JLogger(), true);
        
        Map<String, Object> configMap = new HashMap<>();
        for (String key : config.getKeys(true)) {
            configMap.put(key, config.get(key));
        }
        nekoListBase.setConfig(configMap);
        
        Map<String, Object> langMap = new HashMap<>();
        for (String key : languageConfig.getKeys(true)) {
            langMap.put(key, languageConfig.get(key));
        }
        nekoListBase.setLanguageConfig(langMap);
        
        boolean discordEnabled = config.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled in config: " + discordEnabled);
        if (discordEnabled) {
            discordBot = new DiscordBot(nekoListBase);
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
    
    public void reloadNekoListConfig() {
        reloadConfig();
        config = getConfig();
        loadLanguageFile();
        
        Map<String, Object> configMap = new HashMap<>();
        for (String key : config.getKeys(true)) {
            configMap.put(key, config.get(key));
        }
        nekoListBase.setConfig(configMap);
        
        Map<String, Object> langMap = new HashMap<>();
        for (String key : languageConfig.getKeys(true)) {
            langMap.put(key, languageConfig.get(key));
        }
        nekoListBase.setLanguageConfig(langMap);
        
        if (discordBot != null) {
            discordBot.stopBot();
            discordBot = null;
        }
        
        boolean discordEnabled = config.getBoolean("discord-bot.enabled", false);
        getLogger().info("Discord bot enabled after reload: " + discordEnabled);
        if (discordEnabled) {
            discordBot = new DiscordBot(nekoListBase);
            discordBot.startBot();
        }
        
        getLogger().info("Reloaded configuration with language: " + config.getString("language"));
    }
    
    public boolean isWhitelistEnabled() {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        return whitelistConfig.getBoolean("enabled", false);
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("enabled", enabled);
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml");
        }
    }
    
    public Set<String> getWhitelistedPlayers() {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        if (whitelistConfig.getConfigurationSection("players") == null) {
            whitelistConfig.createSection("players");
            try {
                whitelistConfig.save(whitelistFile);
            } catch (Exception e) {
                getLogger().severe("Could not save whitelist.yml");
            }
        }
        return whitelistConfig.getConfigurationSection("players").getKeys(false);
    }
    
    public boolean isPlayerWhitelisted(String playerName) {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        return whitelistConfig.contains("players." + playerName.toLowerCase());
    }
    
    public void addPlayerToWhitelist(String playerName) {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("players." + playerName.toLowerCase(), true);
        try {
            whitelistConfig.save(whitelistFile);
        } catch (Exception e) {
            getLogger().severe("Could not save whitelist.yml");
        }
    }
    
    public void removePlayerFromWhitelist(String playerName) {
        FileConfiguration whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
        whitelistConfig.set("players." + playerName.toLowerCase(), null);
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
        if (!isWhitelistEnabled()) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("nekolist.bypass")) return;
        
        if (!isPlayerWhitelisted(player.getName())) {
            String message = getMessage("not-whitelisted");
            event.setResult(PlayerLoginEvent.Result.KICK_WHITELIST);
            event.setKickMessage(message);
        }
    }
}