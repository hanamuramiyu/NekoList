package hanamuramiyu.monban.storage.file.group;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilePlayerGroupAssignmentRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsAssignmentsForOnlineAndOfflineIdentities() throws IOException {
        PlayerGroupAssignment online = new PlayerGroupAssignment(
                PlayerIdentity.online("Miyu", UUID.fromString("00000000-0000-0000-0000-000000000001")),
                "moderator"
        );
        PlayerGroupAssignment offline = new PlayerGroupAssignment(
                PlayerIdentity.offline("hanamuramiyu"),
                "default"
        );
        FilePlayerGroupAssignmentRepository repository = new FilePlayerGroupAssignmentRepository(file());

        assertEquals(PlayerGroupAddResult.ADDED, repository.add(online));
        assertEquals(PlayerGroupAddResult.ADDED, repository.add(offline));
        assertEquals(PlayerGroupAddResult.ALREADY_EXISTS, repository.add(online));
        assertEquals(List.of(online, offline), new FilePlayerGroupAssignmentRepository(file()).findAll());
        assertEquals(PlayerGroupRemoveResult.REMOVED, repository.remove(online));
        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, repository.remove(online));
    }

    private Path file() {
        return temporaryDirectory.resolve("group-assignments.yml");
    }
}
