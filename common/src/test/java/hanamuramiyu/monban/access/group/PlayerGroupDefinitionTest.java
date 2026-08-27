package hanamuramiyu.monban.access.group;

import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerGroupDefinitionTest {
    @Test
    void preservesAclAndPermissionsAsImmutableCopies() {
        List<AccessScope> access = new ArrayList<>(List.of(AccessScope.network()));
        List<PermissionGrant> permissions = new ArrayList<>(List.of(
                PermissionGrant.server("survival", "coreprotect.inspect")
        ));

        PlayerGroupDefinition group = new PlayerGroupDefinition("moderator", access, permissions);
        access.add(AccessScope.server("staff"));
        permissions.add(PermissionGrant.network("someproxy.command"));

        assertEquals(List.of(AccessScope.network()), group.accessGrants());
        assertEquals(
                List.of(PermissionGrant.server("survival", "coreprotect.inspect")),
                group.permissions()
        );
        assertThrows(UnsupportedOperationException.class, () -> group.accessGrants().add(AccessScope.server("staff")));
        assertThrows(
                UnsupportedOperationException.class,
                () -> group.permissions().add(PermissionGrant.network("someproxy.command"))
        );
    }

    @Test
    void rejectsInvalidIdsAndDuplicateEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerGroupDefinition("Moderator", List.of(), List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerGroupDefinition(
                        "moderator",
                        List.of(AccessScope.network(), AccessScope.network()),
                        List.of()
                )
        );
        PermissionGrant permission = PermissionGrant.server("survival", "coreprotect.inspect");
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerGroupDefinition("moderator", List.of(), List.of(permission, permission))
        );
    }
}
