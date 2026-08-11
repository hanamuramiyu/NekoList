package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public record AccessGrant(AccessScope scope, PlayerIdentity identity) {
    public AccessGrant {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(identity, "identity");
    }
}
