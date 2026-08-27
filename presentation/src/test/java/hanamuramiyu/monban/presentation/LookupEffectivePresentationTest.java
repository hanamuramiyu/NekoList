package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.effective.PlayerAccessSnapshot;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupEffectivePresentationTest {
    private static final PlayerIdentity IDENTITY = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    @Test
    void displaysGroupsEffectiveAccessPermissionsAndOrigins() {
        InMemoryAccessGrantRepository access = new InMemoryAccessGrantRepository();
        access.add(new AccessGrant(AccessScope.server("staff"), IDENTITY));
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(AccessScope.network(), AccessScope.serverGroup("public")),
                List.of(PermissionGrant.server("survival", "coreprotect.inspect"))
        ));
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        assignments.add(new PlayerGroupAssignment(IDENTITY, "moderator"));
        InMemoryPlayerPermissionGrantRepository permissions = new InMemoryPlayerPermissionGrantRepository();
        permissions.add(new PlayerPermissionGrant(
                IDENTITY,
                PermissionGrant.server("survival", "coreprotect.lookup")
        ));

        PlayerAccessSnapshot snapshot = new PlayerAccessResolver(
                access,
                groups,
                assignments,
                permissions
        ).resolve(IDENTITY);
        String visible = visibleText(new LookupPresentation().result(
                PlayerIdentity.offline("Miyu"),
                Optional.of(IDENTITY),
                List.of(new AccessGrant(AccessScope.network(), IDENTITY)),
                snapshot
        ));

        assertTrue(visible.contains("Groups"));
        assertTrue(visible.contains("moderator"));
        assertTrue(visible.contains("NETWORK — GROUP:moderator"));
        assertTrue(visible.contains("SERVER_GROUP(public) — GROUP:moderator"));
        assertTrue(visible.contains("SERVER(staff) — DIRECT"));
        assertTrue(visible.contains("coreprotect.inspect — GROUP:moderator"));
        assertTrue(visible.contains("coreprotect.lookup — DIRECT"));
    }

    private static String visibleText(List<net.kyori.adventure.text.Component> components) {
        StringBuilder result = new StringBuilder();
        components.forEach(component -> appendVisibleText(component, result));
        return result.toString();
    }

    private static void appendVisibleText(
            net.kyori.adventure.text.Component component,
            StringBuilder result
    ) {
        if (component instanceof net.kyori.adventure.text.TextComponent text) {
            result.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, result));
    }
}
