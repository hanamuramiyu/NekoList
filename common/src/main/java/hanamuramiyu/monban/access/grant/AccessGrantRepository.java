package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

public interface AccessGrantRepository extends AccessGrantLookup, AccessGrantInventory {
    AccessGrantAddResult add(AccessGrant grant);

    AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity);
}
