package hanamuramiyu.monban.access.group;

import java.util.List;
import java.util.Optional;

public interface PlayerGroupRepository {
    Optional<PlayerGroupDefinition> find(String id);

    List<PlayerGroupDefinition> findAll();

    PlayerGroupAddResult add(PlayerGroupDefinition group);

    PlayerGroupUpdateResult update(PlayerGroupDefinition group);

    PlayerGroupRemoveResult remove(String id);
}
