package hanamuramiyu.monban.velocity.grant;

import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidator;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidationException;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;

import java.util.Objects;

public final class VelocityAccessGrantScopeValidator implements AccessGrantScopeValidator {
    private final VelocityBackendScopeValidator backendScopeValidator;

    public VelocityAccessGrantScopeValidator(VelocityBackendScopeValidator backendScopeValidator) {
        this.backendScopeValidator = Objects.requireNonNull(backendScopeValidator, "backendScopeValidator");
    }

    @Override
    public void validate(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        switch (scope.type()) {
            case NETWORK -> {
            }
            case SERVER_GROUP, SERVER -> validateBackendScope(scope);
        }
    }

    private void validateBackendScope(AccessScope scope) {
        try {
            backendScopeValidator.validate(scope, "Access grant administration");
        } catch (VelocityBackendScopeValidationException exception) {
            throw new AccessGrantScopeValidationException(exception.getMessage(), exception);
        }
    }
}
