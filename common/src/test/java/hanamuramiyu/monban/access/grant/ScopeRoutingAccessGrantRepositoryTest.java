package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeRoutingAccessGrantRepositoryTest {
    private static final PlayerIdentity IDENTITY =
            PlayerIdentity.online("hanamuramiyu", UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void networkFindAddRemoveUseOnlyNetworkRepository() {
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);
        AccessGrant grant = new AccessGrant(AccessScope.network(), IDENTITY);
        network.findResult = Optional.of(grant);

        assertEquals(network.findResult, router.find(AccessScope.network(), IDENTITY));
        assertEquals(AccessGrantAddResult.ADDED, router.add(grant));
        assertEquals(AccessGrantRemoveResult.REMOVED, router.remove(AccessScope.network(), IDENTITY));

        assertEquals(1, network.findCalls);
        assertEquals(1, network.addCalls);
        assertEquals(1, network.removeCalls);
        assertEquals(0, scoped.totalMutationAndFindCalls());
    }

    @Test
    void serverGroupFindAddRemoveUseOnlyScopedRepository() {
        assertScopedRouting(AccessScope.serverGroup("testing"));
    }

    @Test
    void serverFindAddRemoveUseOnlyScopedRepository() {
        assertScopedRouting(AccessScope.server("dev"));
    }

    @Test
    void networkMissDoesNotFallBackToScopedRepository() {
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        scoped.findResult = Optional.of(new AccessGrant(AccessScope.network(), IDENTITY));
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);

        assertTrue(router.find(AccessScope.network(), IDENTITY).isEmpty());
        assertEquals(1, network.findCalls);
        assertEquals(0, scoped.findCalls);
    }

    @Test
    void scopedMissDoesNotFallBackToNetworkRepository() {
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        AccessScope scope = AccessScope.server("dev");
        network.findResult = Optional.of(new AccessGrant(scope, IDENTITY));
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);

        assertTrue(router.find(scope, IDENTITY).isEmpty());
        assertEquals(0, network.findCalls);
        assertEquals(1, scoped.findCalls);
    }

    @Test
    void findAllCombinesNetworkFirstAndScopedSecondAsImmutableSnapshot() {
        AccessGrant networkGrant = new AccessGrant(AccessScope.network(), IDENTITY);
        AccessGrant groupGrant = new AccessGrant(AccessScope.serverGroup("testing"), IDENTITY);
        AccessGrant serverGrant = new AccessGrant(AccessScope.server("dev"), IDENTITY);
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        network.all = List.of(networkGrant);
        scoped.all = List.of(groupGrant, serverGrant);
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);

        List<AccessGrant> result = router.findAll();

        assertEquals(List.of(networkGrant, groupGrant, serverGrant), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(networkGrant));
        assertEquals(1, network.findAllCalls);
        assertEquals(1, scoped.findAllCalls);
    }

    @Test
    void delegateFailureIsNotSwallowed() {
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        scoped.failure = new IllegalStateException("delegate failed");
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> router.add(new AccessGrant(AccessScope.server("dev"), IDENTITY))
        );

        assertEquals("delegate failed", exception.getMessage());
    }

    private static void assertScopedRouting(AccessScope scope) {
        RecordingRepository network = new RecordingRepository();
        RecordingRepository scoped = new RecordingRepository();
        ScopeRoutingAccessGrantRepository router = new ScopeRoutingAccessGrantRepository(network, scoped);
        AccessGrant grant = new AccessGrant(scope, IDENTITY);
        scoped.findResult = Optional.of(grant);

        assertEquals(scoped.findResult, router.find(scope, IDENTITY));
        assertEquals(AccessGrantAddResult.ADDED, router.add(grant));
        assertEquals(AccessGrantRemoveResult.REMOVED, router.remove(scope, IDENTITY));

        assertEquals(0, network.totalMutationAndFindCalls());
        assertEquals(1, scoped.findCalls);
        assertEquals(1, scoped.addCalls);
        assertEquals(1, scoped.removeCalls);
    }

    private static final class RecordingRepository implements AccessGrantRepository {
        private int findCalls;
        private int findAllCalls;
        private int addCalls;
        private int removeCalls;
        private Optional<AccessGrant> findResult = Optional.empty();
        private List<AccessGrant> all = List.of();
        private RuntimeException failure;

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            failIfNeeded();
            findCalls++;
            return findResult;
        }

        @Override
        public List<AccessGrant> findAll() {
            failIfNeeded();
            findAllCalls++;
            return all;
        }

        @Override
        public AccessGrantAddResult add(AccessGrant grant) {
            failIfNeeded();
            addCalls++;
            return AccessGrantAddResult.ADDED;
        }

        @Override
        public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
            failIfNeeded();
            removeCalls++;
            return AccessGrantRemoveResult.REMOVED;
        }

        private int totalMutationAndFindCalls() {
            return findCalls + addCalls + removeCalls;
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
