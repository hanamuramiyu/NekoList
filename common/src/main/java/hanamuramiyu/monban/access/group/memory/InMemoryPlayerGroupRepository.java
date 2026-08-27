package hanamuramiyu.monban.access.group.memory;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.group.PlayerGroupUpdateResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryPlayerGroupRepository implements PlayerGroupRepository {
    private final Map<String, PlayerGroupDefinition> groups = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public Optional<PlayerGroupDefinition> find(String id) {
        Objects.requireNonNull(id, "id");

        readLock.lock();
        try {
            return Optional.ofNullable(groups.get(id));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<PlayerGroupDefinition> findAll() {
        readLock.lock();
        try {
            return List.copyOf(groups.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PlayerGroupAddResult add(PlayerGroupDefinition group) {
        Objects.requireNonNull(group, "group");

        writeLock.lock();
        try {
            if (groups.containsKey(group.id())) {
                return PlayerGroupAddResult.ALREADY_EXISTS;
            }
            groups.put(group.id(), group);
            return PlayerGroupAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupUpdateResult update(PlayerGroupDefinition group) {
        Objects.requireNonNull(group, "group");

        writeLock.lock();
        try {
            if (!groups.containsKey(group.id())) {
                return PlayerGroupUpdateResult.NOT_FOUND;
            }
            groups.put(group.id(), group);
            return PlayerGroupUpdateResult.UPDATED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupRemoveResult remove(String id) {
        Objects.requireNonNull(id, "id");

        writeLock.lock();
        try {
            return groups.remove(id) != null
                    ? PlayerGroupRemoveResult.REMOVED
                    : PlayerGroupRemoveResult.NOT_FOUND;
        } finally {
            writeLock.unlock();
        }
    }
}
