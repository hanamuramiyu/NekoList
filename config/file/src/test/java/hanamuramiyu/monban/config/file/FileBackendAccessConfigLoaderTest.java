package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.backend.BackendTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBackendAccessConfigLoaderTest {
    @TempDir Path tempDirectory;

    @Test void missingConfigCreatesOpenDefault() throws IOException {
        Path file = tempDirectory.resolve("backend-access.yml");
        BackendAccessPolicyCatalog catalog = new FileBackendAccessConfigLoader(file).load();
        assertEquals(BackendAccessMode.OPEN, catalog.defaultMode());
        assertEquals(0, catalog.explicitPolicyCount());
        assertEquals("schema-version: 1\ndefault: OPEN\npolicies: []\n", Files.readString(file));
    }

    @Test void loadsServerAndGroupPolicies() throws IOException {
        Path file = write("""
                schema-version: 1
                default: OPEN
                policies:
                  - scope:
                      type: SERVER_GROUP
                      id: testing
                    mode: GRANT_REQUIRED
                  - scope:
                      type: SERVER
                      id: lobby
                    mode: OPEN
                """);
        BackendAccessPolicyCatalog c = new FileBackendAccessConfigLoader(file).load();
        assertEquals(BackendAccessMode.GRANT_REQUIRED, c.effectiveMode(BackendTarget.grouped("test-lobby", "testing")));
        assertEquals(BackendAccessMode.OPEN, c.effectiveMode(BackendTarget.grouped("lobby", "testing")));
    }

    @Test void rejectsUnknownFields() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies: []\nextra: true\n"); }
    @Test void rejectsDuplicateKeys() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\ndefault: GRANT_REQUIRED\npolicies: []\n"); }
    @Test void rejectsAliases() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies:\n  - &p {scope: {type: SERVER, id: lobby}, mode: OPEN}\n  - *p\n"); }
    @Test void rejectsMultipleDocuments() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies: []\n---\nschema-version: 1\ndefault: OPEN\npolicies: []\n"); }
    @Test void rejectsWrongTypes() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies: {}\n"); }
    @Test void rejectsUnknownMode() throws IOException { assertInvalid("schema-version: 1\ndefault: MAYBE\npolicies: []\n"); }
    @Test void rejectsNetworkPolicy() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies:\n  - scope: {type: NETWORK, id: no}\n    mode: GRANT_REQUIRED\n"); }
    @Test void rejectsDuplicatePolicy() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies:\n  - scope: {type: SERVER, id: lobby}\n    mode: OPEN\n  - scope: {type: SERVER, id: lobby}\n    mode: GRANT_REQUIRED\n"); }
    @Test void rejectsMissingScopeId() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies:\n  - scope: {type: SERVER}\n    mode: OPEN\n"); }
    @Test void rejectsUppercaseGroupId() throws IOException { assertInvalid("schema-version: 1\ndefault: OPEN\npolicies:\n  - scope: {type: SERVER_GROUP, id: Testing}\n    mode: OPEN\n"); }
    @Test void rejectsUnsupportedSchema() throws IOException { assertInvalid("schema-version: 2\ndefault: OPEN\npolicies: []\n"); }
    @Test void rejectsFractionalSchema() throws IOException { assertInvalid("schema-version: 1.5\ndefault: OPEN\npolicies: []\n"); }

    private Path write(String text) throws IOException {
        Path file = tempDirectory.resolve("backend-access.yml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }
    private void assertInvalid(String text) throws IOException {
        Path file = write(text);
        assertThrows(IOException.class, () -> new FileBackendAccessConfigLoader(file).load());
        assertTrue(Files.exists(file));
    }
}
