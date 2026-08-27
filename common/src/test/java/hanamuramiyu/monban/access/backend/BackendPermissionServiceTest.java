package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPermissionServiceTest {
    private static final PlayerIdentity IDENTITY = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    @Test
    void resolvesNetworkServerGroupAndServerPermissions() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(),
                List.of(
                        PermissionGrant.network("network.node"),
                        PermissionGrant.serverGroup("public", "group.node"),
                        PermissionGrant.server("survival", "server.node"),
                        PermissionGrant.server("lobby", "other.node")
                )
        ));
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        assignments.add(new PlayerGroupAssignment(IDENTITY, "moderator"));

        BackendPermissionService service = new BackendPermissionService(
                resolver(groups, assignments),
                new ServerGroupCatalog(List.of(new ServerGroupDefinition("public", List.of("survival")))),
                "survival"
        );

        assertEquals(List.of("network.node", "group.node", "server.node"), service.resolveGrantedNodes(IDENTITY));
        assertTrue(service.hasPermission(IDENTITY, "network.node"));
        assertTrue(service.hasPermission(IDENTITY, "group.node"));
        assertTrue(service.hasPermission(IDENTITY, "server.node"));
        assertFalse(service.hasPermission(IDENTITY, "other.node"));
    }

    @Test
    void directPermissionsAreIncluded() {
        InMemoryPlayerPermissionGrantRepository permissions = new InMemoryPlayerPermissionGrantRepository();
        permissions.add(new hanamuramiyu.monban.access.permission.PlayerPermissionGrant(
                IDENTITY,
                PermissionGrant.server("survival", "direct.node")
        ));

        BackendPermissionService service = new BackendPermissionService(
                new PlayerAccessResolver(
                        new InMemoryAccessGrantRepository(),
                        new InMemoryPlayerGroupRepository(),
                        new InMemoryPlayerGroupAssignmentRepository(),
                        permissions
                ),
                new ServerGroupCatalog(List.of()),
                "survival"
        );

        assertEquals(List.of("direct.node"), service.resolveGrantedNodes(IDENTITY));
    }

    @Test
    void remoteSnapshotPermissionsAreUsed() {
        PlayerIdentity backendIdentity = PlayerIdentity.offline("Miyu", IDENTITY.technicalUuid().orElseThrow());
        PlayerGroupDefinition group = new PlayerGroupDefinition(
                "moderator",
                List.of(),
                List.of(
                        PermissionGrant.network("network.node"),
                        PermissionGrant.serverGroup("public", "group.node"),
                        PermissionGrant.server("survival", "server.node"),
                        PermissionGrant.server("lobby", "other.node")
                )
        );
        PlayerAccessStateReceiver receiver = receiver();
        accept(receiver, new PlayerAccessStateSnapshot(
                1,
                List.of(),
                List.of(group),
                List.of(new PlayerGroupAssignment(IDENTITY, group.id())),
                List.of()
        ));

        BackendPermissionService service = new BackendPermissionService(
                resolver(new InMemoryPlayerGroupRepository(), new InMemoryPlayerGroupAssignmentRepository()),
                new ServerGroupCatalog(List.of(new ServerGroupDefinition("public", List.of("survival")))),
                "survival",
                receiver
        );

        assertEquals(List.of("network.node", "group.node", "server.node"), service.resolveGrantedNodes(backendIdentity));
    }

    @Test
    void remoteModeReturnsNoPermissionsUntilSnapshotArrives() {
        PlayerAccessStateReceiver receiver = receiver();
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerGroupDefinition localGroup = new PlayerGroupDefinition(
                "local",
                List.of(),
                List.of(PermissionGrant.server("survival", "local.node"))
        );
        groups.add(localGroup);
        assignments.add(new PlayerGroupAssignment(IDENTITY, localGroup.id()));

        BackendPermissionService service = new BackendPermissionService(
                resolver(groups, assignments),
                new ServerGroupCatalog(List.of()),
                "survival",
                receiver
        );

        assertTrue(service.resolveGrantedNodes(IDENTITY).isEmpty());

        accept(receiver, new PlayerAccessStateSnapshot(1, List.of(), List.of(), List.of(), List.of()));
        assertTrue(service.resolveGrantedNodes(IDENTITY).isEmpty());
    }

    @Test
    void localResolverIsUsedWithoutRemoteReceiver() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerGroupDefinition localGroup = new PlayerGroupDefinition(
                "local",
                List.of(AccessScope.network()),
                List.of(PermissionGrant.server("survival", "local.node"))
        );
        groups.add(localGroup);
        assignments.add(new PlayerGroupAssignment(IDENTITY, localGroup.id()));

        BackendPermissionService service = new BackendPermissionService(
                resolver(groups, assignments),
                new ServerGroupCatalog(List.of()),
                "survival"
        );

        assertEquals(List.of("local.node"), service.resolveGrantedNodes(IDENTITY));
    }

    private static PlayerAccessStateReceiver receiver() {
        return new PlayerAccessStateReceiver(
                new PlayerAccessStateCodec(),
                SyncSecret.fromBase64(Base64.getEncoder().encodeToString(
                        "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
                ))
        );
    }

    private static void accept(PlayerAccessStateReceiver receiver, PlayerAccessStateSnapshot snapshot) {
        SyncSecret secret = SyncSecret.fromBase64(Base64.getEncoder().encodeToString(
                "monban-sync-secret".getBytes(StandardCharsets.UTF_8)
        ));
        assertTrue(receiver.accept(new PlayerAccessStateCodec().encode(snapshot, secret)));
    }

    private static PlayerAccessResolver resolver(
            InMemoryPlayerGroupRepository groups,
            InMemoryPlayerGroupAssignmentRepository assignments
    ) {
        return new PlayerAccessResolver(
                new InMemoryAccessGrantRepository(),
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
    }
}
