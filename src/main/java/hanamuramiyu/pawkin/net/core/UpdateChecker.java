package hanamuramiyu.pawkin.net.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;

public class UpdateChecker {
    
    public static void checkForUpdates(JavaPlugin plugin, ExecutorService executor) {
        executor.execute(() -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                URL url = new URL("https://api.modrinth.com/v2/project/nekolist/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "NekoList/" + currentVersion);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String response = new String(conn.getInputStream().readAllBytes());
                    if (response.contains("\"version_number\":\"")) {
                        String[] parts = response.split("\"version_number\":\"");
                        if (parts.length > 1) {
                            String latestVersion = parts[1].split("\"")[0];
                            if (isNewerVersion(latestVersion, currentVersion)) {
                                plugin.getLogger().info("§a[UPDATE] New version available: " + latestVersion);
                                plugin.getLogger().info("§a[UPDATE] Download: https://modrinth.com/plugin/nekolist/version/" + latestVersion);
                                plugin.getLogger().info("§e[UPDATE] Current version: " + currentVersion);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }
    
    private static boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            String[] newParts = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");
            
            int maxLength = Math.max(newParts.length, currentParts.length);
            for (int i = 0; i < maxLength; i++) {
                int newPart = i < newParts.length ? parseVersionPart(newParts[i]) : 0;
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                
                if (newPart > currentPart) return true;
                if (newPart < currentPart) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static int parseVersionPart(String part) {
        part = part.split("-")[0];
        part = part.split("\\+")[0];
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}