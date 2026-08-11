package hanamuramiyu.monban.access.group;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerGroupCatalogTest {
    @Test
    void findsGroupsByIdAndServer() {
        ServerGroupDefinition publicGroup = new ServerGroupDefinition("public", List.of("lobby", "survival"));
        ServerGroupDefinition testingGroup = new ServerGroupDefinition("testing", List.of("test-lobby"));
        ServerGroupCatalog catalog = new ServerGroupCatalog(List.of(publicGroup, testingGroup));

        assertEquals(publicGroup, catalog.findById("public").orElseThrow());
        assertEquals(publicGroup, catalog.findForServer("survival").orElseThrow());
        assertEquals(testingGroup, catalog.findForServer("test-lobby").orElseThrow());
        assertTrue(catalog.findForServer("ungrouped").isEmpty());
    }

    @Test
    void rejectsDuplicateGroupIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerGroupCatalog(List.of(
                        new ServerGroupDefinition("testing", List.of("test-1")),
                        new ServerGroupDefinition("testing", List.of("test-2"))
                ))
        );
    }

    @Test
    void rejectsServerMembershipInMultipleGroups() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerGroupCatalog(List.of(
                        new ServerGroupDefinition("public", List.of("lobby")),
                        new ServerGroupDefinition("testing", List.of("lobby"))
                ))
        );
    }

    @Test
    void separateGroupsRemainIndependent() {
        ServerGroupDefinition publicGroup = new ServerGroupDefinition("public", List.of("lobby"));
        ServerGroupDefinition internalGroup = new ServerGroupDefinition("internal", List.of("dev"));
        ServerGroupCatalog catalog = new ServerGroupCatalog(List.of(publicGroup, internalGroup));

        assertEquals("public", catalog.findForServer("lobby").orElseThrow().id());
        assertEquals("internal", catalog.findForServer("dev").orElseThrow().id());
    }

    @Test
    void findAllIsIndependentAndImmutable() {
        List<ServerGroupDefinition> source = new ArrayList<>();
        ServerGroupDefinition publicGroup = new ServerGroupDefinition("public", List.of("lobby"));
        source.add(publicGroup);
        ServerGroupCatalog catalog = new ServerGroupCatalog(source);

        source.add(new ServerGroupDefinition("testing", List.of("test-lobby")));

        assertEquals(List.of(publicGroup), catalog.findAll());
        assertThrows(
                UnsupportedOperationException.class,
                () -> catalog.findAll().add(new ServerGroupDefinition("other", List.of()))
        );
    }
}
