package hanamuramiyu.pawkin.net.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.pawkin.net.discord.DiscordBot;
import hanamuramiyu.pawkin.net.velocity.command.VelocityNekoListCommand;
import hanamuramiyu.pawkin.net.velocity.listener.VelocityPlayerListener;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Plugin(
    id = "nekolist",
    name = "NekoList",
    version = "1.2.0",
    description = "Advanced whitelist system",
    authors = {"Hanamura Miyu"}
)
public class VelocityNekoList {
    
    private final ProxyServer server;
    private final Logger logger;
    private Map<String, Object> config;
    private Map<String, Object> languageConfig;
    private DiscordBot discordBot;
    private WhitelistManager whitelistManager;
    private File dataFolder;
    private VelocityPluginWrapper pluginWrapper;
    
    @Inject
    public VelocityNekoList(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("NekoList starting on Velocity platform");
        
        this.dataFolder = new File("plugins/NekoList");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        boolean onlineMode = server.getConfiguration().isOnlineMode();
        logger.info("Server online-mode: " + onlineMode);
        
        saveDefaultConfig(dataFolder);
        loadConfig(dataFolder);
        createLangFiles(dataFolder);
        createWhitelistConfig(dataFolder);
        
        if (!validateLanguageFile(dataFolder)) {
            logger.error("Language file validation failed!");
            return;
        }
        
        loadLanguageFile(dataFolder);
        
        this.whitelistManager = new WhitelistManager(dataFolder, onlineMode);
        this.pluginWrapper = new VelocityPluginWrapper(dataFolder, config, languageConfig, whitelistManager, logger, onlineMode);
        
        VelocityNekoListCommand commandHandler = new VelocityNekoListCommand(pluginWrapper, whitelistManager);
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("whitelist")
                .aliases("wl")
                .plugin(this)
                .build(),
            commandHandler
        );
        
        server.getEventManager().register(this, new VelocityPlayerListener(pluginWrapper, whitelistManager));
        
        boolean discordEnabled = getDiscordEnabled();
        if (discordEnabled) {
            try {
                Map<String, Object> flatConfig = createFlatConfig(config);
                discordBot = new DiscordBot(pluginWrapper, flatConfig);
                boolean started = discordBot.startBot();
                if (!started) {
                    logger.warn("Failed to start Discord bot on first attempt, retrying...");
                    started = discordBot.startBot();
                    if (!started) {
                        logger.error("Failed to start Discord bot after second attempt. Check your bot token and configuration.");
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to start Discord bot: " + e.getMessage());
            }
        } else {
            logger.info("Discord bot is disabled in config");
        }
        
        logger.info("NekoList enabled with language: " + config.get("language"));
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (discordBot != null) {
            discordBot.stopBot();
        }
    }
    
    public void reloadNekoListConfig() {
        logger.info("Reloading NekoList configuration...");
        
        loadConfig(dataFolder);
        loadLanguageFile(dataFolder);
        whitelistManager.loadWhitelist();
        
        if (discordBot != null) {
            discordBot.stopBot();
            discordBot = null;
        }
        
        boolean discordEnabled = getDiscordEnabled();
        if (discordEnabled) {
            try {
                Map<String, Object> flatConfig = createFlatConfig(config);
                boolean onlineMode = server.getConfiguration().isOnlineMode();
                pluginWrapper = new VelocityPluginWrapper(dataFolder, config, languageConfig, whitelistManager, logger, onlineMode);
                discordBot = new DiscordBot(pluginWrapper, flatConfig);
                boolean started = discordBot.startBot();
                if (!started) {
                    logger.warn("Failed to start Discord bot on first attempt after reload, retrying...");
                    started = discordBot.startBot();
                    if (!started) {
                        logger.error("Failed to start Discord bot after second attempt. Check your bot token and configuration.");
                    }
                }
                logger.info("Discord bot reloaded successfully for language: " + config.get("language"));
            } catch (Exception e) {
                logger.error("Failed to start Discord bot after reload: " + e.getMessage());
            }
        }
        
        logger.info("Reloaded configuration with language: " + config.get("language"));
    }
    
    public String getMessage(String path) {
        return pluginWrapper.getMessage(path);
    }
    
    private Map<String, Object> createFlatConfig(Map<String, Object> nestedConfig) {
        Map<String, Object> flat = new HashMap<>();
        flattenConfig(nestedConfig, flat, "");
        return flat;
    }
    
    @SuppressWarnings("unchecked")
    private void flattenConfig(Map<String, Object> source, Map<String, Object> target, String prefix) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenConfig((Map<String, Object>) entry.getValue(), target, key);
            } else {
                target.put(key, entry.getValue());
            }
        }
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
    
    private void saveDefaultConfig(File dataFolder) {
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
    private void loadConfig(File dataFolder) {
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
    
    private void createLangFiles(File dataFolder) {
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
    
    private boolean validateLanguageFile(File dataFolder) {
        String language = (String) config.getOrDefault("language", "en-US");
        File langFile = new File(dataFolder, "lang/" + language + ".yml");
        return langFile.exists();
    }
    
    private void createWhitelistConfig(File dataFolder) {
        File whitelistFile = new File(dataFolder, "whitelist.yml");
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
    }
    
    @SuppressWarnings("unchecked")
    private void loadLanguageFile(File dataFolder) {
        String language = (String) config.getOrDefault("language", "en-US");
        File languageFile = new File(dataFolder, "lang/" + language + ".yml");
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
    
    public Map<String, Object> getLanguageConfig() {
        return languageConfig;
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public ProxyServer getServer() {
        return server;
    }
    
    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
}