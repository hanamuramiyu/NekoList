package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.BackendPermissionSettings;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.IdentityResolutionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMonbanConfigLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingConfigCreatesStandaloneDefaultsWithoutHybridSection() throws IOException {
        Path file = tempDirectory.resolve("config.yml");

        MonbanConfig config = new FileMonbanConfigLoader(file).load();

        assertTrue(Files.isRegularFile(file));
        assertEquals(MonbanConfig.defaults(), config);
        assertEquals(HybridIdentitySettings.defaults(), config.identity().hybrid());
        assertFalse(config.identity().hybrid().enabled());
        assertEquals(HybridIdentityPreference.ONLINE, config.identity().hybrid().dualEntryPreference());
        assertEquals(standaloneConfig(false, "AUTO"), Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("hybrid:"));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("backend-permissions:"));
    }

    @Test
    void missingConfigCreatesVelocityDefaultsWithHybridSection() throws IOException {
        Path file = tempDirectory.resolve("config.yml");
        MonbanConfig velocityDefaults = velocityDefaults();

        MonbanConfig config = new FileMonbanConfigLoader(file, velocityDefaults).load();

        assertTrue(Files.isRegularFile(file));
        assertEquals(velocityDefaults, config);
        assertEquals(velocityConfig(false, "AUTO", false, "ONLINE"), Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("hybrid:"));
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("server-name:"));
    }

    @Test
    void standaloneWithoutHybridLoadsWithDomainHybridDefaults() throws IOException {
        Path file = writeConfig(standaloneConfig(true, "OFFLINE"));

        MonbanConfig config = new FileMonbanConfigLoader(file).load();

        assertEquals(DeploymentMode.STANDALONE, config.deployment().mode());
        assertTrue(config.whitelist().enabled());
        assertEquals(IdentityResolutionMode.OFFLINE, config.identity().mode());
        assertEquals(HybridIdentitySettings.defaults(), config.identity().hybrid());
    }

    @Test
    void suppliedCreationDefaultsDoNotOverwriteExistingConfig() throws IOException {
        Path file = writeConfig(standaloneConfig(true, "OFFLINE"));
        String originalContent = Files.readString(file, StandardCharsets.UTF_8);

        MonbanConfig config = new FileMonbanConfigLoader(file, velocityDefaults()).load();

        assertEquals(DeploymentMode.STANDALONE, config.deployment().mode());
        assertTrue(config.whitelist().enabled());
        assertEquals(IdentityResolutionMode.OFFLINE, config.identity().mode());
        assertEquals(HybridIdentitySettings.defaults(), config.identity().hybrid());
        assertEquals(originalContent, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void saveUpdatesWhitelistStateAndKeepsVelocitySettings() throws IOException {
        Path file = writeConfig(velocityConfig(false, "AUTO", true, "OFFLINE"));
        FileMonbanConfigLoader loader = new FileMonbanConfigLoader(file);
        MonbanConfig original = loader.load();

        loader.save(new MonbanConfig(
                original.deployment(),
                new WhitelistSettings(true),
                original.identity()
        ));

        MonbanConfig saved = loader.load();
        assertTrue(saved.whitelist().enabled());
        assertEquals(original.deployment(), saved.deployment());
        assertEquals(original.identity(), saved.identity());
    }

    @Test
    void loadsConfiguredHybridValuesWithoutChangingConfigVersion() throws IOException {
        Path file = writeConfig(velocityConfig(true, "AUTO", true, "OFFLINE"));

        MonbanConfig config = new FileMonbanConfigLoader(file).load();

        assertEquals(DeploymentMode.VELOCITY, config.deployment().mode());
        assertTrue(config.whitelist().enabled());
        assertEquals(IdentityResolutionMode.AUTO, config.identity().mode());
        assertTrue(config.identity().hybrid().enabled());
        assertEquals(HybridIdentityPreference.OFFLINE, config.identity().hybrid().dualEntryPreference());
    }

    @Test
    void loadsBackendPermissionSettingsFromConfig() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: true
                identity:
                  mode: AUTO
                backend-permissions:
                  enabled: true
                  server-name: survival
                """);

        MonbanConfig config = new FileMonbanConfigLoader(file).load();

        assertEquals(
                new BackendPermissionSettings(true, java.util.Optional.of("survival")),
                config.backendPermissions()
        );
    }

    @Test
    void addsBackendPermissionDefaultsToExistingConfig() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                """);

        MonbanConfig config = new FileMonbanConfigLoader(file).load();
        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertEquals(BackendPermissionSettings.defaults(), config.backendPermissions());
        assertTrue(content.contains("backend-permissions:\n  enabled: false\n  server-name: ''\n"));
        assertTrue(content.startsWith("config-version: 1\ndeployment:"));
    }

    @Test
    void removesBackendServerNameFromExistingVelocityConfig() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: false
                    dual-entry-preference: ONLINE
                backend-permissions:
                  enabled: true
                  server-name: ''
                """);

        MonbanConfig config = new FileMonbanConfigLoader(file).load();
        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertEquals(new BackendPermissionSettings(true, java.util.Optional.empty()), config.backendPermissions());
        assertTrue(content.contains("backend-permissions:\n  enabled: true\n"));
        assertFalse(content.contains("server-name:"));
    }

    @Test
    void emptyBackendServerNameIsAllowedWhileBackendPermissionsAreDisabled() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                backend-permissions:
                  enabled: false
                  server-name: ''
                """);

        MonbanConfig config = new FileMonbanConfigLoader(file).load();

        assertEquals(BackendPermissionSettings.defaults(), config.backendPermissions());
    }

    @Test
    void emptyBackendServerNameIsRejectedWhenBackendPermissionsAreEnabled() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                backend-permissions:
                  enabled: true
                  server-name: ''
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void missingHybridMappingIsRejected() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void hybridEnabledIsRequired() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    dual-entry-preference: ONLINE
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void hybridEnabledMustBeBoolean() throws IOException {
        Path file = writeConfig(velocityConfig(false, "AUTO", "yes-please", "ONLINE"));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void dualEntryPreferenceIsRequired() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: true
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void unknownDualEntryPreferenceFails() throws IOException {
        Path file = writeConfig(velocityConfig(false, "AUTO", true, "MAGIC"));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void unknownHybridFieldFails() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: true
                    dual-entry-preference: ONLINE
                    guess-premium: true
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void standaloneDisabledHybridSectionIsRejected() throws IOException {
        assertStandaloneHybridRejected(false);
    }

    @Test
    void standaloneEnabledHybridSectionIsRejected() throws IOException {
        assertStandaloneHybridRejected(true);
    }

    @Test
    void offlineIdentityModeWithHybridIsRejected() throws IOException {
        Path file = writeConfig(velocityConfig(false, "OFFLINE", true, "ONLINE"));

        IOException exception = assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
        assertTrue(exception.getMessage().contains("identity.mode=AUTO"));
    }

    @Test
    void invalidDeploymentModeFailsInsteadOfUsingDefaults() throws IOException {
        Path file = writeConfig(velocityConfig("MAGIC", false, "AUTO", false, "ONLINE"));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void invalidIdentityModeFailsInsteadOfUsingDefaults() throws IOException {
        Path file = writeConfig(standaloneConfig(false, "MAGIC"));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void trustedProxyIdentityModeIsRejected() throws IOException {
        Path file = writeConfig(standaloneConfig(false, "TRUSTED_PROXY"));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void malformedYamlFailsInsteadOfUsingDefaults() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment: [
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void duplicateKeysAreRejected() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                  enabled: true
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: false
                    dual-entry-preference: ONLINE
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void aliasesAreRejected() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist: &whitelist
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: false
                    dual-entry-preference: ONLINE
                extra: *whitelist
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void multipleDocumentsAreRejected() throws IOException {
        Path file = writeConfig(velocityConfig(false, "AUTO", false, "ONLINE") + "\n---\n{}\n");

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void unsupportedConfigVersionFails() throws IOException {
        Path file = writeConfig(velocityConfig(false, "AUTO", false, "ONLINE").replace(
                "config-version: 1",
                "config-version: 2"
        ));

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    @Test
    void unknownIdentityFieldFails() throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: VELOCITY
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: false
                    dual-entry-preference: ONLINE
                  trusted: true
                """);

        assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
    }

    private void assertStandaloneHybridRejected(boolean enabled) throws IOException {
        Path file = writeConfig("""
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: false
                identity:
                  mode: AUTO
                  hybrid:
                    enabled: %s
                    dual-entry-preference: ONLINE
                """.formatted(enabled));

        IOException exception = assertThrows(IOException.class, () -> new FileMonbanConfigLoader(file).load());
        assertTrue(exception.getMessage().contains("identity.hybrid is not supported in STANDALONE deployment."));
        assertTrue(exception.getMessage().contains("Remove the hybrid section."));
        assertTrue(exception.getMessage().contains("available on Velocity"));
    }

    private static MonbanConfig velocityDefaults() {
        return new MonbanConfig(
                new DeploymentSettings(DeploymentMode.VELOCITY),
                WhitelistSettings.defaults(),
                IdentitySettings.defaults()
        );
    }

    private static String standaloneConfig(boolean whitelistEnabled, String identityMode) {
        return """
                config-version: 1
                deployment:
                  mode: STANDALONE
                whitelist:
                  enabled: %s
                identity:
                  mode: %s
                backend-permissions:
                  enabled: false
                  server-name: ''
                """.formatted(whitelistEnabled, identityMode);
    }

    private static String velocityConfig(
            boolean whitelistEnabled,
            String identityMode,
            Object hybridEnabled,
            String preference
    ) {
        return velocityConfig("VELOCITY", whitelistEnabled, identityMode, hybridEnabled, preference);
    }

    private static String velocityConfig(
            String deploymentMode,
            boolean whitelistEnabled,
            String identityMode,
            Object hybridEnabled,
            String preference
    ) {
        return """
                config-version: 1
                deployment:
                  mode: %s
                whitelist:
                  enabled: %s
                identity:
                  mode: %s
                  hybrid:
                    enabled: %s
                    dual-entry-preference: %s
                backend-permissions:
                  enabled: false
                """.formatted(
                deploymentMode,
                whitelistEnabled,
                identityMode,
                hybridEnabled,
                preference
        );
    }

    private Path writeConfig(String content) throws IOException {
        Path file = tempDirectory.resolve("config.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
