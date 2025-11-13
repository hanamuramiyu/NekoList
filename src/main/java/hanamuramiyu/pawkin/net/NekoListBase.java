package hanamuramiyu.pawkin.net;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface NekoListBase {
    String getMessage(String path);
    boolean isPlayerWhitelisted(String playerName);
    boolean isPlayerWhitelisted(UUID playerUUID);
    void addPlayerToWhitelist(String playerName);
    void addPlayerToWhitelist(String playerName, UUID uuid);
    void removePlayerFromWhitelist(String playerName);
    void updatePlayerData(String playerName, UUID uuid);
    Set<String> getWhitelistedPlayers();
    boolean isWhitelistEnabled();
    Map<String, Object> getPluginConfig();
    boolean isOnlineMode();
}