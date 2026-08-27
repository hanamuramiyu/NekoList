package hanamuramiyu.monban.access.effective;

import hanamuramiyu.monban.access.scope.AccessScope;

import java.util.List;
import java.util.Objects;

public record EffectiveAccess(AccessScope scope, List<AccessOrigin> origins) {
    public EffectiveAccess {
        Objects.requireNonNull(scope, "scope");
        origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("Effective access must have at least one origin.");
        }
    }
}
