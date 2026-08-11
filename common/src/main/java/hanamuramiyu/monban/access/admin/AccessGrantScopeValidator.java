package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.scope.AccessScope;

@FunctionalInterface
public interface AccessGrantScopeValidator {
    /**
     * Validates that a caller-supplied scope is a valid administrative target.
     * Implementations should use {@link AccessGrantScopeValidationException} for expected
     * caller validation failures and allow unexpected runtime failures to propagate unchanged.
     */
    void validate(AccessScope scope);
}
