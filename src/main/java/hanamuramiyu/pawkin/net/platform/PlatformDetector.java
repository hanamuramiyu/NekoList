package hanamuramiyu.pawkin.net.platform;

public class PlatformDetector {
    public static PlatformType detectPlatform() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return PlatformType.BUKKIT;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.velocitypowered.api.proxy.ProxyServer");
                return PlatformType.VELOCITY;
            } catch (ClassNotFoundException e2) {
                return PlatformType.UNKNOWN;
            }
        }
    }
    
    public enum PlatformType {
        BUKKIT, VELOCITY, UNKNOWN
    }
}