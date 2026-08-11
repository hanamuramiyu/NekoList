package hanamuramiyu.monban.access.group;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerGroupDefinitionTest {
    @Test
    void preservesServerNamesWithoutNormalization() {
        ServerGroupDefinition group = new ServerGroupDefinition("testing", List.of("LoBbY", "test-survival"));

        assertEquals("testing", group.id());
        assertEquals(List.of("LoBbY", "test-survival"), group.servers());
    }

    @Test
    void rejectsBlankOrPaddedId() {
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition(" ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition(" testing", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition("testing ", List.of()));
    }

    @Test
    void rejectsBlankOrPaddedServerNames() {
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition("testing", List.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition("testing", List.of(" lobby")));
        assertThrows(IllegalArgumentException.class, () -> new ServerGroupDefinition("testing", List.of("lobby ")));
    }

    @Test
    void rejectsDuplicateServerInsideDefinition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerGroupDefinition("testing", List.of("lobby", "lobby"))
        );
    }

    @Test
    void serverListIsIndependentAndImmutable() {
        List<String> source = new ArrayList<>(List.of("lobby"));
        ServerGroupDefinition group = new ServerGroupDefinition("public", source);

        source.add("survival");

        assertEquals(List.of("lobby"), group.servers());
        assertThrows(UnsupportedOperationException.class, () -> group.servers().add("creative"));
    }
}
