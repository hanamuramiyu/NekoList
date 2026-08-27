package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSyncSecretLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingFileDisablesSync() throws IOException {
        assertTrue(new FileSyncSecretLoader(tempDirectory.resolve("sync.yml")).load().isEmpty());
    }

    @Test
    void createsSecretWhenRequested() throws IOException {
        Path file = tempDirectory.resolve("sync.yml");

        SyncSecret secret = new FileSyncSecretLoader(file).loadOrCreate();

        assertTrue(Files.isRegularFile(file));
        assertEquals(secret, new FileSyncSecretLoader(file).load().orElseThrow());
        assertEquals(secret, new FileSyncSecretLoader(file).loadOrCreate());
    }

    @Test
    void loadsBase64Secret() throws IOException {
        String encoded = Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        );
        Path file = write("schema-version: 1\nsecret: " + encoded + "\n");

        assertTrue(new FileSyncSecretLoader(file).load().isPresent());
    }

    @Test
    void rejectsInvalidSecret() throws IOException {
        Path file = write("schema-version: 1\nsecret: short\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    @Test
    void rejectsUnknownFields() throws IOException {
        Path file = write("schema-version: 1\nsecret: bW9uYmFuLXN5bmMtc2VjcmV0\nextra: true\n");

        assertThrows(IOException.class, () -> new FileSyncSecretLoader(file).load());
    }

    private Path write(String content) throws IOException {
        Path file = tempDirectory.resolve("sync.yml");
        Files.writeString(file, content);
        return file;
    }
}
