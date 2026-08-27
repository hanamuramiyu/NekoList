package hanamuramiyu.monban.access.permission.memory;

import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PermissionGrantRemoveResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryPlayerPermissionGrantRepository implements PlayerPermissionGrantRepository {
    private final Map<GrantKey, PlayerPermissionGrant> grants = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public List<PlayerPermissionGrant> findAll() {
        readLock.lock();
        try {
            return List.copyOf(grants.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PermissionGrantAddResult add(PlayerPermissionGrant grant) {
        Objects.requireNonNull(grant, "grant");
        GrantKey key = new GrantKey(grant);

        writeLock.lock();
        try {
            if (grants.containsKey(key)) {
                return PermissionGrantAddResult.ALREADY_EXISTS;
            }
            grants.put(key, grant);
            return PermissionGrantAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PermissionGrantRemoveResult remove(PlayerPermissionGrant grant) {
        Objects.requireNonNull(grant, "grant");
        GrantKey key = new GrantKey(grant);

        writeLock.lock();
        try {
            return grants.remove(key) != null
                    ? PermissionGrantRemoveResult.REMOVED
                    : PermissionGrantRemoveResult.NOT_FOUND;
        } finally {
            writeLock.unlock();
        }
    }

    private record GrantKey(
            PlayerIdentity identity,
            AccessScope scope,
            String node
    ) {
        private GrantKey(PlayerPermissionGrant grant) {
            this(grant.identity(), grant.grant().scope(), grant.grant().node());
        }
    }
}
