package hanamuramiyu.monban.access.scope;

import java.util.Objects;
import java.util.Optional;

public final class AccessScope {
    public static final int MAX_ID_LENGTH = 128;

    private static final AccessScope NETWORK = new AccessScope(AccessScopeType.NETWORK, Optional.empty());

    private final AccessScopeType type;
    private final Optional<String> id;

    private AccessScope(AccessScopeType type, Optional<String> id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
    }

    public static AccessScope network() {
        return NETWORK;
    }

    public static AccessScope serverGroup(String id) {
        return scoped(AccessScopeType.SERVER_GROUP, id);
    }

    public static AccessScope server(String id) {
        return scoped(AccessScopeType.SERVER, id);
    }

    public AccessScopeType type() {
        return type;
    }

    public Optional<String> id() {
        return id;
    }

    private static AccessScope scoped(AccessScopeType type, String id) {
        Objects.requireNonNull(id, "id");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Access scope id must not be blank.");
        }
        if (!id.equals(id.strip())) {
            throw new IllegalArgumentException("Access scope id must not have leading or trailing whitespace: " + id);
        }
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Access scope id must not exceed " + MAX_ID_LENGTH + " characters: " + id.length()
            );
        }

        return new AccessScope(type, Optional.of(id));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AccessScope other)) {
            return false;
        }
        return type == other.type && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return id.map(value -> type + "(" + value + ")").orElseGet(type::name);
    }
}
