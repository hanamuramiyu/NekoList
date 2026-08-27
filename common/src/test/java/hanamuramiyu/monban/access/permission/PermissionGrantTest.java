package hanamuramiyu.monban.access.permission;

import hanamuramiyu.monban.access.scope.AccessScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PermissionGrantTest {
    @Test
    void createsGrantsForAllSupportedScopes() {
        assertEquals(AccessScope.network(), PermissionGrant.network("proxy.command").scope());
        assertEquals(
                AccessScope.serverGroup("public"),
                PermissionGrant.serverGroup("public", "essentials.fly").scope()
        );
        assertEquals(
                AccessScope.server("survival"),
                PermissionGrant.server("survival", "coreprotect.inspect").scope()
        );
    }

    @Test
    void rejectsInvalidPermissionNodes() {
        assertThrows(NullPointerException.class, () -> PermissionGrant.network(null));
        assertThrows(IllegalArgumentException.class, () -> PermissionGrant.network(""));
        assertThrows(IllegalArgumentException.class, () -> PermissionGrant.network(" coreprotect.inspect"));
        assertThrows(IllegalArgumentException.class, () -> PermissionGrant.network("coreprotect inspect"));
        assertThrows(IllegalArgumentException.class, () -> PermissionGrant.network("x".repeat(257)));
    }
}
