package hanamuramiyu.pawkin.net.core;

import hanamuramiyu.pawkin.net.command.WhitelistCommand;
import hanamuramiyu.pawkin.net.database.DatabaseManager;
import hanamuramiyu.pawkin.net.discord.DiscordBot;
import hanamuramiyu.pawkin.net.listeners.PlayerLoginListener;
import hanamuramiyu.pawkin.net.scheduler.SchedulerManager;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NekoList extends JavaPlugin {
    
    private ConfigManager configManager;
    private FileStorageManager fileStorageManager;
    private WhitelistCache whitelistCache;
    private DiscordBot discordBot;
    private boolean onlineMode;
    private DatabaseManager databaseManager;
    private ExecutorService asyncExecutor;
    private final Map<String, Component> messageCache = new ConcurrentHashMap<>();
    private boolean pluginEnabled = false;
    private org.bstats.bukkit.Metrics metrics;
    private SchedulerManager schedulerManager;
    private final AtomicInteger threadCounter = new AtomicInteger(1);
    
    @Override
    public void onEnable() {
        getLogger().info("NekoList " + getDescription().getVersion() + " starting");
        
        PaperLib.suggestPaper(this);
        this.schedulerManager = new SchedulerManager(this);
        this.onlineMode = Bukkit.getServer().getOnlineMode();
        
        if (schedulerManager.isFolia()) {
            getLogger().info("Detected Folia server - using Folia schedulers");
            this.asyncExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread thread = new Thread(r, "NekoList-FoliaAsync-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
        } else {
            getLogger().info("Detected Bukkit/Paper server - using Bukkit schedulers");
            this.asyncExecutor = Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "NekoList-BukkitAsync-" + threadCounter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
        }
        
        this.configManager = new ConfigManager(this);
        if (!configManager.validateConfig()) {
            getLogger().severe("Configuration validation failed! Disabling plugin.");
            schedulerManager.runTask(() -> getServer().getPluginManager().disablePlugin(this));
            return;
        }
        
        configManager.loadLanguageFile();
        
        this.fileStorageManager = new FileStorageManager(getDataFolder(), asyncExecutor);
        this.whitelistCache = new WhitelistCache(fileStorageManager);
        
        this.databaseManager = new DatabaseManager(configManager.getConfigMap(), getLogger(), getDataFolder().getPath());
        
        String databaseType = databaseManager.getType();
        
        if (databaseManager.isFileDatabase()) {
            getLogger().info("Using file database");
            whitelistCache.initialize();
        } else {
            getLogger().info("Using MySQL/MariaDB database");
            if (!databaseManager.testConnection()) {
                getLogger().warning("Cannot connect to MySQL/MariaDB, switching to file database");
                databaseManager.switchToFileDatabase();
                logDatabaseSwitch();
                broadcastDatabaseSwitch();
            } else {
                try {
                    databaseManager.initializeTable();
                    getLogger().info("Connected to MySQL/MariaDB database");
                } catch (Exception e) {
                    getLogger().warning("Failed to initialize database table, switching to file database");
                    databaseManager.switchToFileDatabase();
                    logDatabaseSwitch();
                    broadcastDatabaseSwitch();
                }
            }
        }
        
        getCommand("whitelist").setExecutor(new WhitelistCommand(this));
        getCommand("whitelist").setTabCompleter(new WhitelistCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), this);
        
        metrics = new org.bstats.bukkit.Metrics(this, 28898);
        
        boolean discordEnabled = configManager.getBukkitConfig().getBoolean("discord-bot.enabled", false);
        if (discordEnabled) {
            CompletableFuture.runAsync(() -> {
                discordBot = new DiscordBot(this, configManager.getConfigMap());
                boolean started = discordBot.startBot();
                if (started) {
                    getLogger().info("Discord bot started successfully");
                }
            }, asyncExecutor);
        }
        
        UpdateChecker.checkForUpdates(this, asyncExecutor);
        
        pluginEnabled = true;
    }
    
    @Override
    public void onDisable() {
        pluginEnabled = false;
        if (discordBot != null) {
            discordBot.stopBot();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        if (schedulerManager != null) {
            schedulerManager.cancelAllTasks();
        }
        if (whitelistCache != null) {
            whitelistCache.clear();
        }
        messageCache.clear();
        
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                    if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        getLogger().severe("Async executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void reloadNekoListConfig() {
        if (!pluginEnabled) return;
        
        schedulerManager.runTaskAsync(() -> {
            configManager.reload();
            messageCache.clear();
            
            String oldDbType = databaseManager.getType();
            boolean oldFileDb = databaseManager.isFileDatabase();
            
            databaseManager.updateConfig(configManager.getConfigMap());
            
            String newDbType = databaseManager.getType();
            boolean newFileDb = databaseManager.isFileDatabase();
            
            if (!newFileDb) {
                if (!databaseManager.testConnection()) {
                    databaseManager.switchToFileDatabase();
                    logDatabaseSwitch();
                    broadcastDatabaseSwitch();
                } else {
                    try {
                        databaseManager.initializeTable();
                    } catch (Exception e) {
                        getLogger().warning("Failed to initialize database table, switching to file database");
                        databaseManager.switchToFileDatabase();
                        logDatabaseSwitch();
                        broadcastDatabaseSwitch();
                    }
                }
            }
            
            if (discordBot != null) {
                discordBot.stopBot();
                discordBot = null;
            }
            
            boolean discordEnabled = configManager.getBukkitConfig().getBoolean("discord-bot.enabled", false);
            if (discordEnabled) {
                discordBot = new DiscordBot(this, configManager.getConfigMap());
                boolean started = discordBot.startBot();
                if (started) {
                    getLogger().info("Discord bot started successfully");
                }
            }
            
            schedulerManager.runTask(() -> getLogger().info("Configuration reloaded"));
        });
    }
    
    private void logDatabaseSwitch() {
        getLogger().warning("========================================");
        getLogger().warning("Switched to file database due to MySQL/MariaDB connection error");
        getLogger().warning("Check your database configuration in config.yml");
        getLogger().warning("========================================");
    }
    
    private void broadcastDatabaseSwitch() {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("nekolist.admin"))
            .forEach(p -> p.sendMessage(getMiniMessage("errors.database-switch")));
    }
    
    public boolean isWhitelistEnabled() {
        if (databaseManager.isFileDatabase()) {
            return whitelistCache.isWhitelistEnabled();
        } else {
            try {
                return databaseManager.getWhitelistEnabled();
            } catch (Exception e) {
                getLogger().warning("Failed to get whitelist status from database: " + e.getMessage());
                return false;
            }
        }
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        if (databaseManager.isFileDatabase()) {
            whitelistCache.setWhitelistEnabled(enabled);
        } else {
            schedulerManager.runTaskAsync(() -> {
                try {
                    databaseManager.updateWhitelistEnabled(enabled);
                } catch (Exception e) {
                    getLogger().warning("Failed to update whitelist status in database: " + e.getMessage());
                }
            });
        }
    }
    
    public Set<String> getWhitelistedPlayers() {
        if (databaseManager.isFileDatabase()) {
            return whitelistCache.getWhitelistedPlayers();
        } else {
            try {
                return databaseManager.getAllPlayers();
            } catch (Exception e) {
                getLogger().warning("Failed to get whitelisted players from database: " + e.getMessage());
                return Set.of();
            }
        }
    }
    
    public boolean isPlayerWhitelisted(String playerName) {
        return isPlayerWhitelistedInternal(playerName, null);
    }
    
    public boolean isPlayerWhitelisted(UUID playerUUID) {
        return isPlayerWhitelistedInternal(null, playerUUID);
    }
    
    private boolean isPlayerWhitelistedInternal(String playerName, UUID playerUUID) {
        if (databaseManager.isFileDatabase()) {
            return whitelistCache.isPlayerWhitelisted(playerName, playerUUID);
        } else {
            try {
                if (playerName != null) {
                    return databaseManager.isPlayerWhitelisted(playerName);
                } else {
                    return databaseManager.isPlayerWhitelisted(playerUUID);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to check if player is whitelisted in database: " + e.getMessage());
                return false;
            }
        }
    }
    
    public void addPlayerToWhitelist(String playerName) {
        if (!isValidPlayerName(playerName)) {
            getLogger().warning("Invalid player name: " + playerName);
            return;
        }
        if (onlineMode) {
            schedulerManager.runTaskAsync(() -> {
                UUID uuid = MojangApiClient.fetchUUIDFromMojang(playerName, getLogger());
                if (uuid != null) {
                    addPlayerToWhitelistInternal(playerName, uuid);
                } else {
                    addPlayerToWhitelistInternal(playerName, null);
                }
            });
        } else {
            addPlayerToWhitelistInternal(playerName, null);
        }
    }
    
    public void addPlayerToWhitelist(String playerName, UUID uuid) {
        addPlayerToWhitelistInternal(playerName, uuid);
    }
    
    private void addPlayerToWhitelistInternal(String playerName, UUID uuid) {
        if (!isValidPlayerName(playerName)) {
            return;
        }
        if (databaseManager.isFileDatabase()) {
            whitelistCache.addPlayer(playerName, uuid);
        } else {
            schedulerManager.runTaskAsync(() -> {
                try {
                    databaseManager.addPlayer(playerName, uuid);
                } catch (Exception e) {
                    getLogger().warning("Failed to add player to database: " + e.getMessage());
                }
            });
        }
    }
    
    public void removePlayerFromWhitelist(String playerName) {
        if (databaseManager.isFileDatabase()) {
            whitelistCache.removePlayer(playerName);
        } else {
            schedulerManager.runTaskAsync(() -> {
                try {
                    databaseManager.removePlayer(playerName);
                } catch (Exception e) {
                    getLogger().warning("Failed to remove player from database: " + e.getMessage());
                }
            });
        }
    }
    
    public void updatePlayerData(String playerName, UUID uuid) {
        if (!databaseManager.isFileDatabase()) {
            schedulerManager.runTaskAsync(() -> {
                try {
                    databaseManager.updatePlayerUUID(playerName, uuid);
                } catch (Exception e) {
                    getLogger().warning("Failed to update player data in database: " + e.getMessage());
                }
            });
        }
    }
    
    public String getMessage(String path) {
        return configManager.getMessage(path);
    }
    
    public Component getMiniMessage(String path) {
        Component cached = messageCache.get(path);
        if (cached != null) {
            return cached;
        }
        
        Component result = configManager.getMiniMessage(path);
        messageCache.put(path, result);
        return result;
    }
    
    public Map<String, Object> getPluginConfig() {
        return configManager.getConfigMap();
    }
    
    public DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    public boolean isOnlineMode() {
        return onlineMode;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public SchedulerManager getSchedulerManager() {
        return schedulerManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_]{1,16}$");
    }
}