package hanamuramiyu.monban.storage.file.group;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
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

public final class FilePlayerGroupAssignmentRepository implements PlayerGroupAssignmentRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String LABEL = "group assignments";
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String ASSIGNMENTS_KEY = "assignments";
    private static final String IDENTITY_KEY = "identity";
    private static final String GROUP_KEY = "group";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, ASSIGNMENTS_KEY);
    private static final Set<String> ASSIGNMENT_FIELDS = Set.of(IDENTITY_KEY, GROUP_KEY);

    private final Path file;
    private final Map<AssignmentKey, PlayerGroupAssignment> assignments;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Yaml loader = FileYamlSupport.createLoader();
    private final Yaml dumper = FileYamlSupport.createDumper();
    private final PlayerIdentityYamlCodec identityCodec = new PlayerIdentityYamlCodec();

    public FilePlayerGroupAssignmentRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.assignments = loadAssignments();
    }

    @Override
    public List<PlayerGroupAssignment> findAll() {
        readLock.lock();
        try {
            return List.copyOf(assignments.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PlayerGroupAddResult add(PlayerGroupAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        AssignmentKey key = new AssignmentKey(assignment);
        writeLock.lock();
        try {
            if (assignments.containsKey(key)) {
                return PlayerGroupAddResult.ALREADY_EXISTS;
            }
            Map<AssignmentKey, PlayerGroupAssignment> updated = new LinkedHashMap<>(assignments);
            updated.put(key, assignment);
            persist(updated);
            replace(updated);
            return PlayerGroupAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupRemoveResult remove(PlayerGroupAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        AssignmentKey key = new AssignmentKey(assignment);
        writeLock.lock();
        try {
            if (!assignments.containsKey(key)) {
                return PlayerGroupRemoveResult.NOT_FOUND;
            }
            Map<AssignmentKey, PlayerGroupAssignment> updated = new LinkedHashMap<>(assignments);
            updated.remove(key);
            persist(updated);
            replace(updated);
            return PlayerGroupRemoveResult.REMOVED;
        } finally {
            writeLock.unlock();
        }
    }

    private Map<AssignmentKey, PlayerGroupAssignment> loadAssignments() throws IOException {
        Path temporaryFile = FileYamlSupport.temporaryFile(file);
        if (Files.exists(file)) {
            requireRegularFile(file);
            Map<AssignmentKey, PlayerGroupAssignment> loaded = parseAssignments(file);
            FileYamlSupport.deleteStaleTemporaryFile(temporaryFile, LABEL);
            return loaded;
        }
        if (!Files.exists(temporaryFile)) {
            return new LinkedHashMap<>();
        }
        requireRegularFile(temporaryFile);
        Map<AssignmentKey, PlayerGroupAssignment> recovered = parseAssignments(temporaryFile);
        FileYamlSupport.moveReplacing(temporaryFile, file);
        return recovered;
    }

    private Map<AssignmentKey, PlayerGroupAssignment> parseAssignments(Path source) throws IOException {
        Map<?, ?> root = FileYamlSupport.requireMap(
                FileYamlSupport.loadDocument(loader, source, LABEL),
                "root"
        );
        FileYamlSupport.requireOnlyFields(root, "root", ROOT_FIELDS);
        BigInteger version = FileYamlSupport.requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(version)) {
            throw FileYamlSupport.invalidFile("Unsupported group assignments schema version: " + version);
        }

        List<?> serializedAssignments = FileYamlSupport.requireList(root.get(ASSIGNMENTS_KEY), ASSIGNMENTS_KEY);
        Map<AssignmentKey, PlayerGroupAssignment> loaded = new LinkedHashMap<>();
        for (int index = 0; index < serializedAssignments.size(); index++) {
            String path = ASSIGNMENTS_KEY + "[" + index + "]";
            Map<?, ?> serialized = FileYamlSupport.requireMap(serializedAssignments.get(index), path);
            FileYamlSupport.requireOnlyFields(serialized, path, ASSIGNMENT_FIELDS);
            PlayerIdentity identity = identityCodec.decode(
                    FileYamlSupport.requireMap(serialized.get(IDENTITY_KEY), path + "." + IDENTITY_KEY),
                    path + "." + IDENTITY_KEY
            );
            String groupId = FileYamlSupport.requireString(serialized.get(GROUP_KEY), path + "." + GROUP_KEY);
            PlayerGroupAssignment assignment;
            try {
                assignment = new PlayerGroupAssignment(identity, groupId);
            } catch (RuntimeException exception) {
                throw FileYamlSupport.invalidFile("Invalid group assignment at " + path + ": " + exception.getMessage(), exception);
            }
            if (loaded.putIfAbsent(new AssignmentKey(assignment), assignment) != null) {
                throw FileYamlSupport.invalidFile(
                        "Duplicate group assignment at " + path + ": " + identity.name() + " / " + groupId
                );
            }
        }
        return loaded;
    }

    private void persist(Map<AssignmentKey, PlayerGroupAssignment> snapshot) {
        try {
            FileYamlSupport.writeAtomically(file, serialize(snapshot));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist group assignments to " + file, exception);
        }
    }

    private String serialize(Map<AssignmentKey, PlayerGroupAssignment> snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        List<Map<String, Object>> serializedAssignments = new ArrayList<>(snapshot.size());
        for (PlayerGroupAssignment assignment : snapshot.values()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put(IDENTITY_KEY, identityCodec.encode(assignment.identity()));
            serialized.put(GROUP_KEY, assignment.groupId());
            serializedAssignments.add(serialized);
        }
        root.put(ASSIGNMENTS_KEY, serializedAssignments);
        return dumper.dump(root);
    }

    private void replace(Map<AssignmentKey, PlayerGroupAssignment> updated) {
        assignments.clear();
        assignments.putAll(updated);
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(LABEL + " path is not a regular file: " + path);
        }
    }

    private record AssignmentKey(PlayerIdentity identity, String groupId) {
        private AssignmentKey(PlayerGroupAssignment assignment) {
            this(assignment.identity(), assignment.groupId());
        }
    }
}
