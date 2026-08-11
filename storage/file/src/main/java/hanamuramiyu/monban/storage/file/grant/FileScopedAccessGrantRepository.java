package hanamuramiyu.monban.storage.file.grant;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.storage.file.internal.PlayerIdentityYamlCodec;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FileScopedAccessGrantRepository implements AccessGrantRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String GRANTS_KEY = "grants";
    private static final String SCOPE_KEY = "scope";
    private static final String IDENTITY_KEY = "identity";
    private static final String TYPE_KEY = "type";
    private static final String ID_KEY = "id";

    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, GRANTS_KEY);
    private static final Set<String> GRANT_FIELDS = Set.of(SCOPE_KEY, IDENTITY_KEY);
    private static final Set<String> SCOPE_FIELDS = Set.of(TYPE_KEY, ID_KEY);

    private final Path file;
    private final Map<GrantKey, AccessGrant> grants;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Yaml loader;
    private final Yaml dumper;
    private final PlayerIdentityYamlCodec identityCodec;

    public FileScopedAccessGrantRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.loader = createLoader();
        this.dumper = createDumper();
        this.identityCodec = new PlayerIdentityYamlCodec();
        this.grants = loadGrants();
    }

    @Override
    public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
        requireSupportedScope(scope);
        Objects.requireNonNull(identity, "identity");
        GrantKey key = new GrantKey(scope, identity);

        readLock.lock();
        try {
            return Optional.ofNullable(grants.get(key));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<AccessGrant> findAll() {
        readLock.lock();
        try {
            return List.copyOf(grants.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public AccessGrantAddResult add(AccessGrant grant) {
        Objects.requireNonNull(grant, "grant");
        requireSupportedScope(grant.scope());
        GrantKey key = new GrantKey(grant.scope(), grant.identity());

        writeLock.lock();
        try {
            if (grants.containsKey(key)) {
                return AccessGrantAddResult.ALREADY_EXISTS;
            }

            Map<GrantKey, AccessGrant> updatedGrants = copyGrants();
            updatedGrants.put(key, grant);
            persist(updatedGrants);
            replaceGrants(updatedGrants);
            return AccessGrantAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
        requireSupportedScope(scope);
        Objects.requireNonNull(identity, "identity");
        GrantKey key = new GrantKey(scope, identity);

        writeLock.lock();
        try {
            if (!grants.containsKey(key)) {
                return AccessGrantRemoveResult.NOT_FOUND;
            }

            Map<GrantKey, AccessGrant> updatedGrants = copyGrants();
            updatedGrants.remove(key);
            persist(updatedGrants);
            replaceGrants(updatedGrants);
            return AccessGrantRemoveResult.REMOVED;
        } finally {
            writeLock.unlock();
        }
    }

    private Map<GrantKey, AccessGrant> loadGrants() throws IOException {
        Path temporaryFile = temporaryFile();

        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("Access grants path is not a regular file: " + file);
            }

            Map<GrantKey, AccessGrant> loadedGrants = parseGrants(file);
            deleteStaleTemporaryFile(temporaryFile);
            return loadedGrants;
        }

        if (!Files.exists(temporaryFile)) {
            return new LinkedHashMap<>();
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new IOException("Access grants temporary path is not a regular file: " + temporaryFile);
        }

        Map<GrantKey, AccessGrant> recoveredGrants = parseGrants(temporaryFile);
        moveReplacing(temporaryFile, file);
        return recoveredGrants;
    }

    private Map<GrantKey, AccessGrant> parseGrants(Path source) throws IOException {
        String yamlText = Files.readString(source, StandardCharsets.UTF_8);
        rejectAliases(yamlText);

        Object document;
        try {
            Iterator<Object> documents = loader.loadAll(yamlText).iterator();
            if (!documents.hasNext()) {
                throw invalidFile("Access grants file is empty");
            }

            document = documents.next();
            if (documents.hasNext()) {
                throw invalidFile("Access grants file must contain exactly one YAML document");
            }
        } catch (YAMLException exception) {
            throw invalidFile("Access grants file contains invalid YAML", exception);
        }

        Map<?, ?> root = requireMap(document, "root");
        requireOnlyFields(root, "root", ROOT_FIELDS);

        BigInteger schemaVersion = requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(schemaVersion)) {
            throw invalidFile("Unsupported access grants schema version: " + schemaVersion);
        }

        List<?> serializedGrants = requireList(root.get(GRANTS_KEY), GRANTS_KEY);
        Map<GrantKey, AccessGrant> loadedGrants = new LinkedHashMap<>();

        for (int index = 0; index < serializedGrants.size(); index++) {
            String grantPath = GRANTS_KEY + "[" + index + "]";
            Map<?, ?> serializedGrant = requireMap(serializedGrants.get(index), grantPath);
            requireOnlyFields(serializedGrant, grantPath, GRANT_FIELDS);

            AccessScope scope = deserializeScope(
                    requireMap(serializedGrant.get(SCOPE_KEY), grantPath + "." + SCOPE_KEY),
                    grantPath + "." + SCOPE_KEY
            );
            PlayerIdentity identity = identityCodec.decode(
                    requireMap(serializedGrant.get(IDENTITY_KEY), grantPath + "." + IDENTITY_KEY),
                    grantPath + "." + IDENTITY_KEY
            );
            AccessGrant grant = new AccessGrant(scope, identity);
            GrantKey key = new GrantKey(scope, identity);

            if (loadedGrants.putIfAbsent(key, grant) != null) {
                throw invalidFile("Duplicate access grant at " + grantPath + ": " + scope + " / " + identity.name());
            }
        }

        return loadedGrants;
    }

    private AccessScope deserializeScope(Map<?, ?> serializedScope, String path) throws IOException {
        requireOnlyFields(serializedScope, path, SCOPE_FIELDS);
        String typeValue = requireString(serializedScope.get(TYPE_KEY), path + "." + TYPE_KEY);

        AccessScopeType type;
        try {
            type = AccessScopeType.valueOf(typeValue);
        } catch (IllegalArgumentException exception) {
            throw invalidFile("Invalid access scope type at " + path + ": " + typeValue, exception);
        }

        if (type == AccessScopeType.NETWORK) {
            throw invalidFile("NETWORK scope is not supported by access-grants.yml at " + path);
        }

        String id = requireString(serializedScope.get(ID_KEY), path + "." + ID_KEY);
        try {
            return switch (type) {
                case SERVER_GROUP -> AccessScope.serverGroup(id);
                case SERVER -> AccessScope.server(id);
                case NETWORK -> throw new IllegalStateException("NETWORK scope was already rejected.");
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidFile("Invalid access scope at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void rejectAliases(String yamlText) throws IOException {
        try {
            for (Event event : loader.parse(new StringReader(yamlText))) {
                if (event instanceof AliasEvent) {
                    throw invalidFile("YAML aliases are not supported in access grants file");
                }
            }
        } catch (YAMLException exception) {
            throw invalidFile("Access grants file contains invalid YAML", exception);
        }
    }

    private void deleteStaleTemporaryFile(Path temporaryFile) throws IOException {
        if (!Files.exists(temporaryFile)) {
            return;
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new IOException("Access grants temporary path is not a regular file: " + temporaryFile);
        }
        Files.delete(temporaryFile);
    }

    private void persist(Map<GrantKey, AccessGrant> snapshot) {
        try {
            writeSnapshot(snapshot);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist access grants to " + file, exception);
        }
    }

    private void writeSnapshot(Map<GrantKey, AccessGrant> snapshot) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);

        Path temporaryFile = temporaryFile();
        byte[] serialized = serialize(snapshot).getBytes(StandardCharsets.UTF_8);

        try {
            try (FileChannel channel = FileChannel.open(
                    temporaryFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(serialized);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            moveReplacing(temporaryFile, file);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path temporaryFile() {
        return file.resolveSibling(file.getFileName() + ".tmp");
    }

    private String serialize(Map<GrantKey, AccessGrant> snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);

        List<Map<String, Object>> serializedGrants = new ArrayList<>(snapshot.size());
        for (AccessGrant grant : snapshot.values()) {
            Map<String, Object> serializedGrant = new LinkedHashMap<>();

            Map<String, Object> serializedScope = new LinkedHashMap<>();
            serializedScope.put(TYPE_KEY, grant.scope().type().name());
            serializedScope.put(ID_KEY, grant.scope().id().orElseThrow());
            serializedGrant.put(SCOPE_KEY, serializedScope);
            serializedGrant.put(IDENTITY_KEY, identityCodec.encode(grant.identity()));

            serializedGrants.add(serializedGrant);
        }

        root.put(GRANTS_KEY, serializedGrants);
        return dumper.dump(root);
    }

    private Map<GrantKey, AccessGrant> copyGrants() {
        return new LinkedHashMap<>(grants);
    }

    private void replaceGrants(Map<GrantKey, AccessGrant> updatedGrants) {
        grants.clear();
        grants.putAll(updatedGrants);
    }

    private static void requireSupportedScope(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.type() == AccessScopeType.NETWORK) {
            throw new IllegalArgumentException(
                    "FileScopedAccessGrantRepository does not support NETWORK scope; "
                            + "whitelist.yml is the NETWORK grant source."
            );
        }
    }

    private static Yaml createLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        return new Yaml(new SafeConstructor(options));
    }

    private static Yaml createDumper() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    private static Map<?, ?> requireMap(Object value, String path) throws IOException {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw invalidFile("Expected mapping at " + path);
    }

    private static List<?> requireList(Object value, String path) throws IOException {
        if (value instanceof List<?> list) {
            return list;
        }
        throw invalidFile("Expected list at " + path);
    }

    private static String requireString(Object value, String path) throws IOException {
        if (value instanceof String string) {
            return string;
        }
        throw invalidFile("Expected string at " + path);
    }

    private static BigInteger requireInteger(Object value, String path) throws IOException {
        if (value instanceof Byte number) {
            return BigInteger.valueOf(number.longValue());
        }
        if (value instanceof Short number) {
            return BigInteger.valueOf(number.longValue());
        }
        if (value instanceof Integer number) {
            return BigInteger.valueOf(number.longValue());
        }
        if (value instanceof Long number) {
            return BigInteger.valueOf(number);
        }
        if (value instanceof BigInteger number) {
            return number;
        }
        throw invalidFile("Expected integer at " + path);
    }

    private static void requireOnlyFields(Map<?, ?> map, String path, Set<String> allowedFields) throws IOException {
        for (Object key : map.keySet()) {
            if (!(key instanceof String field)) {
                throw invalidFile("Expected string field name at " + path);
            }
            if (!allowedFields.contains(field)) {
                throw invalidFile("Unknown field at " + path + "." + field);
            }
        }
    }

    private static IOException invalidFile(String message) {
        return new IOException(message);
    }

    private static IOException invalidFile(String message, Throwable cause) {
        return new IOException(message, cause);
    }

    private record GrantKey(AccessScope scope, PlayerIdentity identity) {
        private GrantKey {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(identity, "identity");
        }
    }
}
