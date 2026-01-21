package hanamuramiyu.pawkin.net.core;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

public class FileStorageManager {
    private final File whitelistFile;
    private final ExecutorService executor;
    private final ReentrantLock fileLock = new ReentrantLock();
    
    public FileStorageManager(File dataFolder, ExecutorService executor) {
        this.whitelistFile = new File(dataFolder, "whitelist.yml");
        this.executor = executor;
        createWhitelistConfig();
    }
    
    private void createWhitelistConfig() {
        if (!whitelistFile.exists()) {
            try {
                whitelistFile.getParentFile().mkdirs();
                whitelistFile.createNewFile();
                YamlConfiguration config = new YamlConfiguration();
                config.set("enabled", false);
                config.set("players", new java.util.HashMap<>());
                saveConfig(config);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create whitelist.yml", e);
            }
        }
    }
    
    public YamlConfiguration loadConfig() {
        fileLock.lock();
        try {
            return YamlConfiguration.loadConfiguration(whitelistFile);
        } finally {
            fileLock.unlock();
        }
    }
    
    public CompletableFuture<Void> saveConfigAsync(YamlConfiguration config) {
        return CompletableFuture.runAsync(() -> saveConfig(config), executor);
    }
    
    public void saveConfig(YamlConfiguration config) {
        fileLock.lock();
        try {
            config.save(whitelistFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save whitelist.yml", e);
        } finally {
            fileLock.unlock();
        }
    }
    
    public File getWhitelistFile() {
        return whitelistFile;
    }
}