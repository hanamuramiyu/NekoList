package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Optional;

public interface AccessGrantLookup {
    Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity);

    default boolean contains(AccessScope scope, PlayerIdentity identity) {
        return find(scope, identity).isPresent();
    }
}
