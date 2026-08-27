package hanamuramiyu.monban.config.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileBackendServerConfigLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingFileDoesNotEnableBackendScopedPermissions() throws IOException {
        assertTrue(new FileBackendServerConfigLoader(tempDirectory.resolve("backend.yml")).load().isEmpty());
    }

    @Test
    void loadsServerName() throws IOException {
        Path file = write("schema-version: 1\nserver-name: survival\n");

        assertEquals("survival", new FileBackendServerConfigLoader(file).load().orElseThrow());
    }

    @Test
    void rejectsUnknownFields() throws IOException {
        Path file = write("schema-version: 1\nserver-name: survival\nextra: true\n");

        assertThrows(IOException.class, () -> new FileBackendServerConfigLoader(file).load());
    }

    @Test
    void rejectsInvalidServerName() throws IOException {
        Path file = write("schema-version: 1\nserver-name: ' survival '\n");

        assertThrows(IOException.class, () -> new FileBackendServerConfigLoader(file).load());
    }

    private Path write(String content) throws IOException {
        Path file = tempDirectory.resolve("backend.yml");
        Files.writeString(file, content);
        return file;
    }
}
