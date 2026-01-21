package hanamuramiyu.pawkin.net.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration bukkitConfig;
    private FileConfiguration languageConfig;
    private File languageFile;
    private Map<String, Object> configMap;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        bukkitConfig = plugin.getConfig();
        loadConfigMap();
        createLangFiles();
        loadLanguageFile();
    }
    
    public void reload() {
        plugin.reloadConfig();
        bukkitConfig = plugin.getConfig();
        loadConfigMap();
        loadLanguageFile();
    }
    
    public boolean validateConfig() {
        if (!bukkitConfig.isConfigurationSection("database")) {
            plugin.getLogger().severe("Missing 'database:' section in config.yml");
            return false;
        }
        
        if (!bukkitConfig.isConfigurationSection("discord-bot")) {
            plugin.getLogger().severe("Missing 'discord-bot:' section in config.yml");
            return false;
        }
        
        String dbType = bukkitConfig.getString("database.type");
        if (!"file".equalsIgnoreCase(dbType) && !"mysql".equalsIgnoreCase(dbType)) {
            plugin.getLogger().severe("Invalid database type in config.yml. Must be 'file' or 'mysql'");
            return false;
        }
        
        return true;
    }
    
    private void loadConfigMap() {
        configMap = new HashMap<>();
        for (String key : bukkitConfig.getKeys(true)) {
            configMap.put(key, bukkitConfig.get(key));
        }
    }
    
    private void createLangFiles() {
        String language = bukkitConfig.getString("language", "en-US");
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        String[] languages = {"en-US", "en-GB", "es-ES", "es-419", "ja-JP", "ru-RU", "uk-UA", "zh-CN", "zh-TW"};
        for (String lang : languages) {
            File langFile = new File(langDir, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to copy language file for " + lang);
                }
            }
        }
    }
    
    public void loadLanguageFile() {
        String language = bukkitConfig.getString("language", "en-US");
        languageFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        
        if (!languageFile.exists()) {
            File fallbackFile = new File(plugin.getDataFolder(), "lang/en-US.yml");
            if (fallbackFile.exists()) {
                languageFile = fallbackFile;
                plugin.getLogger().warning("Language file " + language + ".yml not found, falling back to en-US");
            } else {
                plugin.getLogger().severe("No language files found!");
                return;
            }
        }
        
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }
    
    public String getMessage(String path) {
        return languageConfig.getString(path, "Message not found: " + path);
    }
    
    public Component getMiniMessage(String path) {
        String message = getMessage(path);
        return MiniMessage.miniMessage().deserialize(message);
    }
    
    public FileConfiguration getBukkitConfig() {
        return bukkitConfig;
    }
    
    public Map<String, Object> getConfigMap() {
        return configMap;
    }
}