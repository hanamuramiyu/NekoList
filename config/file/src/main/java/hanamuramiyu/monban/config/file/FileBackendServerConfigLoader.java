package hanamuramiyu.monban.config.file;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FileBackendServerConfigLoader {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String SERVER_NAME_KEY = "server-name";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, SERVER_NAME_KEY);

    private final Path file;
    private final Yaml loader;

    public FileBackendServerConfigLoader(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.loader = createLoader();
    }

    public Optional<String> load() throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        Object document;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            document = loader.load(reader);
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }

        Map<String, Object> root = requireStringMap(document, "root");
        rejectUnknownFields(root, ROOT_FIELDS, "root");
        if (!(root.get(SCHEMA_VERSION_KEY) instanceof Number version)
                || version.intValue() != SCHEMA_VERSION
                || version.longValue() != SCHEMA_VERSION) {
            throw new IOException("Unsupported schema-version at " + SCHEMA_VERSION_KEY + ".");
        }

        Object value = root.get(SERVER_NAME_KEY);
        if (!(value instanceof String serverName)
                || serverName.isBlank()
                || !serverName.equals(serverName.strip())) {
            throw new IOException("Expected clean server name at " + SERVER_NAME_KEY + ".");
        }
        return Optional.of(serverName);
    }

    private static Yaml createLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
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

    private static void rejectUnknownFields(Map<String, Object> map, Set<String> allowed, String path)
            throws IOException {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new IOException("Unknown field at " + path + "." + key);
            }
        }
    }
}
