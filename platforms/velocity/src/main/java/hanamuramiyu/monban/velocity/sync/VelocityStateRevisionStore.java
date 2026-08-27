package hanamuramiyu.monban.velocity.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

final class VelocityStateRevisionStore {
    private final Path file;

    VelocityStateRevisionStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    long load() {
        if (!Files.exists(file)) {
            return 0;
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IOException("State revision path is not a regular file: " + file);
            }
            long revision = Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).strip());
            if (revision < 0) {
                throw new IOException("State revision must not be negative: " + revision);
            }
            return revision;
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Failed to load state revision from " + file, exception);
        }
    }

    void save(long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("State revision must be positive.");
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, Long.toString(revision), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("Failed to persist state revision to " + file, exception);
        }
    }
}
