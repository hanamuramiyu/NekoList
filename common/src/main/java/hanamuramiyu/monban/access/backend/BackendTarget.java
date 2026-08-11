package hanamuramiyu.monban.access.backend;

import java.util.Objects;
import java.util.Optional;

public record BackendTarget(String serverName, Optional<String> serverGroupId) {
    public BackendTarget {
        serverName = requireIdentifier(serverName, "serverName");
        serverGroupId = Objects.requireNonNull(serverGroupId, "serverGroupId")
                .map(value -> requireIdentifier(value, "serverGroupId"));
    }

    public static BackendTarget ungrouped(String serverName) {
        return new BackendTarget(serverName, Optional.empty());
    }

    public static BackendTarget grouped(String serverName, String serverGroupId) {
        return new BackendTarget(serverName, Optional.of(requireIdentifier(serverGroupId, "serverGroupId")));
    }

    private static String requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have leading or trailing whitespace: " + value);
        }
        return value;
    }
}
