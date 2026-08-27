package hanamuramiyu.monban.access.effective;

import hanamuramiyu.monban.access.permission.PermissionGrant;

import java.util.List;
import java.util.Objects;

public record EffectivePermission(PermissionGrant grant, List<AccessOrigin> origins) {
    public EffectivePermission {
        Objects.requireNonNull(grant, "grant");
        origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("Effective permission must have at least one origin.");
        }
    }
}
