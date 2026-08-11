package hanamuramiyu.monban.velocity;

import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.IdentityResolutionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonbanVelocityPluginHybridConfigurationTest {
    @Test
    void enabledHybridAcceptsOfflineModeVelocity() {
        assertDoesNotThrow(() -> MonbanVelocityPlugin.requireHybridVelocityOnlineMode(hybridConfig(true), false));
    }

    @Test
    void enabledHybridRejectsOnlineModeVelocity() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> MonbanVelocityPlugin.requireHybridVelocityOnlineMode(hybridConfig(true), true)
        );

        assertTrue(exception.getMessage().contains("online-mode=false"));
    }

    @Test
    void disabledHybridDoesNotRestrictVelocityOnlineMode() {
        assertDoesNotThrow(() -> MonbanVelocityPlugin.requireHybridVelocityOnlineMode(hybridConfig(false), true));
    }

    private static MonbanConfig hybridConfig(boolean enabled) {
        return new MonbanConfig(
                new DeploymentSettings(DeploymentMode.VELOCITY),
                WhitelistSettings.defaults(),
                new IdentitySettings(
                        IdentityResolutionMode.AUTO,
                        new HybridIdentitySettings(enabled, HybridIdentityPreference.ONLINE)
                )
        );
    }
}
