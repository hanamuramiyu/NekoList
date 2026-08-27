package hanamuramiyu.monban.storage.file.group;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.group.PlayerGroupUpdateResult;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;
import hanamuramiyu.monban.storage.file.internal.FileYamlSupport;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FilePlayerGroupRepository implements PlayerGroupRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String LABEL = "player groups";
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String GROUPS_KEY = "groups";
    private static final String ID_KEY = "id";
    private static final String ACCESS_GRANTS_KEY = "access-grants";
    private static final String PERMISSIONS_KEY = "permissions";
    private static final String SCOPE_KEY = "scope";
    private static final String NODE_KEY = "node";
    private static final String TYPE_KEY = "type";

    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, GROUPS_KEY);
    private static final Set<String> GROUP_FIELDS = Set.of(ID_KEY, ACCESS_GRANTS_KEY, PERMISSIONS_KEY);
    private static final Set<String> PERMISSION_FIELDS = Set.of(SCOPE_KEY, NODE_KEY);
    private static final Set<String> SCOPE_FIELDS = Set.of(TYPE_KEY, "id");

    private final Path file;
    private final Map<String, PlayerGroupDefinition> groups;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Yaml loader = FileYamlSupport.createLoader();
    private final Yaml dumper = FileYamlSupport.createDumper();

    public FilePlayerGroupRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.groups = loadGroups();
    }

    @Override
    public Optional<PlayerGroupDefinition> find(String id) {
        Objects.requireNonNull(id, "id");
        readLock.lock();
        try {
            return Optional.ofNullable(groups.get(id));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<PlayerGroupDefinition> findAll() {
        readLock.lock();
        try {
            return List.copyOf(groups.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PlayerGroupAddResult add(PlayerGroupDefinition group) {
        Objects.requireNonNull(group, "group");
        writeLock.lock();
        try {
            if (groups.containsKey(group.id())) {
                return PlayerGroupAddResult.ALREADY_EXISTS;
            }
            Map<String, PlayerGroupDefinition> updated = new LinkedHashMap<>(groups);
            updated.put(group.id(), group);
            persist(updated);
            replace(updated);
            return PlayerGroupAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupUpdateResult update(PlayerGroupDefinition group) {
        Objects.requireNonNull(group, "group");
        writeLock.lock();
        try {
            if (!groups.containsKey(group.id())) {
                return PlayerGroupUpdateResult.NOT_FOUND;
            }
            Map<String, PlayerGroupDefinition> updated = new LinkedHashMap<>(groups);
            updated.put(group.id(), group);
            persist(updated);
            replace(updated);
            return PlayerGroupUpdateResult.UPDATED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupRemoveResult remove(String id) {
        Objects.requireNonNull(id, "id");
        writeLock.lock();
        try {
            if (!groups.containsKey(id)) {
                return PlayerGroupRemoveResult.NOT_FOUND;
            }
            Map<String, PlayerGroupDefinition> updated = new LinkedHashMap<>(groups);
            updated.remove(id);
            persist(updated);
            replace(updated);
            return PlayerGroupRemoveResult.REMOVED;
        } finally {
            writeLock.unlock();
        }
    }

    private Map<String, PlayerGroupDefinition> loadGroups() throws IOException {
        Path temporaryFile = FileYamlSupport.temporaryFile(file);
        if (Files.exists(file)) {
            requireRegularFile(file, LABEL);
            Map<String, PlayerGroupDefinition> loaded = parseGroups(file);
            FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
            return loaded;
        }
        if (!Files.exists(temporaryFile)) {
            return new LinkedHashMap<>();
        }
        requireRegularFile(temporaryFile, LABEL);
        Map<String, PlayerGroupDefinition> recovered = parseGroups(temporaryFile);
        FileYamlSupport.moveReplacing(temporaryFile, file);
        return recovered;
    }

    private Map<String, PlayerGroupDefinition> parseGroups(Path source) throws IOException {
        Map<?, ?> root = FileYamlSupport.requireMap(
                FileYamlSupport.loadDocument(loader, source, LABEL),
                "root"
        );
        FileYamlSupport.requireOnlyFields(root, "root", ROOT_FIELDS);
        BigInteger version = FileYamlSupport.requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(version)) {
            throw FileYamlSupport.invalidFile("Unsupported player groups schema version: " + version);
        }

        List<?> serializedGroups = FileYamlSupport.requireList(root.get(GROUPS_KEY), GROUPS_KEY);
        Map<String, PlayerGroupDefinition> loaded = new LinkedHashMap<>();
        for (int index = 0; index < serializedGroups.size(); index++) {
            String path = GROUPS_KEY + "[" + index + "]";
            Map<?, ?> serialized = FileYamlSupport.requireMap(serializedGroups.get(index), path);
            FileYamlSupport.requireOnlyFields(serialized, path, GROUP_FIELDS);
            String id = FileYamlSupport.requireString(serialized.get(ID_KEY), path + "." + ID_KEY);
            List<AccessScope> accessGrants = deserializeAccessGrants(
                    FileYamlSupport.requireList(serialized.get(ACCESS_GRANTS_KEY), path + "." + ACCESS_GRANTS_KEY),
                    path + "." + ACCESS_GRANTS_KEY
            );
            List<PermissionGrant> permissions = deserializePermissions(
                    FileYamlSupport.requireList(serialized.get(PERMISSIONS_KEY), path + "." + PERMISSIONS_KEY),
                    path + "." + PERMISSIONS_KEY
            );
            PlayerGroupDefinition group;
            try {
                group = new PlayerGroupDefinition(id, accessGrants, permissions);
            } catch (RuntimeException exception) {
                throw FileYamlSupport.invalidFile("Invalid player group at " + path + ": " + exception.getMessage(), exception);
            }
            if (loaded.putIfAbsent(group.id(), group) != null) {
                throw FileYamlSupport.invalidFile("Duplicate player group at " + path + ": " + group.id());
            }
        }
        return loaded;
    }

    private List<AccessScope> deserializeAccessGrants(List<?> values, String path) throws IOException {
        List<AccessScope> scopes = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            scopes.add(deserializeScope(
                    FileYamlSupport.requireMap(values.get(index), path + "[" + index + "]"),
                    path + "[" + index + "]"
            ));
        }
        return scopes;
    }

    private List<PermissionGrant> deserializePermissions(List<?> values, String path) throws IOException {
        List<PermissionGrant> permissions = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String permissionPath = path + "[" + index + "]";
            Map<?, ?> serialized = FileYamlSupport.requireMap(values.get(index), permissionPath);
            FileYamlSupport.requireOnlyFields(serialized, permissionPath, PERMISSION_FIELDS);
            AccessScope scope = deserializeScope(
                    FileYamlSupport.requireMap(serialized.get(SCOPE_KEY), permissionPath + "." + SCOPE_KEY),
                    permissionPath + "." + SCOPE_KEY
            );
            String node = FileYamlSupport.requireString(serialized.get(NODE_KEY), permissionPath + "." + NODE_KEY);
            try {
                permissions.add(new PermissionGrant(scope, node));
            } catch (RuntimeException exception) {
                throw FileYamlSupport.invalidFile("Invalid permission at " + permissionPath + ": " + exception.getMessage(), exception);
            }
        }
        return permissions;
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
                if (serialized.containsKey("id")) {
                    throw FileYamlSupport.invalidFile("NETWORK scope must not have an id at " + path);
                }
                return AccessScope.network();
            }
            String id = FileYamlSupport.requireString(serialized.get("id"), path + ".id");
            return type == AccessScopeType.SERVER_GROUP
                    ? AccessScope.serverGroup(id)
                    : AccessScope.server(id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw FileYamlSupport.invalidFile("Invalid access scope at " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void persist(Map<String, PlayerGroupDefinition> snapshot) {
        try {
            FileYamlSupport.writeAtomically(file, serialize(snapshot));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist player groups to " + file, exception);
        }
    }

    private String serialize(Map<String, PlayerGroupDefinition> snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        List<Map<String, Object>> serializedGroups = new ArrayList<>(snapshot.size());
        for (PlayerGroupDefinition group : snapshot.values()) {
            Map<String, Object> serializedGroup = new LinkedHashMap<>();
            serializedGroup.put(ID_KEY, group.id());
            serializedGroup.put(ACCESS_GRANTS_KEY, group.accessGrants().stream().map(this::serializeScope).toList());
            serializedGroup.put(PERMISSIONS_KEY, group.permissions().stream().map(this::serializePermission).toList());
            serializedGroups.add(serializedGroup);
        }
        root.put(GROUPS_KEY, serializedGroups);
        return dumper.dump(root);
    }

    private Map<String, Object> serializePermission(PermissionGrant grant) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put(SCOPE_KEY, serializeScope(grant.scope()));
        serialized.put(NODE_KEY, grant.node());
        return serialized;
    }

    private Map<String, Object> serializeScope(AccessScope scope) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put(TYPE_KEY, scope.type().name());
        scope.id().ifPresent(id -> serialized.put("id", id));
        return serialized;
    }

    private void replace(Map<String, PlayerGroupDefinition> updated) {
        groups.clear();
        groups.putAll(updated);
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(label + " path is not a regular file: " + path);
        }
    }
}
