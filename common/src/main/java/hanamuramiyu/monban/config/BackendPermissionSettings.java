package hanamuramiyu.monban.config;

import java.util.Objects;
import java.util.Optional;

public record BackendPermissionSettings(boolean enabled, Optional<String> serverName) {
    public BackendPermissionSettings {
        Objects.requireNonNull(serverName, "serverName");
        serverName = serverName.map(BackendPermissionSettings::requireServerName);
    }

    public static BackendPermissionSettings defaults() {
        return new BackendPermissionSettings(false, Optional.empty());
    }

    public BackendPermissionSettings withServerName(String value) {
        return new BackendPermissionSettings(enabled, Optional.of(value));
    }

    private static String requireServerName(String value) {
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Backend server name must be clean text: " + value);
        }
        return value;
    }
}
