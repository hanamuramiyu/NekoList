package hanamuramiyu.monban.storage.file.group;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePlayerGroupRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsGroupsWithAllScopesAndPermissions() throws IOException {
        Path file = groupsFile();
        PlayerGroupDefinition group = new PlayerGroupDefinition(
                "moderator",
                List.of(AccessScope.network(), AccessScope.serverGroup("public"), AccessScope.server("staff")),
                List.of(
                        PermissionGrant.network("proxy.command"),
                        PermissionGrant.serverGroup("public", "essentials.fly"),
                        PermissionGrant.server("survival", "coreprotect.inspect")
                )
        );
        FilePlayerGroupRepository repository = new FilePlayerGroupRepository(file);

        assertEquals(PlayerGroupAddResult.ADDED, repository.add(group));
        assertEquals(PlayerGroupAddResult.ALREADY_EXISTS, repository.add(group));
        assertEquals(List.of(group), new FilePlayerGroupRepository(file).findAll());
        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(temporaryFile()));
        assertEquals(PlayerGroupRemoveResult.REMOVED, repository.remove("moderator"));
        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, repository.remove("moderator"));
    }

    @Test
    void createsEmptyVersionedFileOnFirstMutation() throws IOException {
        FilePlayerGroupRepository repository = new FilePlayerGroupRepository(groupsFile());
        repository.add(new PlayerGroupDefinition("default", List.of(), List.of()));

        String contents = Files.readString(groupsFile(), StandardCharsets.UTF_8);
        assertTrue(contents.startsWith("schema-version: 1\n"));
        assertTrue(contents.contains("groups:"));
    }

    @Test
    void rejectsUnknownFieldsAndUnsupportedVersions() throws IOException {
        Files.writeString(groupsFile(), "schema-version: 2\ngroups: []\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new FilePlayerGroupRepository(groupsFile()));

        Files.writeString(groupsFile(), "schema-version: 1\ngroups: []\nextra: true\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new FilePlayerGroupRepository(groupsFile()));
    }

    @Test
    void recoversValidTemporaryFileWhenPrimaryIsMissing() throws IOException {
        Files.writeString(
                temporaryFile(),
                "schema-version: 1\ngroups:\n  - id: default\n    access-grants: []\n    permissions: []\n",
                StandardCharsets.UTF_8
        );

        FilePlayerGroupRepository repository = new FilePlayerGroupRepository(groupsFile());

        assertEquals("default", repository.findAll().getFirst().id());
        assertTrue(Files.exists(groupsFile()));
        assertFalse(Files.exists(temporaryFile()));
    }

    private Path groupsFile() {
        return temporaryDirectory.resolve("player-groups.yml");
    }

    private Path temporaryFile() {
        return temporaryDirectory.resolve("player-groups.yml.tmp");
    }
}
