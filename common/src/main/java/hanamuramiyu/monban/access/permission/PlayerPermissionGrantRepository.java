package hanamuramiyu.monban.access.permission;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.List;

public interface PlayerPermissionGrantRepository {
    List<PlayerPermissionGrant> findAll();

    default List<PlayerPermissionGrant> findAll(PlayerIdentity identity) {
        return findAll().stream()
                .filter(grant -> grant.identity().equals(identity))
                .toList();
    }

    PermissionGrantAddResult add(PlayerPermissionGrant grant);

    PermissionGrantRemoveResult remove(PlayerPermissionGrant grant);
}
