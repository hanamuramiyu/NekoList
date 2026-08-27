package hanamuramiyu.monban.access.effective;

import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.List;
import java.util.Objects;

public record PlayerAccessSnapshot(
        PlayerIdentity identity,
        List<PlayerGroupDefinition> groups,
        List<EffectiveAccess> accesses,
        List<EffectivePermission> permissions
) {
    public PlayerAccessSnapshot {
        Objects.requireNonNull(identity, "identity");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        accesses = List.copyOf(Objects.requireNonNull(accesses, "accesses"));
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    public boolean hasAccess(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        return accesses.stream().anyMatch(access -> access.scope().equals(scope));
    }

    public List<EffectivePermission> permissions(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        return permissions.stream()
                .filter(permission -> permission.grant().scope().equals(scope))
                .toList();
    }

    public boolean hasPermission(AccessScope scope, String node) {
        Objects.requireNonNull(node, "node");
        return permissions(scope).stream()
                .map(permission -> permission.grant().node())
                .anyMatch(node::equals);
    }
}
