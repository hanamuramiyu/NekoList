package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;

import java.util.List;
import java.util.Objects;

public record PlayerAccessStateSnapshot(
        long revision,
        boolean networkWhitelistEnabled,
        List<AccessGrant> accessGrants,
        List<PlayerGroupDefinition> groups,
        List<PlayerGroupAssignment> assignments,
        List<PlayerPermissionGrant> permissions
) {
    public PlayerAccessStateSnapshot(
            long revision,
            List<AccessGrant> accessGrants,
            List<PlayerGroupDefinition> groups,
            List<PlayerGroupAssignment> assignments,
            List<PlayerPermissionGrant> permissions
    ) {
        this(revision, true, accessGrants, groups, assignments, permissions);
    }

    public PlayerAccessStateSnapshot {
        if (revision < 1) {
            throw new IllegalArgumentException("Snapshot revision must be positive.");
        }
        accessGrants = copy(accessGrants, "accessGrants");
        groups = copy(groups, "groups");
        assignments = copy(assignments, "assignments");
        permissions = copy(permissions, "permissions");
    }

    private static <T> List<T> copy(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        values.forEach(value -> Objects.requireNonNull(value, field + " value"));
        return List.copyOf(values);
    }
}
