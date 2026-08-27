package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.sync.SyncSecret;

import java.util.Objects;
import java.util.Optional;

public final class BackendPermissionConfiguration {
    private BackendPermissionConfiguration() {
    }

    public static void requireSynchronization(
            boolean enabled,
            Optional<String> backendServerName,
            SyncSecret syncSecret
    ) {
        Objects.requireNonNull(backendServerName, "backendServerName");
        if (!enabled) {
            return;
        }
        if (backendServerName.isEmpty()) {
            throw new IllegalStateException("backend-permissions.enabled requires server-name.");
        }
        if (syncSecret == null) {
            throw new IllegalStateException(
                    "backend-permissions.enabled requires sync.yml for centralized backend permissions."
            );
        }
    }
}
