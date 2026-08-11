package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public final class BackendAdmissionService {
    private final BackendAccessPolicyCatalog policies;
    private final AccessGrantLookup grants;

    public BackendAdmissionService(BackendAccessPolicyCatalog policies, AccessGrantLookup grants) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.grants = Objects.requireNonNull(grants, "grants");
    }

    public BackendAdmissionDecision evaluate(BackendTarget target, PlayerIdentity identity) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(identity, "identity");

        if (policies.effectiveMode(target) == BackendAccessMode.OPEN) {
            return BackendAdmissionDecision.ALLOWED;
        }

        if (grants.contains(AccessScope.server(target.serverName()), identity)) {
            return BackendAdmissionDecision.ALLOWED;
        }

        if (target.serverGroupId().isPresent()
                && grants.contains(AccessScope.serverGroup(target.serverGroupId().orElseThrow()), identity)) {
            return BackendAdmissionDecision.ALLOWED;
        }

        return BackendAdmissionDecision.NOT_GRANTED;
    }
}
