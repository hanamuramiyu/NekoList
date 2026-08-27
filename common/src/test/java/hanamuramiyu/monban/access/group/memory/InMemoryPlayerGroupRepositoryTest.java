package hanamuramiyu.monban.access.group.memory;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPlayerGroupRepositoryTest {
    @Test
    void storesGroupsAndRejectsDuplicateIds() {
        InMemoryPlayerGroupRepository repository = new InMemoryPlayerGroupRepository();
        PlayerGroupDefinition group = new PlayerGroupDefinition("moderator", List.of(), List.of());

        assertEquals(PlayerGroupAddResult.ADDED, repository.add(group));
        assertEquals(PlayerGroupAddResult.ALREADY_EXISTS, repository.add(group));
        assertEquals(group, repository.find("moderator").orElseThrow());
        assertEquals(List.of(group), repository.findAll());
        assertEquals(PlayerGroupRemoveResult.REMOVED, repository.remove("moderator"));
        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, repository.remove("moderator"));
        assertTrue(repository.findAll().isEmpty());
    }
}
