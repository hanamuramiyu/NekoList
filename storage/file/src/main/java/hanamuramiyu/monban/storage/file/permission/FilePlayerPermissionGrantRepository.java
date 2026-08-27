package hanamuramiyu.monban.storage.file.permission;

import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PermissionGrantRemoveResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.storage.file.internal.FileYamlSupport;
import hanamuramiyu.monban.storage.file.internal.PlayerIdentityYamlCodec;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FilePlayerPermissionGrantRepository implements PlayerPermissionGrantRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String LABEL = "player permissions";
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String PERMISSIONS_KEY = "permissions";
    private static final String IDENTITY_KEY = "identity";
    private static final String SCOPE_KEY = "scope";
    private static final String NODE_KEY = "node";
    private static final String TYPE_KEY = "type";
    private static final String ID_KEY = "id";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, PERMISSIONS_KEY);
    private static final Set<String> PERMISSION_FIELDS = Set.of(IDENTITY_KEY, SCOPE_KEY, NODE_KEY);
    private static final Set<String> SCOPE_FIELDS = Set.of(TYPE_KEY, ID_KEY);

    private final Path file;
    private final Map<GrantKey, PlayerPermissionGrant> grants;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Yaml loader = FileYamlSupport.createLoader();
    private final Yaml dumper = FileYamlSupport.createDumper();
    private final PlayerIdentityYamlCodec identityCodec = new PlayerIdentityYamlCodec();

    public FilePlayerPermissionGrantRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.grants = loadGrants();
    }

    @Override
    public List<PlayerPermissionGrant> findAll() {
        readLock.lock();
        try {
            return List.copyOf(grants.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PermissionGrantAddResult add(PlayerPermissionGrant grant) {
        Objects.requireNonNull(grant, "grant");
        GrantKey key = new GrantKey(grant);
        writeLock.lock();
        try {
            if (grants.containsKey(key)) {
                return PermissionGrantAddResult.ALREADY_EXISTS;
            }
            Map<GrantKey, PlayerPermissionGrant> updated = new LinkedHashMap<>(grants);
            updated.put(key, grant);
            persist(updated);
            replace(updated);
            return PermissionGrantAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PermissionGrantRemoveResult remove(PlayerPermissionGrant grant) {
        Objects.requireNonNull(grant, "grant");
        GrantKey key = new GrantKey(grant);
        writeLock.lock();
        try {
            if (!grants.containsKey(key)) {
                return PermissionGrantRemoveResult.NOT_FOUND;
            }
            Map<GrantKey, PlayerPermissionGrant> updated = new LinkedHashMap<>(grants);
            updated.remove(key);
            persist(updated);
            replace(updated);
            return PermissionGrantRemoveResult.REMOVED;
        } finally {
            writeLock.unlock();
        }
    }

    private Map<GrantKey, PlayerPermissionGrant> loadGrants() throws IOException {
        Path temporaryFile = FileYamlSupport.temporaryFile(file);
        if (Files.exists(file)) {
            requireRegularFile(file);
            Map<GrantKey, PlayerPermissionGrant> loaded = parseGrants(file);
            FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
            return loaded;
        }
        if (!Files.exists(temporaryFile)) {
            return new LinkedHashMap<>();
        }
        requireRegularFile(temporaryFile);
        Map<GrantKey, PlayerPermissionGrant> recovered = parseGrants(temporaryFile);
        FileYamlSupport.moveReplacing(temporaryFile, file);
        return recovered;
    }

    private Map<GrantKey, PlayerPermissionGrant> parseGrants(Path source) throws IOException {
        Map<?, ?> root = FileYamlSupport.requireMap(
                FileYamlSupport.loadDocument(loader, source, LABEL),
                "root"
        );
        FileYamlSupport.requireOnlyFields(root, "root", ROOT_FIELDS);
        BigInteger version = FileYamlSupport.requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(version)) {
            throw FileYamlSupport.invalidFile("Unsupported player permissions schema version: " + version);
        }

        List<?> serializedGrants = FileYamlSupport.requireList(root.get(PERMISSIONS_KEY), PERMISSIONS_KEY);
        Map<GrantKey, PlayerPermissionGrant> loaded = new LinkedHashMap<>();
        for (int index = 0; index < serializedGrants.size(); index++) {
            String path = PERMISSIONS_KEY + "[" + index + "]";
            Map<?, ?> serialized = FileYamlSupport.requireMap(serializedGrants.get(index), path);
            FileYamlSupport.requireOnlyFields(serialized, path, PERMISSION_FIELDS);
            PlayerIdentity identity = identityCodec.decode(
                    FileYamlSupport.requireMap(serialized.get(IDENTITY_KEY), path + "." + IDENTITY_KEY),
                    path + "." + IDENTITY_KEY
            );
            AccessScope scope = deserializeScope(
                    FileYamlSupport.requireMap(serialized.get(SCOPE_KEY), path + "." + SCOPE_KEY),
                    path + "." + SCOPE_KEY
            );
            String node = FileYamlSupport.requireString(serialized.get(NODE_KEY), path + "." + NODE_KEY);
            PlayerPermissionGrant grant;
            try {
                grant = new PlayerPermissionGrant(identity, new PermissionGrant(scope, node));
            } catch (RuntimeException exception) {
                throw FileYamlSupport.invalidFile("Invalid player permission at " + path + ": " + exception.getMessage(), exception);
            }
            if (loaded.putIfAbsent(new GrantKey(grant), grant) != null) {
                throw FileYamlSupport.invalidFile("Duplicate player permission at " + path + ": " + identity.name() + " / " + node);
            }
        }
        return loaded;
    }

    private AccessScope deserializeScope(Map<?, ?> serialized, String path) throws IOException {
        FileYamlSupport.requireOnlyFields(serialized, path, SCOPE_FIELDS);
        String typeValue = FileYamlSupport.requireString(serialized.get(TYPE_KEY), path + "." + TYPE_KEY);
        AccessScopeType type;
        try {
            type = AccessScopeType.valueOf(typeValue);
        } catch (IllegalArgumentException exception) {
            throw FileYamlSupport.invalidFile("Invalid access scope type at " + path + ": " + typeValue, exception);
        }
        try {
            if (type == AccessScopeType.NETWORK) {
                if (serialized.containsKey(ID_KEY)) {
                    throw FileYamlSupport.invalidFile("NETWORK scope must not have an id at " + path);
                }
                return AccessScope.network();
            }
            String id = FileYamlSupport.requireString(serialized.get(ID_KEY), path + "." + ID_KEY);
            return type == AccessScopeType.SERVER_GROUP
                    ? AccessScope.serverGroup(id)
                    : AccessScope.server(id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw FileYamlSupport.invalidFile("Invalid access scope at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void persist(Map<GrantKey, PlayerPermissionGrant> snapshot) {
        try {
            FileYamlSupport.writeAtomically(file, serialize(snapshot));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist player permissions to " + file, exception);
        }
    }

    private String serialize(Map<GrantKey, PlayerPermissionGrant> snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        List<Map<String, Object>> serializedGrants = new ArrayList<>(snapshot.size());
        for (PlayerPermissionGrant grant : snapshot.values()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put(IDENTITY_KEY, identityCodec.encode(grant.identity()));
            serialized.put(SCOPE_KEY, serializeScope(grant.grant().scope()));
            serialized.put(NODE_KEY, grant.grant().node());
            serializedGrants.add(serialized);
        }
        root.put(PERMISSIONS_KEY, serializedGrants);
        return dumper.dump(root);
    }

    private Map<String, Object> serializeScope(AccessScope scope) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put(TYPE_KEY, scope.type().name());
        scope.id().ifPresent(id -> serialized.put(ID_KEY, id));
        return serialized;
    }

    private void replace(Map<GrantKey, PlayerPermissionGrant> updated) {
        grants.clear();
        grants.putAll(updated);
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(LABEL + " path is not a regular file: " + path);
        }
    }

    private record GrantKey(PlayerIdentity identity, AccessScope scope, String node) {
        private GrantKey(PlayerPermissionGrant grant) {
            this(grant.identity(), grant.grant().scope(), grant.grant().node());
        }
    }
}
