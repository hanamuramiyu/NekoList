package hanamuramiyu.monban.storage.file.whitelist;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.storage.file.internal.PlayerIdentityYamlCodec;
import hanamuramiyu.monban.whitelist.WhitelistAddResult;
import hanamuramiyu.monban.whitelist.WhitelistRemoveResult;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import hanamuramiyu.monban.whitelist.WhitelistUpdateResult;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
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

public final class FileWhitelistRepository implements WhitelistRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String ENTRIES_KEY = "entries";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, ENTRIES_KEY);

    private final Path file;
    private final Map<PlayerIdentity, PlayerIdentity> entries;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Yaml loader;
    private final Yaml dumper;
    private final PlayerIdentityYamlCodec identityCodec;

    public FileWhitelistRepository(Path file) throws IOException {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.loader = createLoader();
        this.dumper = createDumper();
        this.identityCodec = new PlayerIdentityYamlCodec();
        this.entries = loadEntries();
    }

    @Override
    public Optional<PlayerIdentity> find(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        readLock.lock();
        try {
            return Optional.ofNullable(entries.get(identity));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<PlayerIdentity> findAll() {
        readLock.lock();
        try {
            return List.copyOf(entries.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public WhitelistAddResult add(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        writeLock.lock();
        try {
            if (entries.containsKey(identity)) {
                return WhitelistAddResult.ALREADY_EXISTS;
            }

            Map<PlayerIdentity, PlayerIdentity> updatedEntries = copyEntries();
            updatedEntries.put(identity, identity);
            persist(updatedEntries);
            replaceEntries(updatedEntries);
            return WhitelistAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public WhitelistRemoveResult remove(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        writeLock.lock();
        try {
            if (!entries.containsKey(identity)) {
                return WhitelistRemoveResult.NOT_FOUND;
            }

            Map<PlayerIdentity, PlayerIdentity> updatedEntries = copyEntries();
            updatedEntries.remove(identity);
            persist(updatedEntries);
            replaceEntries(updatedEntries);
            return WhitelistRemoveResult.REMOVED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public WhitelistUpdateResult update(PlayerIdentity currentIdentity, PlayerIdentity updatedIdentity) {
        Objects.requireNonNull(currentIdentity, "currentIdentity");
        Objects.requireNonNull(updatedIdentity, "updatedIdentity");

        if (currentIdentity.type() != updatedIdentity.type()) {
            return WhitelistUpdateResult.IDENTITY_TYPE_MISMATCH;
        }

        writeLock.lock();
        try {
            PlayerIdentity existing = entries.get(currentIdentity);
            if (existing == null) {
                return WhitelistUpdateResult.NOT_FOUND;
            }

            if (!currentIdentity.sameIdentityAs(updatedIdentity) && entries.containsKey(updatedIdentity)) {
                return WhitelistUpdateResult.ALREADY_EXISTS;
            }

            Map<PlayerIdentity, PlayerIdentity> updatedEntries = copyEntries();
            updatedEntries.remove(currentIdentity);
            updatedEntries.put(updatedIdentity, updatedIdentity);
            persist(updatedEntries);
            replaceEntries(updatedEntries);
            return WhitelistUpdateResult.UPDATED;
        } finally {
            writeLock.unlock();
        }
    }

    private Map<PlayerIdentity, PlayerIdentity> loadEntries() throws IOException {
        Path temporaryFile = temporaryFile();

        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("Whitelist path is not a regular file: " + file);
            }

            Map<PlayerIdentity, PlayerIdentity> loadedEntries = parseEntries(file);
            deleteStaleTemporaryFile(temporaryFile);
            return loadedEntries;
        }

        if (!Files.exists(temporaryFile)) {
            return new LinkedHashMap<>();
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new IOException("Whitelist temporary path is not a regular file: " + temporaryFile);
        }

        Map<PlayerIdentity, PlayerIdentity> recoveredEntries = parseEntries(temporaryFile);
        moveReplacing(temporaryFile, file);
        return recoveredEntries;
    }

    private Map<PlayerIdentity, PlayerIdentity> parseEntries(Path source) throws IOException {
        Object document;
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            Iterator<Object> documents = loader.loadAll(reader).iterator();
            if (!documents.hasNext()) {
                throw invalidFile("Whitelist file is empty");
            }

            document = documents.next();
            if (documents.hasNext()) {
                throw invalidFile("Whitelist file must contain exactly one YAML document");
            }
        } catch (YAMLException exception) {
            throw invalidFile("Whitelist file contains invalid YAML", exception);
        }

        Map<?, ?> root = requireMap(document, "root");
        requireOnlyFields(root, "root", ROOT_FIELDS);

        BigInteger schemaVersion = requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (!BigInteger.valueOf(SCHEMA_VERSION).equals(schemaVersion)) {
            throw invalidFile("Unsupported whitelist schema version: " + schemaVersion);
        }

        List<?> serializedEntries = requireList(root.get(ENTRIES_KEY), ENTRIES_KEY);
        Map<PlayerIdentity, PlayerIdentity> loadedEntries = new LinkedHashMap<>();

        for (int index = 0; index < serializedEntries.size(); index++) {
            Map<?, ?> serializedEntry = requireMap(serializedEntries.get(index), ENTRIES_KEY + "[" + index + "]");
            PlayerIdentity identity = identityCodec.decode(serializedEntry, ENTRIES_KEY + "[" + index + "]");
            if (loadedEntries.putIfAbsent(identity, identity) != null) {
                throw invalidFile("Duplicate whitelist identity at entries[" + index + "]: " + identity.name());
            }
        }

        return loadedEntries;
    }

    private void deleteStaleTemporaryFile(Path temporaryFile) throws IOException {
        if (!Files.exists(temporaryFile)) {
            return;
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new IOException("Whitelist temporary path is not a regular file: " + temporaryFile);
        }
        Files.delete(temporaryFile);
    }

    private void persist(Map<PlayerIdentity, PlayerIdentity> snapshot) {
        try {
            writeSnapshot(snapshot);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist whitelist to " + file, exception);
        }
    }

    private void writeSnapshot(Map<PlayerIdentity, PlayerIdentity> snapshot) throws IOException {
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

    private String serialize(Map<PlayerIdentity, PlayerIdentity> snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);

        List<Map<String, Object>> serializedEntries = new ArrayList<>(snapshot.size());
        for (PlayerIdentity identity : snapshot.values()) {
            serializedEntries.add(identityCodec.encode(identity));
        }

        root.put(ENTRIES_KEY, serializedEntries);
        return dumper.dump(root);
    }

    private Map<PlayerIdentity, PlayerIdentity> copyEntries() {
        return new LinkedHashMap<>(entries);
    }

    private void replaceEntries(Map<PlayerIdentity, PlayerIdentity> updatedEntries) {
        entries.clear();
        entries.putAll(updatedEntries);
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
}
