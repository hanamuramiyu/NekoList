package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class FileServerGroupsConfigLoader {
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema-version";
    private static final String GROUPS_KEY = "groups";
    private static final String ID_KEY = "id";
    private static final String SERVERS_KEY = "servers";

    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_VERSION_KEY, GROUPS_KEY);
    private static final Set<String> GROUP_FIELDS = Set.of(ID_KEY, SERVERS_KEY);
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private final Path file;
    private final Yaml loader;

    public FileServerGroupsConfigLoader(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.loader = createLoader();
    }

    public ServerGroupCatalog load() throws IOException {
        if (!Files.exists(file)) {
            createDefaultFile();
        }

        Object document;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            document = loader.load(reader);
        } catch (YAMLException exception) {
            throw new IOException("Invalid YAML in " + file, exception);
        }

        Map<String, Object> root = requireStringMap(document, "root");
        rejectUnknownFields(root, ROOT_FIELDS, "root");

        int schemaVersion = requireInteger(root.get(SCHEMA_VERSION_KEY), SCHEMA_VERSION_KEY);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IOException("Unsupported schema-version at " + SCHEMA_VERSION_KEY + ": " + schemaVersion);
        }

        List<?> groupDocuments = requireList(root.get(GROUPS_KEY), GROUPS_KEY);
        List<ServerGroupDefinition> groups = new ArrayList<>(groupDocuments.size());
        Set<String> groupIds = new HashSet<>();
        Map<String, String> serverOwners = new HashMap<>();

        for (int index = 0; index < groupDocuments.size(); index++) {
            String groupPath = GROUPS_KEY + "[" + index + "]";
            Map<String, Object> group = requireStringMap(groupDocuments.get(index), groupPath);
            rejectUnknownFields(group, GROUP_FIELDS, groupPath);

            String id = requireString(group.get(ID_KEY), groupPath + "." + ID_KEY);
            if (!GROUP_ID_PATTERN.matcher(id).matches()) {
                throw new IOException(
                        "Invalid server group id at " + groupPath + "." + ID_KEY
                                + ": expected [a-z0-9][a-z0-9._-]{0,63}, got " + id
                );
            }
            if (!groupIds.add(id)) {
                throw new IOException("Duplicate server group id: " + id);
            }

            List<?> serverDocuments = requireList(group.get(SERVERS_KEY), groupPath + "." + SERVERS_KEY);
            List<String> servers = new ArrayList<>(serverDocuments.size());
            Set<String> groupServers = new HashSet<>();

            for (int serverIndex = 0; serverIndex < serverDocuments.size(); serverIndex++) {
                String serverPath = groupPath + "." + SERVERS_KEY + "[" + serverIndex + "]";
                String serverName = requireString(serverDocuments.get(serverIndex), serverPath);

                if (!groupServers.add(serverName)) {
                    throw new IOException("Duplicate server reference at " + serverPath + ": " + serverName);
                }

                String previousOwner = serverOwners.putIfAbsent(serverName, id);
                if (previousOwner != null) {
                    throw new IOException(
                            "Server " + serverName + " belongs to multiple groups: " + previousOwner + " and " + id
                    );
                }

                servers.add(serverName);
            }

            try {
                groups.add(new ServerGroupDefinition(id, servers));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IOException("Invalid server group at " + groupPath + ": " + exception.getMessage(), exception);
            }
        }

        try {
            return new ServerGroupCatalog(groups);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid server group catalog: " + exception.getMessage(), exception);
        }
    }

    private void createDefaultFile() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = Files.createTempFile(parent, file.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            byte[] bytes = "schema-version: 1\ngroups: []\n".getBytes(StandardCharsets.UTF_8);
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
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
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

    private static List<?> requireList(Object value, String path) throws IOException {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IOException("Expected list at " + path);
    }

    private static String requireString(Object value, String path) throws IOException {
        if (!(value instanceof String text)) {
            throw new IOException("Expected string at " + path);
        }
        if (text.isBlank()) {
            throw new IOException("Expected non-blank string at " + path);
        }
        if (!text.equals(text.strip())) {
            throw new IOException("Leading or trailing whitespace is not allowed at " + path + ": " + text);
        }
        return text;
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
}
