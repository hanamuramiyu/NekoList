package hanamuramiyu.monban.config.file;

import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServerGroupsConfigLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingConfigCreatesEmptyCatalog() throws IOException {
        Path file = tempDirectory.resolve("server-groups.yml");

        ServerGroupCatalog catalog = new FileServerGroupsConfigLoader(file).load();

        assertTrue(Files.isRegularFile(file));
        assertTrue(catalog.findAll().isEmpty());
        assertEquals("schema-version: 1\ngroups: []\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void loadsConfiguredGroupsWithoutNormalizingServerNames() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - LoBbY
                      - survival
                  - id: testing
                    servers:
                      - test-lobby
                """);

        ServerGroupCatalog catalog = new FileServerGroupsConfigLoader(file).load();

        assertEquals(List.of("LoBbY", "survival"), catalog.findById("public").orElseThrow().servers());
        assertEquals("testing", catalog.findForServer("test-lobby").orElseThrow().id());
    }

    @Test
    void rejectsUppercaseGroupIdInsteadOfNormalizingIt() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: Testing
                    servers: []
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsInvalidGroupIdCharactersAndLength() throws IOException {
        Path invalidCharacter = writeConfig("""
                schema-version: 1
                groups:
                  - id: test/group
                    servers: []
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(invalidCharacter).load());

        Path tooLong = writeConfig("""
                schema-version: 1
                groups:
                  - id: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                    servers: []
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(tooLong).load());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws IOException {
        Path file = writeConfig("""
                schema-version: 2
                groups: []
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsUnknownFields() throws IOException {
        Path rootField = writeConfig("""
                schema-version: 1
                groups: []
                extra: true
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(rootField).load());

        Path groupField = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers: []
                    extra: true
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(groupField).load());
    }

    @Test
    void rejectsDuplicateYamlKeys() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    id: testing
                    servers: []
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsWrongTypes() throws IOException {
        Path groupsNotList = writeConfig("""
                schema-version: 1
                groups: nope
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(groupsNotList).load());

        Path serversNotList = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers: lobby
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(serversNotList).load());
    }

    @Test
    void rejectsDuplicateGroupIds() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - lobby
                  - id: public
                    servers:
                      - survival
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsDuplicateServerWithinGroup() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - lobby
                      - lobby
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsServerInMultipleGroups() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - lobby
                  - id: testing
                    servers:
                      - lobby
                """);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());
    }

    @Test
    void rejectsBlankOrPaddedServerNames() throws IOException {
        Path blank = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - " "
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(blank).load());

        Path padded = writeConfig("""
                schema-version: 1
                groups:
                  - id: public
                    servers:
                      - " lobby "
                """);
        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(padded).load());
    }

    @Test
    void invalidExistingConfigIsNotReplacedWithEmptyDefaults() throws IOException {
        Path file = writeConfig("""
                schema-version: 1
                groups:
                  - id: Invalid
                    servers: []
                """);
        String original = Files.readString(file, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new FileServerGroupsConfigLoader(file).load());

        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    private Path writeConfig(String content) throws IOException {
        Path file = tempDirectory.resolve("server-groups.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
