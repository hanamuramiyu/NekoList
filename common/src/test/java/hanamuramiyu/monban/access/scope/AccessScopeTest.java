package hanamuramiyu.monban.access.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessScopeTest {
    @Test
    void networkHasNoId() {
        AccessScope scope = AccessScope.network();

        assertEquals(AccessScopeType.NETWORK, scope.type());
        assertTrue(scope.id().isEmpty());
    }

    @Test
    void scopedIdsArePreservedExactly() {
        AccessScope group = AccessScope.serverGroup("Testing-Group");
        AccessScope server = AccessScope.server("Dev-1");

        assertEquals("Testing-Group", group.id().orElseThrow());
        assertEquals("Dev-1", server.id().orElseThrow());
    }

    @Test
    void sameIdInDifferentScopeTypesIsDifferent() {
        assertNotEquals(AccessScope.serverGroup("testing"), AccessScope.server("testing"));
    }

    @Test
    void rejectsNullScopedId() {
        assertThrows(NullPointerException.class, () -> AccessScope.serverGroup(null));
        assertThrows(NullPointerException.class, () -> AccessScope.server(null));
    }

    @Test
    void rejectsBlankScopedId() {
        assertThrows(IllegalArgumentException.class, () -> AccessScope.serverGroup(""));
        assertThrows(IllegalArgumentException.class, () -> AccessScope.server("   "));
    }

    @Test
    void rejectsLeadingOrTrailingWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> AccessScope.serverGroup(" testing"));
        assertThrows(IllegalArgumentException.class, () -> AccessScope.server("dev-1 "));
    }

    @Test
    void rejectsScopedIdLongerThanLimit() {
        String tooLong = "x".repeat(AccessScope.MAX_ID_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> AccessScope.serverGroup(tooLong));
        assertThrows(IllegalArgumentException.class, () -> AccessScope.server(tooLong));
    }
}
