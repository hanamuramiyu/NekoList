package hanamuramiyu.monban.whitelist.memory;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.whitelist.WhitelistAddResult;
import hanamuramiyu.monban.whitelist.WhitelistRemoveResult;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import hanamuramiyu.monban.whitelist.WhitelistUpdateResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryWhitelistRepository implements WhitelistRepository {
    private final Map<PlayerIdentity, PlayerIdentity> entries = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public Optional<PlayerIdentity> find(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        readLock.lock();
        try {
            return Optional.ofNullable(entries.get(identity));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<PlayerIdentity> findAll() {
        readLock.lock();
        try {
            return List.copyOf(entries.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public WhitelistAddResult add(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        writeLock.lock();
        try {
            if (entries.containsKey(identity)) {
                return WhitelistAddResult.ALREADY_EXISTS;
            }

            entries.put(identity, identity);
            return WhitelistAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public WhitelistRemoveResult remove(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        writeLock.lock();
        try {
            return entries.remove(identity) != null
                    ? WhitelistRemoveResult.REMOVED
                    : WhitelistRemoveResult.NOT_FOUND;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public WhitelistUpdateResult update(PlayerIdentity currentIdentity, PlayerIdentity updatedIdentity) {
        Objects.requireNonNull(currentIdentity, "currentIdentity");
        Objects.requireNonNull(updatedIdentity, "updatedIdentity");

        if (currentIdentity.type() != updatedIdentity.type()) {
            return WhitelistUpdateResult.IDENTITY_TYPE_MISMATCH;
        }

        writeLock.lock();
        try {
            PlayerIdentity existing = entries.get(currentIdentity);
            if (existing == null) {
                return WhitelistUpdateResult.NOT_FOUND;
            }

            if (!currentIdentity.sameIdentityAs(updatedIdentity) && entries.containsKey(updatedIdentity)) {
                return WhitelistUpdateResult.ALREADY_EXISTS;
            }

            entries.remove(currentIdentity);
            entries.put(updatedIdentity, updatedIdentity);
            return WhitelistUpdateResult.UPDATED;
        } finally {
            writeLock.unlock();
        }
    }
}
