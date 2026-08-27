package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerGroupAdministrationServiceTest {
    @Test
    void managesGroupsAssignmentsAndDirectPermissions() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        InMemoryPlayerPermissionGrantRepository permissions = new InMemoryPlayerPermissionGrantRepository();
        PlayerGroupAdministrationService service = new PlayerGroupAdministrationService(
                groups,
                assignments,
                permissions,
                scope -> {
                }
        );
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        PermissionGrant directPermission = PermissionGrant.server("survival", "coreprotect.inspect");

        assertEquals(PlayerGroupAddResult.ADDED, service.createGroup("moderator"));
        assertEquals(PlayerGroupEntryResult.ADDED, service.grantAccess("moderator", AccessScope.network()));
        assertEquals(
                PlayerGroupEntryResult.ADDED,
                service.grantPermission("moderator", PermissionGrant.network("proxy.command"))
        );
        assertEquals(
                PlayerGroupEntryResult.ADDED,
                service.addAssignment(identity, "moderator")
        );
        assertEquals(
                PermissionGrantAddResult.ADDED,
                service.addDirectPermission(identity, directPermission)
        );

        PlayerGroupDefinition group = groups.find("moderator").orElseThrow();
        assertEquals(List.of(AccessScope.network()), group.accessGrants());
        assertEquals(List.of(PermissionGrant.network("proxy.command")), group.permissions());
        assertEquals(List.of(new PlayerGroupAssignment(identity, "moderator")), assignments.findAll());
        assertEquals(List.of(new PlayerPermissionGrant(identity, directPermission)), permissions.findAll());

        assertEquals(PlayerGroupRemoveResult.REMOVED, service.removeGroup("moderator"));
        assertTrue(groups.find("moderator").isEmpty());
        assertTrue(assignments.findAll().isEmpty());
    }
}
