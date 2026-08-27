package hanamuramiyu.monban.access.permission;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public record PlayerPermissionGrant(PlayerIdentity identity, PermissionGrant grant) {
    public PlayerPermissionGrant {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(grant, "grant");
    }
}
