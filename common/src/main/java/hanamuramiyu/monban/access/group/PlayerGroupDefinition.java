package hanamuramiyu.monban.access.group;

import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record PlayerGroupDefinition(
        String id,
        List<AccessScope> accessGrants,
        List<PermissionGrant> permissions
) {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public PlayerGroupDefinition {
        id = requireId(id);
        accessGrants = copyUnique(accessGrants, "accessGrants");
        permissions = copyUnique(permissions, "permissions");
    }

    private static <T> List<T> copyUnique(List<T> values, String field) {
        Objects.requireNonNull(values, field);
        List<T> copy = new ArrayList<>(values.size());
        Set<T> unique = new HashSet<>();
        for (T value : values) {
            Objects.requireNonNull(value, field + " value");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate " + field + " value: " + value);
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid player group id: " + id);
        }
        return id;
    }
}
