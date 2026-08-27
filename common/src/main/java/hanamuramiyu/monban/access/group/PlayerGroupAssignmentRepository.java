package hanamuramiyu.monban.access.group;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.List;

public interface PlayerGroupAssignmentRepository {
    List<PlayerGroupAssignment> findAll();

    default List<PlayerGroupAssignment> findAll(PlayerIdentity identity) {
        return findAll().stream()
                .filter(assignment -> assignment.identity().equals(identity))
                .toList();
    }

    PlayerGroupAddResult add(PlayerGroupAssignment assignment);

    PlayerGroupRemoveResult remove(PlayerGroupAssignment assignment);
}
