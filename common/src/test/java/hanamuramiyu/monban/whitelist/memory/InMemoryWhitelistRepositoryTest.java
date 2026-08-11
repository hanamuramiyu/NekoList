package hanamuramiyu.monban.whitelist.memory;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.whitelist.WhitelistAddResult;
import hanamuramiyu.monban.whitelist.WhitelistRemoveResult;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import hanamuramiyu.monban.whitelist.WhitelistUpdateResult;
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

class InMemoryWhitelistRepositoryTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID UUID_THREE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final WhitelistRepository repository = new InMemoryWhitelistRepository();

    @Test
    void addsOnlineIdentity() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);

        assertEquals(WhitelistAddResult.ADDED, repository.add(identity));
        assertTrue(repository.contains(identity));
        assertEquals(identity, repository.find(identity).orElseThrow());
    }

    @Test
    void onlineIdentityWithSameUuidAndDifferentNameIsDuplicate() {
        PlayerIdentity original = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity renamed = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);

        assertEquals(WhitelistAddResult.ADDED, repository.add(original));
        assertEquals(WhitelistAddResult.ALREADY_EXISTS, repository.add(renamed));
        assertEquals(original, repository.find(renamed).orElseThrow());
        assertEquals("hanamuramiyu_old", repository.find(renamed).orElseThrow().name());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void sameNameWithDifferentOnlineUuidsCreatesDifferentIdentities() {
        PlayerIdentity first = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity second = PlayerIdentity.online("hanamuramiyu", UUID_TWO);

        assertEquals(WhitelistAddResult.ADDED, repository.add(first));
        assertEquals(WhitelistAddResult.ADDED, repository.add(second));
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void offlineNamesAreUniqueCaseInsensitively() {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu");
        PlayerIdentity second = PlayerIdentity.offline("HanamuraMiyu");

        assertEquals(WhitelistAddResult.ADDED, repository.add(first));
        assertEquals(WhitelistAddResult.ALREADY_EXISTS, repository.add(second));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void offlineTechnicalUuidDoesNotAffectUniqueness() {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);
        PlayerIdentity second = PlayerIdentity.offline("HanamuraMiyu", UUID_TWO);

        assertEquals(WhitelistAddResult.ADDED, repository.add(first));
        assertEquals(WhitelistAddResult.ALREADY_EXISTS, repository.add(second));
        assertEquals(first, repository.find(second).orElseThrow());
    }

    @Test
    void findOnlineIdentityAfterNameChangeUsesVerifiedUuid() {
        PlayerIdentity stored = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity current = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);
        repository.add(stored);

        PlayerIdentity found = repository.find(current).orElseThrow();

        assertEquals("hanamuramiyu_old", found.name());
        assertEquals(UUID_ONE, found.verifiedUuid().orElseThrow());
    }

    @Test
    void removesIdentity() {
        PlayerIdentity stored = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity current = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);
        repository.add(stored);

        assertEquals(WhitelistRemoveResult.REMOVED, repository.remove(current));
        assertFalse(repository.contains(stored));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void removingMissingIdentityReturnsNotFound() {
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu");

        assertEquals(WhitelistRemoveResult.NOT_FOUND, repository.remove(identity));
    }

    @Test
    void updatesOnlineNameWithoutChangingIdentity() {
        PlayerIdentity original = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity renamed = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);
        repository.add(original);

        assertEquals(WhitelistUpdateResult.UPDATED, repository.update(original, renamed));
        assertEquals("hanamuramiyu_new", repository.find(original).orElseThrow().name());
        assertEquals("hanamuramiyu_new", repository.find(renamed).orElseThrow().name());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void updatesOfflineName() {
        PlayerIdentity original = PlayerIdentity.offline("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity renamed = PlayerIdentity.offline("hanamuramiyu_new", UUID_TWO);
        repository.add(original);

        assertEquals(WhitelistUpdateResult.UPDATED, repository.update(original, renamed));
        assertFalse(repository.contains(original));
        assertEquals(renamed, repository.find(renamed).orElseThrow());
        assertEquals("hanamuramiyu_new", repository.find(renamed).orElseThrow().name());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void updateToExistingIdentityReturnsAlreadyExists() {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu");
        PlayerIdentity second = PlayerIdentity.offline("hanamuramiyu2");
        PlayerIdentity conflictingRename = PlayerIdentity.offline("HANAMURAMIYU2", UUID_THREE);
        repository.add(first);
        repository.add(second);

        assertEquals(WhitelistUpdateResult.ALREADY_EXISTS, repository.update(first, conflictingRename));
        assertTrue(repository.contains(first));
        assertTrue(repository.contains(second));
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void updateFromOnlineToOfflineIsRejectedAndKeepsOriginalEntry() {
        PlayerIdentity original = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity downgraded = PlayerIdentity.offline("hanamuramiyu", UUID_TWO);
        repository.add(original);

        assertEquals(WhitelistUpdateResult.IDENTITY_TYPE_MISMATCH, repository.update(original, downgraded));
        assertEquals(original, repository.find(original).orElseThrow());
        assertFalse(repository.contains(downgraded));
        assertEquals(List.of(original), repository.findAll());
    }

    @Test
    void updateFromOfflineToOnlineIsRejectedAndKeepsOriginalEntry() {
        PlayerIdentity original = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);
        PlayerIdentity upgraded = PlayerIdentity.online("hanamuramiyu", UUID_TWO);
        repository.add(original);

        assertEquals(WhitelistUpdateResult.IDENTITY_TYPE_MISMATCH, repository.update(original, upgraded));
        assertEquals(original, repository.find(original).orElseThrow());
        assertFalse(repository.contains(upgraded));
        assertEquals(List.of(original), repository.findAll());
    }

    @Test
    void updateMissingIdentityReturnsNotFound() {
        PlayerIdentity missing = PlayerIdentity.offline("hanamuramiyu2");
        PlayerIdentity renamed = PlayerIdentity.offline("hanamuramiyu_new");

        assertEquals(WhitelistUpdateResult.NOT_FOUND, repository.update(missing, renamed));
    }

    @Test
    void findAllReturnsIndependentImmutableSnapshot() {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu");
        PlayerIdentity second = PlayerIdentity.offline("hanamuramiyu2");
        repository.add(first);

        List<PlayerIdentity> snapshot = repository.findAll();
        repository.add(second);
        repository.remove(first);

        assertEquals(List.of(first), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));
        assertEquals(List.of(second), repository.findAll());
    }

    @Test
    void concurrentAddOfSameIdentitySucceedsExactlyOnce() throws Exception {
        int threadCount = 16;
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<WhitelistAddResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.add(identity);
                }));
            }

            ready.await();
            start.countDown();

            long addedCount = 0;
            long alreadyExistsCount = 0;
            for (Future<WhitelistAddResult> future : futures) {
                WhitelistAddResult result = future.get();
                if (result == WhitelistAddResult.ADDED) {
                    addedCount++;
                } else if (result == WhitelistAddResult.ALREADY_EXISTS) {
                    alreadyExistsCount++;
                }
            }

            assertEquals(1, addedCount);
            assertEquals(threadCount - 1L, alreadyExistsCount);
            assertEquals(1, repository.findAll().size());
        }
    }
}
