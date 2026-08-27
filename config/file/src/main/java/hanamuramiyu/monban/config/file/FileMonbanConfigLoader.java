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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FileMonbanConfigLoader {
    private static final int CONFIG_VERSION = 1;
    private static final String CONFIG_VERSION_KEY = "config-version";
    private static final String DEPLOYMENT_KEY = "deployment";
    private static final String WHITELIST_KEY = "whitelist";
    private static final String IDENTITY_KEY = "identity";
    private static final String BACKEND_PERMISSIONS_KEY = "backend-permissions";
    private static final String SERVER_NAME_KEY = "server-name";
    private static final String HYBRID_KEY = "hybrid";
    private static final String ENABLED_KEY = "enabled";
    private static final String MODE_KEY = "mode";
    private static final String DUAL_ENTRY_PREFERENCE_KEY = "dual-entry-preference";

    private static final Set<String> ROOT_FIELDS = Set.of(
            CONFIG_VERSION_KEY,
            DEPLOYMENT_KEY,
            WHITELIST_KEY,
            IDENTITY_KEY,
            BACKEND_PERMISSIONS_KEY
    );
    private static final Set<String> DEPLOYMENT_FIELDS = Set.of(MODE_KEY);
    private static final Set<String> WHITELIST_FIELDS = Set.of(ENABLED_KEY);
    private static final Set<String> STANDALONE_IDENTITY_FIELDS = Set.of(MODE_KEY);
    private static final Set<String> VELOCITY_IDENTITY_FIELDS = Set.of(MODE_KEY, HYBRID_KEY);
    private static final Set<String> HYBRID_FIELDS = Set.of(ENABLED_KEY, DUAL_ENTRY_PREFERENCE_KEY);
    private static final Set<String> BACKEND_PERMISSIONS_FIELDS = Set.of(ENABLED_KEY, SERVER_NAME_KEY);

    private final Path file;
    private final MonbanConfig creationDefaults;
    private final Yaml loader;

    public FileMonbanConfigLoader(Path file) {
        this(file, MonbanConfig.defaults());
    }

    public FileMonbanConfigLoader(Path file, MonbanConfig creationDefaults) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.creationDefaults = Objects.requireNonNull(creationDefaults, "creationDefaults");
        this.loader = createLoader();
    }

    public MonbanConfig load() throws IOException {
        if (!Files.exists(file)) {
            createDefaultFile();
        }

        String yamlText = Files.readString(file, StandardCharsets.UTF_8);
        rejectAliases(yamlText);

        Object document;
        try {
            Iterator<Object> documents = loader.loadAll(yamlText).iterator();
            if (!documents.hasNext()) {
                throw new IOException("Config is empty: " + file);
            }
            document = documents.next();
            if (documents.hasNext()) {
                throw new IOException("Config must contain exactly one YAML document: " + file);
            }
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }

        Map<String, Object> root = requireStringMap(document, "root");
        rejectUnknownFields(root, ROOT_FIELDS, "root");

        int configVersion = requireInteger(root.get(CONFIG_VERSION_KEY), CONFIG_VERSION_KEY);
        if (configVersion != CONFIG_VERSION) {
            throw new IOException("Unsupported config-version at " + CONFIG_VERSION_KEY + ": " + configVersion);
        }

        Map<String, Object> deployment = requireStringMap(root.get(DEPLOYMENT_KEY), DEPLOYMENT_KEY);
        rejectUnknownFields(deployment, DEPLOYMENT_FIELDS, DEPLOYMENT_KEY);
        DeploymentMode deploymentMode = requireDeploymentMode(
                deployment.get(MODE_KEY),
                DEPLOYMENT_KEY + "." + MODE_KEY
        );

        Map<String, Object> whitelist = requireStringMap(root.get(WHITELIST_KEY), WHITELIST_KEY);
        rejectUnknownFields(whitelist, WHITELIST_FIELDS, WHITELIST_KEY);
        boolean whitelistEnabled = requireBoolean(whitelist.get(ENABLED_KEY), WHITELIST_KEY + "." + ENABLED_KEY);

        Map<String, Object> identity = requireStringMap(root.get(IDENTITY_KEY), IDENTITY_KEY);
        IdentityResolutionMode identityMode = requireIdentityMode(
                identity.get(MODE_KEY),
                IDENTITY_KEY + "." + MODE_KEY
        );
        HybridIdentitySettings hybridSettings = switch (deploymentMode) {
            case STANDALONE -> {
                if (identity.containsKey(HYBRID_KEY)) {
                    throw new IOException(
                            "identity.hybrid is not supported in STANDALONE deployment.\n"
                                    + "Remove the hybrid section. Hybrid authentication flow selection is available on Velocity."
                    );
                }
                rejectUnknownFields(identity, STANDALONE_IDENTITY_FIELDS, IDENTITY_KEY);
                yield HybridIdentitySettings.defaults();
            }
            case VELOCITY -> {
                rejectUnknownFields(identity, VELOCITY_IDENTITY_FIELDS, IDENTITY_KEY);
                Map<String, Object> hybrid = requireStringMap(
                        identity.get(HYBRID_KEY),
                        IDENTITY_KEY + "." + HYBRID_KEY
                );
                rejectUnknownFields(hybrid, HYBRID_FIELDS, IDENTITY_KEY + "." + HYBRID_KEY);
                boolean hybridEnabled = requireBoolean(
                        hybrid.get(ENABLED_KEY),
                        IDENTITY_KEY + "." + HYBRID_KEY + "." + ENABLED_KEY
                );
                HybridIdentityPreference dualEntryPreference = requireHybridPreference(
                        hybrid.get(DUAL_ENTRY_PREFERENCE_KEY),
                        IDENTITY_KEY + "." + HYBRID_KEY + "." + DUAL_ENTRY_PREFERENCE_KEY
                );
                yield new HybridIdentitySettings(hybridEnabled, dualEntryPreference);
            }
        };

        boolean backendPermissionsMissing = !root.containsKey(BACKEND_PERMISSIONS_KEY);
        boolean velocityServerNamePresent = deploymentMode == DeploymentMode.VELOCITY
                && root.get(BACKEND_PERMISSIONS_KEY) instanceof Map<?, ?> backendPermissionsSection
                && backendPermissionsSection.containsKey(SERVER_NAME_KEY);
        BackendPermissionSettings backendPermissions = parseBackendPermissions(
                root.get(BACKEND_PERMISSIONS_KEY),
                deploymentMode
        );

        MonbanConfig config = new MonbanConfig(
                new DeploymentSettings(deploymentMode),
                new WhitelistSettings(whitelistEnabled),
                new IdentitySettings(identityMode, hybridSettings),
                backendPermissions
        );
        validateHybridConfiguration(config);
        if (backendPermissionsMissing || velocityServerNamePresent) {
            migrateBackendPermissions(yamlText, config);
        }
        return config;
    }

    public void save(MonbanConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        validateHybridConfiguration(config);
        writeAtomically(serializeConfig(config));
    }

    private void createDefaultFile() throws IOException {
        writeAtomically(serializeCreationDefaults(), false);
    }

    private void writeAtomically(String content) throws IOException {
        writeAtomically(content, true);
    }

    private void writeAtomically(String content, boolean replaceExisting) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            try {
                if (replaceExisting) {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException exception) {
                if (replaceExisting) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, file);
                }
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private String serializeCreationDefaults() {
        return serializeConfig(creationDefaults);
    }

    private void migrateBackendPermissions(String yamlText, MonbanConfig config) throws IOException {
        if (config.deployment().mode() == DeploymentMode.VELOCITY) {
            writeAtomically(serializeConfig(config));
            return;
        }
        String separator = yamlText.endsWith("\n") ? "" : "\n";
        writeAtomically(yamlText + separator + serializeBackendPermissions(config.backendPermissions(), true));
    }

    private String serializeConfig(MonbanConfig config) {
        String base = switch (config.deployment().mode()) {
            case STANDALONE -> """
                    config-version: %d
                    deployment:
                      mode: STANDALONE
                    whitelist:
                      enabled: %s
                    identity:
                      mode: %s
                    """.formatted(
                    CONFIG_VERSION,
                    config.whitelist().enabled(),
                    config.identity().mode().name()
            );
            case VELOCITY -> """
                    config-version: %d
                    deployment:
                      mode: VELOCITY
                    whitelist:
                      enabled: %s
                    identity:
                      mode: %s
                      hybrid:
                        enabled: %s
                        dual-entry-preference: %s
                    """.formatted(
                    CONFIG_VERSION,
                    config.whitelist().enabled(),
                    config.identity().mode().name(),
                    config.identity().hybrid().enabled(),
                    config.identity().hybrid().dualEntryPreference().name()
            );
        };
        return base + serializeBackendPermissions(
                config.backendPermissions(),
                config.deployment().mode() == DeploymentMode.STANDALONE
        );
    }

    private static BackendPermissionSettings parseBackendPermissions(
            Object value,
            DeploymentMode deploymentMode
    ) throws IOException {
        if (value == null) {
            return BackendPermissionSettings.defaults();
        }
        Map<String, Object> section = requireStringMap(value, BACKEND_PERMISSIONS_KEY);
        rejectUnknownFields(section, BACKEND_PERMISSIONS_FIELDS, BACKEND_PERMISSIONS_KEY);
        boolean enabled = requireBoolean(
                section.get(ENABLED_KEY),
                BACKEND_PERMISSIONS_KEY + "." + ENABLED_KEY
        );
        Object serverNameValue = section.get(SERVER_NAME_KEY);
        if (serverNameValue == null) {
            return new BackendPermissionSettings(enabled, java.util.Optional.empty());
        }
        if (!(serverNameValue instanceof String serverName)) {
            throw new IOException("Expected server name at " + BACKEND_PERMISSIONS_KEY + "." + SERVER_NAME_KEY);
        }
        if (deploymentMode == DeploymentMode.VELOCITY) {
            return new BackendPermissionSettings(enabled, java.util.Optional.empty());
        }
        if (serverName.isEmpty() && !enabled) {
            return BackendPermissionSettings.defaults();
        }
        try {
            return new BackendPermissionSettings(enabled, java.util.Optional.of(serverName));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid server name at " + BACKEND_PERMISSIONS_KEY + "." + SERVER_NAME_KEY, exception);
        }
    }

    private static String serializeBackendPermissions(
            BackendPermissionSettings settings,
            boolean includeServerName
    ) {
        String result = "backend-permissions:\n"
                + "  enabled: " + settings.enabled() + "\n";
        if (!includeServerName) {
            return result;
        }
        String serverName = settings.serverName()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .orElse("''");
        return result + "  server-name: " + serverName + "\n";
    }

    private void rejectAliases(String yamlText) throws IOException {
        try {
            for (Event event : loader.parse(new StringReader(yamlText))) {
                if (event instanceof AliasEvent) {
                    throw new IOException("YAML aliases are not supported in config: " + file);
                }
            }
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }
    }

    private static Yaml createLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private static void validateHybridConfiguration(MonbanConfig config) throws IOException {
        if (!config.identity().hybrid().enabled()) {
            return;
        }
        if (config.deployment().mode() != DeploymentMode.VELOCITY) {
            throw new IOException("identity.hybrid is not supported in STANDALONE deployment.");
        }
        if (config.identity().mode() != IdentityResolutionMode.AUTO) {
            throw new IOException("identity.hybrid.enabled=true requires identity.mode=AUTO.");
        }
    }

    private static Map<String, Object> requireStringMap(Object value, String path) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException("Expected mapping at " + path);
        }

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IOException("Expected string field name at " + path);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void rejectUnknownFields(Map<String, Object> map, Set<String> allowed, String path) throws IOException {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new IOException("Unknown field at " + path + "." + key);
            }
        }
    }

    private static int requireInteger(Object value, String path) throws IOException {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Byte byteValue) {
            return byteValue.intValue();
        }
        if (value instanceof Short shortValue) {
            return shortValue.intValue();
        }
        if (value instanceof Long longValue && longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }
        if (value instanceof java.math.BigInteger bigInteger && bigInteger.bitLength() < 32) {
            return bigInteger.intValue();
        }
        throw new IOException("Expected integer at " + path);
    }

    private static boolean requireBoolean(Object value, String path) throws IOException {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IOException("Expected boolean at " + path);
    }

    private static DeploymentMode requireDeploymentMode(Object value, String path) throws IOException {
        if (!(value instanceof String text)) {
            throw new IOException("Expected deployment mode at " + path);
        }

        try {
            return DeploymentMode.valueOf(text);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported deployment mode at " + path + ": " + text, exception);
        }
    }

    private static IdentityResolutionMode requireIdentityMode(Object value, String path) throws IOException {
        if (!(value instanceof String text)) {
            throw new IOException("Expected identity mode at " + path);
        }

        try {
            return IdentityResolutionMode.valueOf(text);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported identity mode at " + path + ": " + text, exception);
        }
    }

    private static HybridIdentityPreference requireHybridPreference(Object value, String path) throws IOException {
        if (!(value instanceof String text)) {
            throw new IOException("Expected hybrid identity preference at " + path);
        }

        try {
            return HybridIdentityPreference.valueOf(text);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported hybrid identity preference at " + path + ": " + text, exception);
        }
    }
}
