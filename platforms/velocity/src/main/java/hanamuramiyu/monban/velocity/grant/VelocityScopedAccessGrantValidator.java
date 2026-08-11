package hanamuramiyu.monban.velocity.grant;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;

import java.util.List;
import java.util.Objects;

public final class VelocityScopedAccessGrantValidator {
    private final VelocityBackendScopeValidator scopeValidator;

    public VelocityScopedAccessGrantValidator(VelocityBackendScopeValidator scopeValidator) {
        this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
    }

    public void validate(List<AccessGrant> grants) {
        Objects.requireNonNull(grants, "grants");
        for (AccessGrant grant : grants) {
            Objects.requireNonNull(grant, "grant");
            scopeValidator.validate(grant.scope(), "Persistent scoped grant");
        }
    }
}
