package hanamuramiyu.monban.config;

public record WhitelistSettings(boolean enabled) {
    public static WhitelistSettings defaults() {
        return new WhitelistSettings(false);
    }
}
