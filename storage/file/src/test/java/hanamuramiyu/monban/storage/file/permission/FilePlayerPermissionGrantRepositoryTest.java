package hanamuramiyu.monban.storage.file.permission;

import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PermissionGrantRemoveResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilePlayerPermissionGrantRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsDirectPermissionsWithAllScopes() throws IOException {
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        PlayerPermissionGrant network = new PlayerPermissionGrant(identity, PermissionGrant.network("proxy.command"));
        PlayerPermissionGrant group = new PlayerPermissionGrant(
                identity,
                PermissionGrant.serverGroup("public", "essentials.fly")
        );
        PlayerPermissionGrant server = new PlayerPermissionGrant(
                identity,
                PermissionGrant.server("survival", "coreprotect.inspect")
        );
        FilePlayerPermissionGrantRepository repository = new FilePlayerPermissionGrantRepository(file());

        assertEquals(PermissionGrantAddResult.ADDED, repository.add(network));
        assertEquals(PermissionGrantAddResult.ADDED, repository.add(group));
        assertEquals(PermissionGrantAddResult.ADDED, repository.add(server));
        assertEquals(PermissionGrantAddResult.ALREADY_EXISTS, repository.add(network));
        assertEquals(List.of(network, group, server), new FilePlayerPermissionGrantRepository(file()).findAll());
        assertEquals(List.of(group), repository.findAll(identity).stream()
                .filter(grant -> grant.grant().scope().id().orElse("").equals("public"))
                .toList());
        assertEquals(PermissionGrantRemoveResult.REMOVED, repository.remove(group));
        assertEquals(PermissionGrantRemoveResult.NOT_FOUND, repository.remove(group));
    }

    @Test
    void rejectsMalformedPermissionScope() throws IOException {
        java.nio.file.Files.writeString(
                file(),
                "schema-version: 1\npermissions:\n"
                        + "  - identity: {type: OFFLINE, name: Miyu}\n"
                        + "    scope: {type: NETWORK, id: invalid}\n"
                        + "    node: proxy.command\n"
        );

        assertThrows(IOException.class, () -> new FilePlayerPermissionGrantRepository(file()));
    }

    private Path file() {
        return temporaryDirectory.resolve("player-permissions.yml");
    }
}
