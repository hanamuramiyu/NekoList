package hanamuramiyu.monban.access.group.memory;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryPlayerGroupAssignmentRepository implements PlayerGroupAssignmentRepository {
    private final Map<AssignmentKey, PlayerGroupAssignment> assignments = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    @Override
    public List<PlayerGroupAssignment> findAll() {
        readLock.lock();
        try {
            return List.copyOf(assignments.values());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public PlayerGroupAddResult add(PlayerGroupAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        AssignmentKey key = new AssignmentKey(assignment);

        writeLock.lock();
        try {
            if (assignments.containsKey(key)) {
                return PlayerGroupAddResult.ALREADY_EXISTS;
            }
            assignments.put(key, assignment);
            return PlayerGroupAddResult.ADDED;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public PlayerGroupRemoveResult remove(PlayerGroupAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        AssignmentKey key = new AssignmentKey(assignment);

        writeLock.lock();
        try {
            return assignments.remove(key) != null
                    ? PlayerGroupRemoveResult.REMOVED
                    : PlayerGroupRemoveResult.NOT_FOUND;
        } finally {
            writeLock.unlock();
        }
    }

    private record AssignmentKey(
            hanamuramiyu.monban.identity.PlayerIdentity identity,
            String groupId
    ) {
        private AssignmentKey(PlayerGroupAssignment assignment) {
            this(assignment.identity(), assignment.groupId());
        }
    }
}
