package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScopeRoutingAccessGrantRepository implements AccessGrantRepository {
    private final AccessGrantRepository networkRepository;
    private final AccessGrantRepository scopedRepository;

    public ScopeRoutingAccessGrantRepository(
            AccessGrantRepository networkRepository,
            AccessGrantRepository scopedRepository
    ) {
        this.networkRepository = Objects.requireNonNull(networkRepository, "networkRepository");
        this.scopedRepository = Objects.requireNonNull(scopedRepository, "scopedRepository");
    }

    @Override
    public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(identity, "identity");
        return repositoryFor(scope).find(scope, identity);
    }

    @Override
    public List<AccessGrant> findAll() {
        List<AccessGrant> grants = new ArrayList<>();
        grants.addAll(networkRepository.findAll());
        grants.addAll(scopedRepository.findAll());
        return List.copyOf(grants);
    }

    @Override
    public AccessGrantAddResult add(AccessGrant grant) {
        Objects.requireNonNull(grant, "grant");
        return repositoryFor(grant.scope()).add(grant);
    }

    @Override
    public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(identity, "identity");
        return repositoryFor(scope).remove(scope, identity);
    }

    private AccessGrantRepository repositoryFor(AccessScope scope) {
        return switch (scope.type()) {
            case NETWORK -> networkRepository;
            case SERVER_GROUP, SERVER -> scopedRepository;
        };
    }
}
