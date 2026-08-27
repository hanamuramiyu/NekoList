package hanamuramiyu.monban.storage.file.internal;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FileYamlSupport {
    private FileYamlSupport() {
    }

    public static Yaml createLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        return new Yaml(new SafeConstructor(options));
    }

    public static Yaml createDumper() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    public static Object loadDocument(Yaml loader, Path source, String label) throws IOException {
        String yamlText = Files.readString(source, StandardCharsets.UTF_8);
        rejectAliases(loader, yamlText, label);

        try {
            Iterator<Object> documents = loader.loadAll(yamlText).iterator();
            if (!documents.hasNext()) {
                throw invalidFile(label + " file is empty");
            }
            Object document = documents.next();
            if (documents.hasNext()) {
                throw invalidFile(label + " file must contain exactly one YAML document");
            }
            return document;
        } catch (YAMLException exception) {
            throw invalidFile(label + " file contains invalid YAML", exception);
        }
    }

    public static void writeAtomically(Path file, String contents) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);

        Path temporaryFile = temporaryFile(file);
        byte[] serialized = contents.getBytes(StandardCharsets.UTF_8);
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

    public static Path temporaryFile(Path file) {
        return file.resolveSibling(file.getFileName() + ".tmp");
    }

    public static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteStaleTemporaryFile(Path temporaryFile, String label) throws IOException {
        if (!Files.exists(temporaryFile)) {
            return;
        }
        if (!Files.isRegularFile(temporaryFile)) {
            throw new IOException(label + " temporary path is not a regular file: " + temporaryFile);
        }
        Files.delete(temporaryFile);
    }

    public static Map<?, ?> requireMap(Object value, String path) throws IOException {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw invalidFile("Expected mapping at " + path);
    }

    public static List<?> requireList(Object value, String path) throws IOException {
        if (value instanceof List<?> list) {
            return list;
        }
        throw invalidFile("Expected list at " + path);
    }

    public static String requireString(Object value, String path) throws IOException {
        if (value instanceof String string) {
            return string;
        }
        throw invalidFile("Expected string at " + path);
    }

    public static BigInteger requireInteger(Object value, String path) throws IOException {
        if (value instanceof Number number) {
            try {
                return new BigInteger(number.toString());
            } catch (NumberFormatException exception) {
                throw invalidFile("Expected integer at " + path, exception);
            }
        }
        throw invalidFile("Expected integer at " + path);
    }

    public static void requireOnlyFields(Map<?, ?> map, String path, Set<String> allowedFields) throws IOException {
        for (Object field : map.keySet()) {
            if (!(field instanceof String fieldName)) {
                throw invalidFile("Expected string field name at " + path);
            }
            if (!allowedFields.contains(fieldName)) {
                throw invalidFile("Unknown field at " + path + "." + fieldName);
            }
        }
    }

    public static IOException invalidFile(String message) {
        return new IOException(message);
    }

    public static IOException invalidFile(String message, Throwable cause) {
        return new IOException(message, cause);
    }

    private static void rejectAliases(Yaml loader, String yamlText, String label) throws IOException {
        try {
            for (Event event : loader.parse(new StringReader(yamlText))) {
                if (event instanceof AliasEvent) {
                    throw invalidFile(label + " file does not support YAML aliases");
                }
            }
        } catch (YAMLException exception) {
            throw invalidFile(label + " file contains invalid YAML", exception);
        }
    }
}
