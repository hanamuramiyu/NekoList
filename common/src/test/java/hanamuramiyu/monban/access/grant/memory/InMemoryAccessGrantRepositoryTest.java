package hanamuramiyu.monban.access.grant.memory;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAccessGrantRepositoryTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AccessGrantRepository repository = new InMemoryAccessGrantRepository();

    @Test
    void repositoryCanBeUsedThroughReadOnlyLookup() {
        AccessScope scope = AccessScope.network();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        repository.add(new AccessGrant(scope, identity));

        AccessGrantLookup lookup = repository;

        assertTrue(lookup.contains(scope, identity));
        assertEquals(identity, lookup.find(scope, identity).orElseThrow().identity());
    }

    @Test
    void sameIdentityCanExistInMultipleScopes() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        AccessGrant network = new AccessGrant(AccessScope.network(), identity);
        AccessGrant group = new AccessGrant(AccessScope.serverGroup("testing"), identity);
        AccessGrant server = new AccessGrant(AccessScope.server("dev-1"), identity);

        assertEquals(AccessGrantAddResult.ADDED, repository.add(network));
        assertEquals(AccessGrantAddResult.ADDED, repository.add(group));
        assertEquals(AccessGrantAddResult.ADDED, repository.add(server));
        assertTrue(repository.contains(network.scope(), identity));
        assertTrue(repository.contains(group.scope(), identity));
        assertTrue(repository.contains(server.scope(), identity));
        assertEquals(3, repository.findAll().size());
    }

    @Test
    void removingFromOneScopeDoesNotAffectAnother() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        AccessScope network = AccessScope.network();
        AccessScope group = AccessScope.serverGroup("testing");
        repository.add(new AccessGrant(network, identity));
        repository.add(new AccessGrant(group, identity));

        assertEquals(AccessGrantRemoveResult.REMOVED, repository.remove(group, identity));
        assertTrue(repository.contains(network, identity));
        assertFalse(repository.contains(group, identity));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void duplicateWithinSameScopeReturnsAlreadyExists() {
        AccessScope scope = AccessScope.network();
        AccessGrant grant = new AccessGrant(scope, PlayerIdentity.online("hanamuramiyu", UUID_ONE));

        assertEquals(AccessGrantAddResult.ADDED, repository.add(grant));
        assertEquals(AccessGrantAddResult.ALREADY_EXISTS, repository.add(grant));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void onlineRenameUsesVerifiedUuidWithinScope() {
        AccessScope scope = AccessScope.serverGroup("testing");
        PlayerIdentity original = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity renamed = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);
        repository.add(new AccessGrant(scope, original));

        assertTrue(repository.contains(scope, renamed));
        assertEquals(AccessGrantAddResult.ALREADY_EXISTS, repository.add(new AccessGrant(scope, renamed)));
        assertEquals("hanamuramiyu_old", repository.find(scope, renamed).orElseThrow().identity().name());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void offlineIdentityIsCaseInsensitiveWithinScope() {
        AccessScope scope = AccessScope.server("survival");
        PlayerIdentity original = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);
        PlayerIdentity sameIdentity = PlayerIdentity.offline("HanamuraMiyu", UUID_TWO);
        repository.add(new AccessGrant(scope, original));

        assertTrue(repository.contains(scope, sameIdentity));
        assertEquals(AccessGrantAddResult.ALREADY_EXISTS, repository.add(new AccessGrant(scope, sameIdentity)));
        assertEquals("hanamuramiyu", repository.find(scope, sameIdentity).orElseThrow().identity().name());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void findAllReturnsIndependentImmutableSnapshot() {
        AccessGrant first = new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"));
        AccessGrant second = new AccessGrant(AccessScope.server("dev-1"), PlayerIdentity.offline("hanamuramiyu2"));
        repository.add(first);

        List<AccessGrant> snapshot = repository.findAll();
        repository.add(second);
        repository.remove(first.scope(), first.identity());

        assertEquals(List.of(first), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));
        assertEquals(List.of(second), repository.findAll());
    }

    @Test
    void concurrentAddOfSameGrantSucceedsExactlyOnce() throws Exception {
        int threadCount = 16;
        AccessGrant grant = new AccessGrant(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.online("hanamuramiyu", UUID_ONE)
        );
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AccessGrantAddResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.add(grant);
                }));
            }

            ready.await();
            start.countDown();

            long addedCount = 0;
            long alreadyExistsCount = 0;
            for (Future<AccessGrantAddResult> future : futures) {
                AccessGrantAddResult result = future.get();
                if (result == AccessGrantAddResult.ADDED) {
                    addedCount++;
                } else if (result == AccessGrantAddResult.ALREADY_EXISTS) {
                    alreadyExistsCount++;
                }
            }

            assertEquals(1, addedCount);
            assertEquals(threadCount - 1L, alreadyExistsCount);
            assertEquals(1, repository.findAll().size());
        }
    }
}
