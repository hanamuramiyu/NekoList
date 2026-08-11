package hanamuramiyu.monban.velocity.backend;

import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;

import java.util.Objects;

public final class VelocityBackendAccessPolicyValidator {
    private final VelocityBackendScopeValidator scopeValidator;

    public VelocityBackendAccessPolicyValidator(VelocityBackendScopeValidator scopeValidator) {
        this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
    }

    public void validate(BackendAccessPolicyCatalog policies) {
        Objects.requireNonNull(policies, "policies");
        policies.serverGroupPolicies().keySet().forEach(groupId ->
                scopeValidator.validate(AccessScope.serverGroup(groupId), "Backend access policy")
        );
        policies.serverPolicies().keySet().forEach(serverName ->
                scopeValidator.validate(AccessScope.server(serverName), "Backend access policy")
        );
    }
}
