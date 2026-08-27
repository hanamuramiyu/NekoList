package hanamuramiyu.monban.access.effective;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAccessResolverTest {
    private static final PlayerIdentity IDENTITY = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    @Test
    void combinesDirectAndGroupAccessAndPermissionsWithOrigins() {
        InMemoryAccessGrantRepository directAccess = new InMemoryAccessGrantRepository();
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        InMemoryPlayerPermissionGrantRepository directPermissions = new InMemoryPlayerPermissionGrantRepository();

        PermissionGrant inspect = PermissionGrant.server("survival", "coreprotect.inspect");
        groups.add(new PlayerGroupDefinition(
                "default",
                List.of(AccessScope.network()),
                List.of(PermissionGrant.network("someproxy.command"))
        ));
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(AccessScope.serverGroup("public"), AccessScope.server("staff")),
                List.of(inspect, PermissionGrant.server("survival", "coreprotect.lookup"))
        ));
        assignments.add(new PlayerGroupAssignment(IDENTITY, "default"));
        assignments.add(new PlayerGroupAssignment(IDENTITY, "moderator"));
        directAccess.add(new AccessGrant(AccessScope.server("staff"), IDENTITY));
        directPermissions.add(new PlayerPermissionGrant(IDENTITY, inspect));
        directPermissions.add(new PlayerPermissionGrant(
                IDENTITY,
                PermissionGrant.server("survival", "example.permission")
        ));

        PlayerAccessSnapshot snapshot = new PlayerAccessResolver(
                directAccess,
                groups,
                assignments,
                directPermissions
        ).resolve(IDENTITY);

        assertEquals(List.of("default", "moderator"), snapshot.groups().stream().map(PlayerGroupDefinition::id).toList());
        assertTrue(snapshot.hasAccess(AccessScope.network()));
        assertTrue(snapshot.hasAccess(AccessScope.serverGroup("public")));
        assertTrue(snapshot.hasAccess(AccessScope.server("staff")));
        assertFalse(snapshot.hasAccess(AccessScope.server("survival")));

        EffectiveAccess staffAccess = snapshot.accesses().stream()
                .filter(access -> access.scope().equals(AccessScope.server("staff")))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(AccessOrigin.direct(), AccessOrigin.group("moderator")),
                staffAccess.origins()
        );

        EffectivePermission inspectPermission = snapshot.permissions(AccessScope.server("survival")).stream()
                .filter(permission -> permission.grant().node().equals("coreprotect.inspect"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(AccessOrigin.direct(), AccessOrigin.group("moderator")), inspectPermission.origins());
        assertTrue(snapshot.hasPermission(AccessScope.server("survival"), "coreprotect.lookup"));
        assertTrue(snapshot.hasPermission(AccessScope.server("survival"), "example.permission"));
        assertFalse(snapshot.hasPermission(AccessScope.server("survival"), "essentials.fly"));
    }

    @Test
    void ignoresAssignmentsToUnknownGroups() {
        InMemoryAccessGrantRepository directAccess = new InMemoryAccessGrantRepository();
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        InMemoryPlayerPermissionGrantRepository directPermissions = new InMemoryPlayerPermissionGrantRepository();
        assignments.add(new PlayerGroupAssignment(IDENTITY, "removed"));

        PlayerAccessSnapshot snapshot = new PlayerAccessResolver(
                directAccess,
                groups,
                assignments,
                directPermissions
        ).resolve(IDENTITY);

        assertTrue(snapshot.groups().isEmpty());
        assertTrue(snapshot.accesses().isEmpty());
        assertTrue(snapshot.permissions().isEmpty());
    }
}
