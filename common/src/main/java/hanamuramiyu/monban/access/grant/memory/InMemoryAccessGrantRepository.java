package hanamuramiyu.monban.access.grant.memory;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryAccessGrantRepository implements AccessGrantRepository {
    private final Map<GrantKey, AccessGrant> grants = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
        GrantKey key = new GrantKey(scope, identity);

        readLock.lock();
        try {
            return Optional.ofNullable(grants.get(key));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<AccessGrant> findAll() {
        readLock.lock();
        try {
            return List.copyOf(grants.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public AccessGrantAddResult add(AccessGrant grant) {
        Objects.requireNonNull(grant, "grant");
        GrantKey key = new GrantKey(grant.scope(), grant.identity());

        writeLock.lock();
        try {
            if (grants.containsKey(key)) {
                return AccessGrantAddResult.ALREADY_EXISTS;
            }

            grants.put(key, grant);
            return AccessGrantAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
        GrantKey key = new GrantKey(scope, identity);

        writeLock.lock();
        try {
            return grants.remove(key) != null
                    ? AccessGrantRemoveResult.REMOVED
                    : AccessGrantRemoveResult.NOT_FOUND;
        } finally {
            writeLock.unlock();
        }
    }

    private record GrantKey(AccessScope scope, PlayerIdentity identity) {
        private GrantKey {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(identity, "identity");
        }
    }
}
