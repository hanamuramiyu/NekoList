package hanamuramiyu.pawkin.net.core;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Logger;

public class MojangApiClient {
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final long RATE_LIMIT_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    private static long lastRequestTime = 0;
    
    public static UUID fetchUUIDFromMojang(String playerName, Logger logger) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                enforceRateLimit();
                
                URL url = new URL(MOJANG_API_URL + playerName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "NekoList");
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    String response = new String(conn.getInputStream().readAllBytes());
                    if (response.contains("\"id\":")) {
                        String uuidStr = response.split("\"id\":\"")[1].split("\"")[0];
                        return parseUUID(uuidStr);
                    }
                } else if (responseCode == 429) {
                    if (attempt < MAX_RETRIES - 1) {
                        logger.warning("Mojang API rate limit exceeded for player " + playerName + 
                                     ", retrying in " + RETRY_DELAY_MS + "ms (attempt " + (attempt + 1) + ")");
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        logger.warning("Mojang API rate limit exceeded for player " + playerName + " after " + MAX_RETRIES + " attempts");
                        return null;
                    }
                } else if (responseCode == 404) {
                    logger.warning("Player " + playerName + " not found in Mojang API");
                    return null;
                } else {
                    logger.warning("Mojang API returned error code " + responseCode + " for player " + playerName);
                    if (attempt < MAX_RETRIES - 1) {
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                }
                
            } catch (Exception e) {
                logger.warning("Failed to fetch UUID from Mojang for player " + playerName + ": " + e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        
        return null;
    }
    
    private static void enforceRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;
        
        if (timeSinceLastRequest < RATE_LIMIT_MS) {
            Thread.sleep(RATE_LIMIT_MS - timeSinceLastRequest);
        }
        
        lastRequestTime = System.currentTimeMillis();
    }
    
    private static UUID parseUUID(String uuidStr) {
        if (uuidStr.length() == 32) {
            String formattedUuid = uuidStr.substring(0, 8) + "-" + uuidStr.substring(8, 12) + "-" +
                                 uuidStr.substring(12, 16) + "-" + uuidStr.substring(16, 20) + "-" +
                                 uuidStr.substring(20, 32);
            return UUID.fromString(formattedUuid);
        } else if (uuidStr.length() == 36) {
            return UUID.fromString(uuidStr);
        }
        return null;
    }
}