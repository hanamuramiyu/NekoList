package hanamuramiyu.monban.access.effective;

import java.util.Objects;
import java.util.Optional;

public record AccessOrigin(AccessOriginType type, Optional<String> groupId) {
    public AccessOrigin {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(groupId, "groupId");
        if (type == AccessOriginType.DIRECT && groupId.isPresent()) {
            throw new IllegalArgumentException("Direct access origin must not have a group id.");
        }
        if (type == AccessOriginType.GROUP && groupId.isEmpty()) {
            throw new IllegalArgumentException("Group access origin must have a group id.");
        }
    }

    public static AccessOrigin direct() {
        return new AccessOrigin(AccessOriginType.DIRECT, Optional.empty());
    }

    public static AccessOrigin group(String groupId) {
        return new AccessOrigin(AccessOriginType.GROUP, Optional.of(requireGroupId(groupId)));
    }

    private static String requireGroupId(String groupId) {
        Objects.requireNonNull(groupId, "groupId");
        if (groupId.isBlank() || !groupId.equals(groupId.strip())) {
            throw new IllegalArgumentException("Group id must be clean text: " + groupId);
        }
        return groupId;
    }
}
