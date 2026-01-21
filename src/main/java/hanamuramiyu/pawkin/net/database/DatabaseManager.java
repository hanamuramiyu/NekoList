package hanamuramiyu.pawkin.net.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private HikariDataSource dataSource;
    private String type;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String table;
    private boolean useSSL;
    private int timeout;
    private final Logger logger;
    private final String dataFolderPath;
    private boolean driverLoaded = false;
    private boolean canConnect = true;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final Map<String, ReentrantLock> tableLocks = new ConcurrentHashMap<>();
    
    public DatabaseManager(Map<String, Object> config, Logger logger, String dataFolderPath) {
        this.logger = logger;
        this.dataFolderPath = dataFolderPath;
        initConfig(config);
    }
    
    private void initConfig(Map<String, Object> config) {
        Object dbConfigObj = config.get("database");
        if (dbConfigObj == null) {
            dbConfigObj = new HashMap<String, Object>();
        }
        
        Map<String, Object> dbConfig;
        if (dbConfigObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) dbConfigObj;
            dbConfig = temp;
        } else if (dbConfigObj instanceof org.bukkit.configuration.ConfigurationSection) {
            dbConfig = ((org.bukkit.configuration.ConfigurationSection) dbConfigObj).getValues(true);
        } else if (dbConfigObj instanceof org.bukkit.configuration.MemorySection) {
            dbConfig = ((org.bukkit.configuration.MemorySection) dbConfigObj).getValues(true);
        } else {
            dbConfig = new HashMap<>();
        }
        
        this.type = (String) dbConfig.getOrDefault("type", "file");
        
        Object mysqlObj = dbConfig.get("mysql");
        Map<String, Object> mysqlConfig;
        if (mysqlObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) mysqlObj;
            mysqlConfig = temp;
        } else if (mysqlObj instanceof org.bukkit.configuration.ConfigurationSection) {
            mysqlConfig = ((org.bukkit.configuration.ConfigurationSection) mysqlObj).getValues(true);
        } else if (mysqlObj instanceof org.bukkit.configuration.MemorySection) {
            mysqlConfig = ((org.bukkit.configuration.MemorySection) mysqlObj).getValues(true);
        } else {
            mysqlConfig = new HashMap<>();
        }
        
        this.host = (String) mysqlConfig.getOrDefault("host", "localhost");
        this.port = ((Number) mysqlConfig.getOrDefault("port", 3306)).intValue();
        this.database = (String) mysqlConfig.getOrDefault("database", "minecraft");
        this.username = (String) mysqlConfig.getOrDefault("username", "username");
        this.password = (String) mysqlConfig.getOrDefault("password", "password");
        this.table = (String) mysqlConfig.getOrDefault("table", "whitelist");
        this.useSSL = (boolean) mysqlConfig.getOrDefault("use-ssl", false);
        this.timeout = ((Number) mysqlConfig.getOrDefault("connection-timeout", 30000)).intValue();
        
        if (!"file".equalsIgnoreCase(type)) {
            setupHikari();
        }
    }
    
    private void setupHikari() {
        connectionLock.lock();
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            driverLoaded = true;
            
            String url = String.format("jdbc:mariadb://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&useUnicode=true",
                    host, port, database, useSSL);
            
            try (Connection testConn = DriverManager.getConnection(url, username, password)) {
                logger.info("Database connection test successful");
            } catch (SQLException e) {
                logger.severe("Database connection failed: " + e.getMessage());
                canConnect = false;
                return;
            }
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setConnectionTimeout(timeout);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("NekoList-HikariPool");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            
            dataSource = new HikariDataSource(config);
            logger.info("HikariCP connection pool initialized");
        } catch (ClassNotFoundException e) {
            logger.severe("MariaDB JDBC Driver not found! Make sure it's in dependencies.");
            canConnect = false;
        } catch (Exception e) {
            logger.severe("Failed to initialize HikariCP: " + e.getMessage());
            canConnect = false;
        } finally {
            connectionLock.unlock();
        }
    }
    
    public void updateConfig(Map<String, Object> config) {
        connectionLock.lock();
        try {
            disconnect();
            driverLoaded = false;
            canConnect = true;
            initConfig(config);
        } finally {
            connectionLock.unlock();
        }
    }
    
    public void switchToFileDatabase() {
        connectionLock.lock();
        try {
            disconnect();
            this.type = "file";
            driverLoaded = false;
            canConnect = true;
            tableLocks.clear();
        } finally {
            connectionLock.unlock();
        }
    }
    
    public boolean testConnection() {
        if ("file".equalsIgnoreCase(type)) {
            return true;
        }
        
        if (!driverLoaded) {
            return false;
        }
        
        if (dataSource == null) {
            return false;
        }
        
        try (Connection conn = dataSource.getConnection()) {
            logger.info("Database connection test successful");
            return true;
        } catch (SQLException e) {
            logger.warning("Database connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    public Connection getConnection() throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            throw new SQLException("File database doesn't support connections");
        }
        
        if (!driverLoaded) {
            throw new SQLException("Database driver not loaded");
        }
        
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not available");
        }
        
        return dataSource.getConnection();
    }
    
    public void disconnect() {
        connectionLock.lock();
        try {
            tableLocks.clear();
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                logger.info("HikariCP connection pool closed");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error closing database connection", e);
        } finally {
            connectionLock.unlock();
        }
    }
    
    public void initializeTable() throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return;
        }
        
        if (!testConnection()) {
            throw new SQLException("Cannot initialize table - connection failed");
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                         "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                         "`username` VARCHAR(64) NOT NULL," +
                         "`uuid` VARCHAR(36) UNIQUE," +
                         "`created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                         "UNIQUE KEY idx_username_lower (username)," +
                         "INDEX idx_uuid (uuid)" +
                         ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            
            stmt.execute(sql);
            logger.info("Database table initialized");
            
            sql = "CREATE TABLE IF NOT EXISTS `" + table + "_settings` (" +
                  "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                  "`whitelist_enabled` BOOLEAN NOT NULL DEFAULT FALSE," +
                  "`updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                  ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            
            stmt.execute(sql);
            logger.info("Settings table initialized");
            
            sql = "INSERT IGNORE INTO `" + table + "_settings` (id, whitelist_enabled) VALUES (1, FALSE)";
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.severe("Failed to initialize database table: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public void updateWhitelistEnabled(boolean enabled) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return;
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table + "_settings", k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE `" + table + "_settings` SET whitelist_enabled = ? WHERE id = 1")) {
                stmt.setBoolean(1, enabled);
                stmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("Failed to update whitelist enabled status: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public boolean getWhitelistEnabled() throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            throw new SQLException("File database doesn't store whitelist status");
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table + "_settings", k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT whitelist_enabled FROM `" + table + "_settings` WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getBoolean("whitelist_enabled");
            }
            return false;
        } catch (SQLException e) {
            logger.severe("Failed to get whitelist enabled status: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public boolean isPlayerWhitelisted(String username) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return false;
        }
        
        if (!canConnect) {
            return false;
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) as count FROM `" + table + "` WHERE LOWER(`username`) = LOWER(?)")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to check if player is whitelisted by username: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
        return false;
    }
    
    public boolean isPlayerWhitelisted(UUID uuid) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return false;
        }
        
        if (!canConnect) {
            return false;
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) as count FROM `" + table + "` WHERE `uuid` = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to check if player is whitelisted by UUID: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
        return false;
    }
    
    public void addPlayer(String username, UUID uuid) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return;
        }
        
        if (!canConnect) {
            throw new SQLException("Cannot add player - database connection failed");
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `" + table + "` (`username`, `uuid`) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE `username` = VALUES(`username`), `uuid` = VALUES(`uuid`)")) {
                stmt.setString(1, username);
                stmt.setString(2, uuid != null ? uuid.toString() : null);
                stmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("Failed to add player to database: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public void removePlayer(String username) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return;
        }
        
        if (!canConnect) {
            throw new SQLException("Cannot remove player - database connection failed");
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM `" + table + "` WHERE LOWER(`username`) = LOWER(?)")) {
                stmt.setString(1, username);
                stmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("Failed to remove player from database: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public Set<String> getAllPlayers() throws SQLException {
        Set<String> players = new HashSet<>();
        
        if ("file".equalsIgnoreCase(type)) {
            return players;
        }
        
        if (!canConnect) {
            return players;
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT `username` FROM `" + table + "` ORDER BY `username`")) {
            while (rs.next()) {
                players.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            logger.severe("Failed to get all players from database: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
        
        return players;
    }
    
    public void updatePlayerUUID(String username, UUID uuid) throws SQLException {
        if ("file".equalsIgnoreCase(type)) {
            return;
        }
        
        if (!canConnect) {
            throw new SQLException("Cannot update player - database connection failed");
        }
        
        ReentrantLock tableLock = tableLocks.computeIfAbsent(table, k -> new ReentrantLock());
        tableLock.lock();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE `" + table + "` SET `uuid` = ? WHERE LOWER(`username`) = LOWER(?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update player UUID in database: " + e.getMessage());
            throw e;
        } finally {
            tableLock.unlock();
        }
    }
    
    public String getType() {
        return type;
    }
    
    public boolean isFileDatabase() {
        return "file".equalsIgnoreCase(type);
    }
    
    public boolean canConnect() {
        return canConnect;
    }
    
    public String getDataFolderPath() {
        return dataFolderPath;
    }
    
    public String getTable() {
        return table;
    }
}