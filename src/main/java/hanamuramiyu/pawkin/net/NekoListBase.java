package hanamuramiyu.pawkin.net;

import hanamuramiyu.pawkin.net.discord.DiscordBot;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class NekoListBase {
    
    private Map<String, Object> config;
    private Map<String, Object> whitelistConfig;
    private Map<String, Object> languageConfig;
    private File whitelistFile;
    private File languageFile;
    private DiscordBot discordBot;
    private Object server;
    private Logger logger;
    private File dataFolder;
    private boolean isBukkit;
    
    public NekoListBase() {
    }
    
    public NekoListBase(Object server, Logger logger, boolean isBukkit) {
        this.server = server;
        this.logger = logger;
        this.isBukkit = isBukkit;
        
        if (isBukkit) {
            try {
                Class<?> javaPluginClass = Class.forName("org.bukkit.plugin.java.JavaPlugin");
                if (javaPluginClass.isInstance(server)) {
                    Object dataFolderMethod = javaPluginClass.getMethod("getDataFolder").invoke(server);
                    this.dataFolder = (File) dataFolderMethod;
                }
            } catch (Exception e) {
                this.dataFolder = new File("plugins/NekoList");
            }
        } else {
            this.dataFolder = new File("plugins/NekoList");
        }
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    public void setServer(Object server) {
        this.server = server;
    }
    
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    public void setBukkit(boolean isBukkit) {
        this.isBukkit = isBukkit;
    }
    
    public void setDataFolder(File dataFolder) {
        this.dataFolder = dataFolder;
    }
    
    public void onEnable() {
        if (isBukkit) {
            logger.info("NekoList starting on Bukkit platform");
        } else {
            logger.info("NekoList starting on Velocity platform");
        }
        
        saveDefaultConfig();
        loadConfig();
        
        createLangFiles();
        createWhitelistConfig();
        
        if (!validateLanguageFile()) {
            logger.error("Language file validation failed!");
            return;
        }
        
        loadLanguageFile();
        
        boolean discordEnabled = getDiscordEnabled();
        
        if (discordEnabled) {
            try {
                discordBot = new DiscordBot(this);
                discordBot.startBot();
            } catch (Exception e) {
                logger.error("Failed to start Discord bot: " + e.getMessage());
            }
        } else {
            logger.info("Discord bot is disabled in config");
        }
        
        logger.info("NekoList enabled with language: " + config.get("language"));
    }
    
    @SuppressWarnings("unchecked")
    private boolean getDiscordEnabled() {
        if (config == null) {
            return false;
        }
        
        try {
            Object discordBotObj = config.get("discord-bot");
            if (discordBotObj instanceof Map) {
                Map<String, Object> discordConfig = (Map<String, Object>) discordBotObj;
                Object enabledObj = discordConfig.get("enabled");
                if (enabledObj instanceof Boolean) {
                    return (Boolean) enabledObj;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Error reading discord-bot.enabled: " + e.getMessage());
            return false;
        }
    }
    
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stopBot();
        }
    }
    
    private void saveDefaultConfig() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Created default config.yml");
                }
            } catch (Exception e) {
                logger.warn("Could not create config.yml");
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadConfig() {
        File configFile = new File(dataFolder, "config.yml");
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(configFile)) {
            config = (Map<String, Object>) yaml.load(reader);
            if (config == null) {
                config = new HashMap<>();
            }
        } catch (Exception e) {
            logger.error("Could not load config.yml");
            config = new HashMap<>();
        }
    }
    
    private void createLangFiles() {
        String[] languages = {"en-US", "en-GB", "es-ES", "es-MX", "es-AR", "es-CL", "es-CO", "es-PE", "ja-JP", "ru-RU", "uk-UA", "zh-CN", "zh-TW"};
        File langDir = new File(dataFolder, "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        for (String lang : languages) {
            File langFile = new File(langDir, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = getClass().getResourceAsStream("/lang/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Created language file: " + lang + ".yml");
                    }
                } catch (Exception e) {
                    logger.warn("Could not create language file: " + lang + ".yml");
                }
            }
        }
    }
    
    private boolean validateLanguageFile() {
        String language = (String) config.getOrDefault("language", "en-US");
        File langFile = new File(dataFolder, "lang/" + language + ".yml");
        return langFile.exists();
    }
    
    @SuppressWarnings("unchecked")
    private void createWhitelistConfig() {
        whitelistFile = new File(dataFolder, "whitelist.yml");
        if (!whitelistFile.exists()) {
            try (InputStream in = getClass().getResourceAsStream("/whitelist.yml")) {
                if (in != null) {
                    Files.copy(in, whitelistFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Created whitelist.yml");
                }
            } catch (Exception e) {
                logger.warn("Could not create whitelist.yml");
            }
        }
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(whitelistFile)) {
            whitelistConfig = (Map<String, Object>) yaml.load(reader);
            if (whitelistConfig == null) {
                whitelistConfig = new HashMap<>();
                whitelistConfig.put("enabled", false);
                whitelistConfig.put("players", new HashMap<>());
                saveWhitelistConfig();
            }
        } catch (Exception e) {
            logger.error("Could not load whitelist.yml");
            whitelistConfig = new HashMap<>();
            whitelistConfig.put("enabled", false);
            whitelistConfig.put("players", new HashMap<>());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadLanguageFile() {
        String language = (String) config.getOrDefault("language", "en-US");
        languageFile = new File(dataFolder, "lang/" + language + ".yml");
        if (!languageFile.exists()) {
            logger.error("Language file not found: " + languageFile.getPath());
            return;
        }
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(languageFile)) {
            languageConfig = (Map<String, Object>) yaml.load(reader);
            if (languageConfig == null) {
                languageConfig = new HashMap<>();
            }
        } catch (Exception e) {
            logger.error("Could not load language file: " + language);
            languageConfig = new HashMap<>();
        }
        logger.info("Loaded language file: " + language + ".yml");
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
    
    public void setLanguageConfig(Map<String, Object> languageConfig) {
        this.languageConfig = languageConfig;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
    
    @SuppressWarnings("unchecked")
    public void saveWhitelistConfig() {
        try (FileWriter writer = new FileWriter(whitelistFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(whitelistConfig, writer);
        } catch (Exception e) {
            logger.error("Could not save whitelist.yml");
        }
    }
    
    public void reloadNekoListConfig() {
        loadConfig();
        createWhitelistConfig();
        loadLanguageFile();
        
        if (discordBot != null) {
            discordBot.stopBot();
            discordBot = null;
        }
        
        boolean discordEnabled = getDiscordEnabled();
        if (discordEnabled) {
            try {
                discordBot = new DiscordBot(this);
                discordBot.startBot();
                logger.info("Discord bot reloaded successfully");
            } catch (Exception e) {
                logger.error("Failed to start Discord bot after reload: " + e.getMessage());
            }
        }
        
        logger.info("Reloaded configuration with language: " + config.get("language"));
    }
    
    public boolean isWhitelistEnabled() {
        if (whitelistConfig == null) {
            return false;
        }
        return (Boolean) whitelistConfig.getOrDefault("enabled", false);
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        if (whitelistConfig == null) {
            createWhitelistConfig();
        }
        whitelistConfig.put("enabled", enabled);
        saveWhitelistConfig();
    }
    
    @SuppressWarnings("unchecked")
    public Set<String> getWhitelistedPlayers() {
        if (whitelistConfig == null) {
            createWhitelistConfig();
        }
        Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
        if (players == null) {
            players = new HashMap<>();
            whitelistConfig.put("players", players);
            saveWhitelistConfig();
        }
        return players.keySet();
    }
    
    @SuppressWarnings("unchecked")
    public boolean isPlayerWhitelisted(String playerName) {
        if (whitelistConfig == null) {
            createWhitelistConfig();
        }
        Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
        return players != null && players.containsKey(playerName.toLowerCase());
    }
    
    @SuppressWarnings("unchecked")
    public void addPlayerToWhitelist(String playerName) {
        if (whitelistConfig == null) {
            createWhitelistConfig();
        }
        Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
        if (players == null) {
            players = new HashMap<>();
            whitelistConfig.put("players", players);
        }
        players.put(playerName.toLowerCase(), true);
        saveWhitelistConfig();
        logger.info("Added player to whitelist: " + playerName);
    }
    
    @SuppressWarnings("unchecked")
    public void removePlayerFromWhitelist(String playerName) {
        if (whitelistConfig == null) {
            createWhitelistConfig();
        }
        Map<String, Object> players = (Map<String, Object>) whitelistConfig.get("players");
        if (players != null) {
            players.remove(playerName.toLowerCase());
            saveWhitelistConfig();
            logger.info("Removed player from whitelist: " + playerName);
        }
    }
    
    @SuppressWarnings("unchecked")
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
    
    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    public boolean isBukkit() {
        return isBukkit;
    }
}