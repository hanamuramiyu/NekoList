package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public final class BackendAdmissionService {
    private final BackendAccessPolicyCatalog policies;
    private final AccessGrantLookup grants;
    private final PlayerAccessResolver playerAccessResolver;

    public BackendAdmissionService(BackendAccessPolicyCatalog policies, AccessGrantLookup grants) {
        this(policies, grants, null);
    }

    public BackendAdmissionService(
            BackendAccessPolicyCatalog policies,
            AccessGrantLookup grants,
            PlayerAccessResolver playerAccessResolver
    ) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.playerAccessResolver = playerAccessResolver;
    }

    public BackendAdmissionDecision evaluate(BackendTarget target, PlayerIdentity identity) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(identity, "identity");

        if (policies.effectiveMode(target) == BackendAccessMode.OPEN) {
            return BackendAdmissionDecision.ALLOWED;
        }

        if (hasAccess(identity, AccessScope.server(target.serverName()))) {
            return BackendAdmissionDecision.ALLOWED;
        }

        if (target.serverGroupId().isPresent()
                && hasAccess(identity, AccessScope.serverGroup(target.serverGroupId().orElseThrow()))) {
            return BackendAdmissionDecision.ALLOWED;
        }

        return BackendAdmissionDecision.NOT_GRANTED;
    }

    private boolean hasAccess(PlayerIdentity identity, AccessScope scope) {
        return playerAccessResolver != null
                ? playerAccessResolver.resolve(identity).hasAccess(scope)
                : grants.contains(scope, identity);
    }
}
