package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class FileBackendAccessConfigLoader {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String DEFAULT_KEY = "default";
    private static final String POLICIES_KEY = "policies";
    private static final String SCOPE_KEY = "scope";
    private static final String TYPE_KEY = "type";
    private static final String ID_KEY = "id";
    private static final String MODE_KEY = "mode";

    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, DEFAULT_KEY, POLICIES_KEY);
    private static final Set<String> POLICY_FIELDS = Set.of(SCOPE_KEY, MODE_KEY);
    private static final Set<String> SCOPE_FIELDS = Set.of(TYPE_KEY, ID_KEY);
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private final Path file;
    private final Yaml loader;

    public FileBackendAccessConfigLoader(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.loader = createLoader();
    }

    public BackendAccessPolicyCatalog load() throws IOException {
        if (!Files.exists(file)) {
            createDefaultFile();
        }

        String yamlText = Files.readString(file, StandardCharsets.UTF_8);
        rejectAliases(yamlText);

        Object document;
        try {
            Iterator<Object> documents = loader.loadAll(yamlText).iterator();
            if (!documents.hasNext()) {
                throw new IOException("Backend access config is empty: " + file);
            }
            document = documents.next();
            if (documents.hasNext()) {
                throw new IOException("Backend access config must contain exactly one YAML document: " + file);
            }
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }

        Map<String, Object> root = requireStringMap(document, "root");
        rejectUnknownFields(root, ROOT_FIELDS, "root");

        int schemaVersion = requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IOException("Unsupported schema-version at " + SCHEMA_VERSION_KEY + ": " + schemaVersion);
        }

        BackendAccessMode defaultMode = requireMode(root.get(DEFAULT_KEY), DEFAULT_KEY);
        List<?> policyDocuments = requireList(root.get(POLICIES_KEY), POLICIES_KEY);
        Map<String, BackendAccessMode> groupPolicies = new LinkedHashMap<>();
        Map<String, BackendAccessMode> serverPolicies = new LinkedHashMap<>();
        Set<AccessScope> seenScopes = new HashSet<>();

        for (int index = 0; index < policyDocuments.size(); index++) {
            String policyPath = POLICIES_KEY + "[" + index + "]";
            Map<String, Object> policy = requireStringMap(policyDocuments.get(index), policyPath);
            rejectUnknownFields(policy, POLICY_FIELDS, policyPath);

            AccessScope scope = parseScope(
                    requireStringMap(policy.get(SCOPE_KEY), policyPath + "." + SCOPE_KEY),
                    policyPath + "." + SCOPE_KEY
            );
            if (!seenScopes.add(scope)) {
                throw new IOException("Duplicate backend access policy for scope: " + scope);
            }
            BackendAccessMode mode = requireMode(policy.get(MODE_KEY), policyPath + "." + MODE_KEY);

            switch (scope.type()) {
                case NETWORK -> throw new IOException("NETWORK scope is not supported by backend-access.yml at " + policyPath);
                case SERVER_GROUP -> groupPolicies.put(scope.id().orElseThrow(), mode);
                case SERVER -> serverPolicies.put(scope.id().orElseThrow(), mode);
            }
        }

        return new BackendAccessPolicyCatalog(defaultMode, groupPolicies, serverPolicies);
    }

    private AccessScope parseScope(Map<String, Object> scope, String path) throws IOException {
        rejectUnknownFields(scope, SCOPE_FIELDS, path);
        String typeText = requireString(scope.get(TYPE_KEY), path + "." + TYPE_KEY);
        AccessScopeType type;
        try {
            type = AccessScopeType.valueOf(typeText);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported backend access scope type at " + path + "." + TYPE_KEY + ": " + typeText, exception);
        }
        if (type == AccessScopeType.NETWORK) {
            throw new IOException("NETWORK scope is not supported by backend-access.yml at " + path);
        }

        String id = requireString(scope.get(ID_KEY), path + "." + ID_KEY);
        if (type == AccessScopeType.SERVER_GROUP && !GROUP_ID_PATTERN.matcher(id).matches()) {
            throw new IOException(
                    "Invalid server group id at " + path + "." + ID_KEY
                            + ": expected [a-z0-9][a-z0-9._-]{0,63}, got " + id
            );
        }

        try {
            return type == AccessScopeType.SERVER_GROUP ? AccessScope.serverGroup(id) : AccessScope.server(id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid backend access scope at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void createDefaultFile() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            byte[] bytes = "schema-version: 1\ndefault: OPEN\npolicies: []\n".getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private void rejectAliases(String yamlText) throws IOException {
        try {
            for (Event event : loader.parse(new StringReader(yamlText))) {
                if (event instanceof AliasEvent) {
                    throw new IOException("YAML aliases are not supported in backend access config: " + file);
                }
            }
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }
    }

    private static Yaml createLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        return new Yaml(new SafeConstructor(options));
    }

    private static Map<String, Object> requireStringMap(Object value, String path) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("Expected mapping at " + path);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IOException("Expected string field name at " + path);
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<?> requireList(Object value, String path) throws IOException {
        if (value instanceof List<?> list) return list;
        throw new IOException("Expected list at " + path);
    }

    private static String requireString(Object value, String path) throws IOException {
        if (!(value instanceof String text)) throw new IOException("Expected string at " + path);
        if (text.isBlank()) throw new IOException("Expected non-blank string at " + path);
        if (!text.equals(text.strip())) throw new IOException("Leading or trailing whitespace is not allowed at " + path + ": " + text);
        return text;
    }

    private static BackendAccessMode requireMode(Object value, String path) throws IOException {
        String text = requireString(value, path);
        try {
            return BackendAccessMode.valueOf(text);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported backend access mode at " + path + ": " + text, exception);
        }
    }

    private static void rejectUnknownFields(Map<String, Object> map, Set<String> allowed, String path) throws IOException {
        for (String key : map.keySet()) if (!allowed.contains(key)) throw new IOException("Unknown field at " + path + "." + key);
    }

    private static int requireInteger(Object value, String path) throws IOException {
        if (value instanceof Integer integer) return integer;
        if (value instanceof Byte byteValue) return byteValue.intValue();
        if (value instanceof Short shortValue) return shortValue.intValue();
        if (value instanceof Long longValue && longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) return longValue.intValue();
        if (value instanceof java.math.BigInteger bigInteger && bigInteger.bitLength() < 32) return bigInteger.intValue();
        throw new IOException("Expected integer at " + path);
    }
}
