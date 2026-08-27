package hanamuramiyu.monban.access;

import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;

import java.util.Objects;
import java.util.UUID;

public final class PlayerAccessService {
    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final MonbanConfig config;
    private final PlayerIdentityResolver identityResolver;
    private final AccessGrantLookup accessGrantLookup;
    private final PlayerAccessResolver playerAccessResolver;
    private final PlayerAccessStateReceiver stateReceiver;
    private final WhitelistPolicy whitelistPolicy;

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup
    ) {
        this(config, identityResolver, accessGrantLookup, new WhitelistPolicy(config.whitelist().enabled()), null);
    }

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup,
            WhitelistPolicy whitelistPolicy
    ) {
        this(config, identityResolver, accessGrantLookup, whitelistPolicy, null);
    }

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup,
            WhitelistPolicy whitelistPolicy,
            PlayerAccessResolver playerAccessResolver
    ) {
        this(config, identityResolver, accessGrantLookup, whitelistPolicy, playerAccessResolver, null);
    }

    public PlayerAccessService(
            MonbanConfig config,
            PlayerIdentityResolver identityResolver,
            AccessGrantLookup accessGrantLookup,
            WhitelistPolicy whitelistPolicy,
            PlayerAccessResolver playerAccessResolver,
            PlayerAccessStateReceiver stateReceiver
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
        if (stateReceiver != null && playerAccessResolver == null) {
            throw new IllegalArgumentException("A player access resolver is required for synchronized state.");
        }

        this.config = checkedConfig;
        this.identityResolver = checkedIdentityResolver;
        this.accessGrantLookup = checkedAccessGrantLookup;
        this.playerAccessResolver = playerAccessResolver;
        this.stateReceiver = stateReceiver;
        this.whitelistPolicy = checkedWhitelistPolicy;
    }

    public PlayerAccessEvaluation evaluate(String name, UUID technicalUuid, boolean platformAuthenticated) {
        PlayerIdentity identity = identityResolver.resolve(name, technicalUuid, platformAuthenticated);

        if (!isWhitelistEnabled()) {
            return new PlayerAccessEvaluation(identity, AccessDecision.ALLOWED);
        }

        AccessDecision decision = hasNetworkAccess(identity)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_WHITELISTED;

        return new PlayerAccessEvaluation(identity, decision);
    }

    private boolean isWhitelistEnabled() {
        return stateReceiver == null
                ? whitelistPolicy.enabled()
                : stateReceiver.current()
                        .map(state -> state.networkWhitelistEnabled())
                        .orElse(true);
    }

    private boolean hasNetworkAccess(PlayerIdentity identity) {
        if (stateReceiver != null) {
            return stateReceiver.current()
                    .map(state -> playerAccessResolver.resolve(state, identity).hasAccess(NETWORK_SCOPE))
                    .orElse(false);
        }
        return playerAccessResolver != null
                ? playerAccessResolver.resolve(identity).hasAccess(NETWORK_SCOPE)
                : accessGrantLookup.contains(NETWORK_SCOPE, identity);
    }

    public boolean whitelistEnabled() {
        return whitelistPolicy.enabled();
    }

    public boolean setWhitelistEnabled(boolean enabled) {
        return whitelistPolicy.setEnabled(enabled);
    }
}
