package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPresentationTest {
    private static final UUID UUID_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void listCommandPreservesScopeAndPage() {
        AccessPresentation presentation = new AccessPresentation();

        assertEquals("/monban access list 2", presentation.listCommand(null, 2));
        assertEquals("/monban access list group testing 3",
                presentation.listCommand(AccessScope.serverGroup("testing"), 3));
        assertEquals("/monban access list server \"test server\" 4",
                presentation.listCommand(AccessScope.server("test server"), 4));
    }

    @Test
    void paginationHandlesPageBoundariesAndNavigation() {
        AccessPresentation presentation = new AccessPresentation();
        List<AccessGrant> grants = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            grants.add(new AccessGrant(AccessScope.serverGroup("testing"),
                    PlayerIdentity.offline("player" + index)));
        }

        AccessListView first = presentation.listing(grants, AccessScope.serverGroup("testing"), 1);
        AccessListView last = presentation.listing(grants, AccessScope.serverGroup("testing"), 2);

        assertTrue(first.successful());
        assertTrue(last.successful());
        assertEquals(12, first.lines().size());
        assertEquals(3, last.lines().size());
        assertFalse(visibleText(first.lines().get(0)).contains("page 2"));
        assertTrue(visibleText(last.lines().get(0)).contains("page 2/2"));
    }

    @Test
    void onlineIdentityShowsFullUuidInAccessList() {
        AccessPresentation presentation = new AccessPresentation();
        AccessListView view = presentation.listing(List.of(new AccessGrant(
                AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_VALUE)
        )), null, 1);

        assertTrue(visibleText(view.lines()).contains(UUID_VALUE.toString()));
    }

    private static String visibleText(List<Component> components) {
        return components.stream().map(AccessPresentationTest::visibleText).reduce("", String::concat);
    }

    private static String visibleText(Component component) {
        StringBuilder value = new StringBuilder();
        appendVisibleText(component, value);
        return value.toString();
    }

    private static void appendVisibleText(Component component, StringBuilder value) {
        if (component instanceof TextComponent text) {
            value.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, value));
    }
}
