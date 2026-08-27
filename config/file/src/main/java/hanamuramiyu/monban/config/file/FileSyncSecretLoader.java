package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.sync.SyncSecret;
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
import java.security.SecureRandom;
import java.util.Base64;

public final class FileSyncSecretLoader {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String SECRET_KEY = "secret";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, SECRET_KEY);

    private final Path file;
    private final Yaml loader;

    public FileSyncSecretLoader(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        this.loader = new Yaml(new SafeConstructor(options));
    }

    public Optional<SyncSecret> load() throws IOException {
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
        for (String field : root.keySet()) {
            if (!ROOT_FIELDS.contains(field)) {
                throw new IOException("Unknown field at root." + field);
            }
        }
        Object version = root.get(SCHEMA_VERSION_KEY);
        if (!(version instanceof Number number)
                || number.intValue() != SCHEMA_VERSION
                || number.longValue() != SCHEMA_VERSION) {
            throw new IOException("Unsupported schema-version at " + SCHEMA_VERSION_KEY + ".");
        }
        Object secret = root.get(SECRET_KEY);
        if (!(secret instanceof String value) || value.isBlank() || !value.equals(value.strip())) {
            throw new IOException("Expected clean Base64 secret at " + SECRET_KEY + ".");
        }
        try {
            return Optional.of(SyncSecret.fromBase64(value));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid sync secret at " + SECRET_KEY + ".", exception);
        }
    }

    public SyncSecret loadOrCreate() throws IOException {
        Optional<SyncSecret> existing = load();
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        SyncSecret generated = SyncSecret.of(bytes);
        write(generated);
        return generated;
    }

    private void write(SyncSecret secret) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String content = "schema-version: 1\nsecret: "
                + Base64.getEncoder().encodeToString(secret.bytes())
                + "\n";
        Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
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
}
