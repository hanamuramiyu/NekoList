package hanamuramiyu.monban.access.permission;

import hanamuramiyu.monban.access.scope.AccessScope;

import java.util.Objects;

public record PermissionGrant(AccessScope scope, String node) {
    public PermissionGrant {
        Objects.requireNonNull(scope, "scope");
        node = requireNode(node);
    }

    public static PermissionGrant network(String node) {
        return new PermissionGrant(AccessScope.network(), node);
    }

    public static PermissionGrant serverGroup(String groupId, String node) {
        return new PermissionGrant(AccessScope.serverGroup(groupId), node);
    }

    public static PermissionGrant server(String serverName, String node) {
        return new PermissionGrant(AccessScope.server(serverName), node);
    }

    private static String requireNode(String node) {
        Objects.requireNonNull(node, "node");
        if (node.isBlank()) {
            throw new IllegalArgumentException("Permission node must not be blank.");
        }
        if (!node.equals(node.strip())) {
            throw new IllegalArgumentException("Permission node must not have leading or trailing whitespace: " + node);
        }
        if (node.length() > 256) {
            throw new IllegalArgumentException("Permission node must not exceed 256 characters: " + node.length());
        }
        if (node.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Permission node must not contain whitespace: " + node);
        }
        return node;
    }
}
