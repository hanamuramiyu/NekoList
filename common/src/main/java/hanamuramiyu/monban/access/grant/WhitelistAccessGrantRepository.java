package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.whitelist.WhitelistRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WhitelistAccessGrantRepository implements AccessGrantRepository {
    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final WhitelistRepository whitelistRepository;

    public WhitelistAccessGrantRepository(WhitelistRepository whitelistRepository) {
        this.whitelistRepository = Objects.requireNonNull(whitelistRepository, "whitelistRepository");
    }

    @Override
    public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
        requireNetworkScope(scope);
        Objects.requireNonNull(identity, "identity");

        return whitelistRepository.find(identity)
                .map(storedIdentity -> new AccessGrant(NETWORK_SCOPE, storedIdentity));
    }

    @Override
    public List<AccessGrant> findAll() {
        return whitelistRepository.findAll().stream()
                .map(identity -> new AccessGrant(NETWORK_SCOPE, identity))
                .toList();
    }

    @Override
    public AccessGrantAddResult add(AccessGrant grant) {
        Objects.requireNonNull(grant, "grant");
        requireNetworkScope(grant.scope());

        return switch (whitelistRepository.add(grant.identity())) {
            case ADDED -> AccessGrantAddResult.ADDED;
            case ALREADY_EXISTS -> AccessGrantAddResult.ALREADY_EXISTS;
        };
    }

    @Override
    public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
        requireNetworkScope(scope);
        Objects.requireNonNull(identity, "identity");

        return switch (whitelistRepository.remove(identity)) {
            case REMOVED -> AccessGrantRemoveResult.REMOVED;
            case NOT_FOUND -> AccessGrantRemoveResult.NOT_FOUND;
        };
    }

    private static void requireNetworkScope(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (!NETWORK_SCOPE.equals(scope)) {
            throw new IllegalArgumentException(
                    "Whitelist access-grant repository supports only NETWORK access scope, got: " + scope
            );
        }
    }
}
