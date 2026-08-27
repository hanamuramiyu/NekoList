package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendPermissionConfigurationTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );

    @Test
    void allowsUnconfiguredStandaloneBackend() {
        assertDoesNotThrow(() -> BackendPermissionConfiguration.requireSynchronization(false, Optional.empty(), null));
    }

    @Test
    void requiresSecretForConfiguredBackend() {
        assertThrows(
                IllegalStateException.class,
                () -> BackendPermissionConfiguration.requireSynchronization(true, Optional.of("survival"), null)
        );
    }

    @Test
    void acceptsConfiguredBackendWithSecret() {
        assertDoesNotThrow(() -> BackendPermissionConfiguration.requireSynchronization(
                true,
                Optional.of("survival"),
                SECRET
        ));
    }
}
