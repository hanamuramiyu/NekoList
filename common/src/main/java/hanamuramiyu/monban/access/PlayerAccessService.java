package hanamuramiyu.monban.access;

import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;

import java.util.Objects;
import java.util.UUID;

public final class PlayerAccessService {
    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final MonbanConfig config;
    private final PlayerIdentityResolver identityResolver;
    private final AccessGrantLookup accessGrantLookup;

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup
    ) {
        MonbanConfig checkedConfig = Objects.requireNonNull(config, "config");
        PlayerIdentityResolver checkedIdentityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        AccessGrantLookup checkedAccessGrantLookup = Objects.requireNonNull(accessGrantLookup, "accessGrantLookup");

        if (checkedConfig.identity().mode() != checkedIdentityResolver.mode()) {
            throw new IllegalArgumentException(
                    "Identity resolver mode " + checkedIdentityResolver.mode()
                            + " does not match configured identity mode " + checkedConfig.identity().mode() + "."
            );
        }

        this.config = checkedConfig;
        this.identityResolver = checkedIdentityResolver;
        this.accessGrantLookup = checkedAccessGrantLookup;
    }

    public PlayerAccessEvaluation evaluate(String name, UUID technicalUuid, boolean platformAuthenticated) {
        PlayerIdentity identity = identityResolver.resolve(name, technicalUuid, platformAuthenticated);

        if (!config.whitelist().enabled()) {
            return new PlayerAccessEvaluation(identity, AccessDecision.ALLOWED);
        }

        AccessDecision decision = accessGrantLookup.contains(NETWORK_SCOPE, identity)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_WHITELISTED;

        return new PlayerAccessEvaluation(identity, decision);
    }
}
