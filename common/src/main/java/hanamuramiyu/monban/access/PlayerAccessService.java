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
    private final WhitelistPolicy whitelistPolicy;

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup
    ) {
        this(config, identityResolver, accessGrantLookup, new WhitelistPolicy(config.whitelist().enabled()));
    }

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup,
            WhitelistPolicy whitelistPolicy
    ) {
        MonbanConfig checkedConfig = Objects.requireNonNull(config, "config");
        PlayerIdentityResolver checkedIdentityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        AccessGrantLookup checkedAccessGrantLookup = Objects.requireNonNull(accessGrantLookup, "accessGrantLookup");
        WhitelistPolicy checkedWhitelistPolicy = Objects.requireNonNull(whitelistPolicy, "whitelistPolicy");

        if (checkedConfig.identity().mode() != checkedIdentityResolver.mode()) {
            throw new IllegalArgumentException(
                    "Identity resolver mode " + checkedIdentityResolver.mode()
                            + " does not match configured identity mode " + checkedConfig.identity().mode() + "."
            );
        }

        this.config = checkedConfig;
        this.identityResolver = checkedIdentityResolver;
        this.accessGrantLookup = checkedAccessGrantLookup;
        this.whitelistPolicy = checkedWhitelistPolicy;
    }

    public PlayerAccessEvaluation evaluate(String name, UUID technicalUuid, boolean platformAuthenticated) {
        PlayerIdentity identity = identityResolver.resolve(name, technicalUuid, platformAuthenticated);

        if (!whitelistPolicy.enabled()) {
            return new PlayerAccessEvaluation(identity, AccessDecision.ALLOWED);
        }

        AccessDecision decision = accessGrantLookup.contains(NETWORK_SCOPE, identity)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_WHITELISTED;

        return new PlayerAccessEvaluation(identity, decision);
    }

    public boolean whitelistEnabled() {
        return whitelistPolicy.enabled();
    }

    public boolean setWhitelistEnabled(boolean enabled) {
        return whitelistPolicy.setEnabled(enabled);
    }
}
