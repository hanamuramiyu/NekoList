package hanamuramiyu.monban.access.grant;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.whitelist.WhitelistAddResult;
import hanamuramiyu.monban.whitelist.WhitelistRemoveResult;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import hanamuramiyu.monban.whitelist.WhitelistUpdateResult;
import hanamuramiyu.monban.whitelist.memory.InMemoryWhitelistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistAccessGrantRepositoryTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final WhitelistRepository whitelistRepository = new InMemoryWhitelistRepository();
    private final WhitelistAccessGrantRepository repository = new WhitelistAccessGrantRepository(whitelistRepository);

    @Test
    void networkFindReturnsGrant() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        whitelistRepository.add(identity);

        AccessGrant grant = repository.find(AccessScope.network(), identity).orElseThrow();

        assertEquals(AccessScope.network(), grant.scope());
        assertEquals(identity, grant.identity());
    }

    @Test
    void findAllMapsWhitelistIdentitiesToNetworkGrants() {
        PlayerIdentity online = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity offline = PlayerIdentity.offline("hanamuramiyu2", UUID_TWO);
        whitelistRepository.add(online);
        whitelistRepository.add(offline);

        List<AccessGrant> grants = repository.findAll();

        assertEquals(List.of(
                new AccessGrant(AccessScope.network(), online),
                new AccessGrant(AccessScope.network(), offline)
        ), grants);
        assertThrows(UnsupportedOperationException.class, () -> grants.add(new AccessGrant(AccessScope.network(), online)));
    }

    @Test
    void networkAddMapsResults() {
        AccessGrant grant = new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_ONE));

        assertEquals(AccessGrantAddResult.ADDED, repository.add(grant));
        assertEquals(AccessGrantAddResult.ALREADY_EXISTS, repository.add(grant));
    }

    @Test
    void networkRemoveMapsResults() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        whitelistRepository.add(identity);

        assertEquals(AccessGrantRemoveResult.REMOVED, repository.remove(AccessScope.network(), identity));
        assertEquals(AccessGrantRemoveResult.NOT_FOUND, repository.remove(AccessScope.network(), identity));
    }

    @Test
    void onlineIdentitySemanticsRemainUuidBased() {
        whitelistRepository.add(PlayerIdentity.online("hanamuramiyu_old", UUID_ONE));

        assertTrue(repository.find(
                AccessScope.network(),
                PlayerIdentity.online("hanamuramiyu_new", UUID_ONE)
        ).isPresent());
    }

    @Test
    void offlineIdentitySemanticsRemainCaseInsensitive() {
        whitelistRepository.add(PlayerIdentity.offline("hanamuramiyu", UUID_ONE));

        assertTrue(repository.find(
                AccessScope.network(),
                PlayerIdentity.offline("HanamuraMiyu", UUID_TWO)
        ).isPresent());
    }

    @Test
    void serverGroupOperationsAreRejected() {
        AccessScope scope = AccessScope.serverGroup("testing");
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);

        assertThrows(IllegalArgumentException.class, () -> repository.find(scope, identity));
        assertThrows(IllegalArgumentException.class, () -> repository.add(new AccessGrant(scope, identity)));
        assertThrows(IllegalArgumentException.class, () -> repository.remove(scope, identity));
    }

    @Test
    void serverOperationsAreRejected() {
        AccessScope scope = AccessScope.server("dev");
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);

        assertThrows(IllegalArgumentException.class, () -> repository.find(scope, identity));
        assertThrows(IllegalArgumentException.class, () -> repository.add(new AccessGrant(scope, identity)));
        assertThrows(IllegalArgumentException.class, () -> repository.remove(scope, identity));
    }

    @Test
    void delegateFailuresPropagate() {
        WhitelistAccessGrantRepository failing = new WhitelistAccessGrantRepository(new FailingWhitelistRepository());
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> failing.add(new AccessGrant(AccessScope.network(), identity))
        );

        assertEquals("delegate failed", exception.getMessage());
    }

    private static final class FailingWhitelistRepository implements WhitelistRepository {
        @Override
        public Optional<PlayerIdentity> find(PlayerIdentity identity) {
            throw new IllegalStateException("delegate failed");
        }

        @Override
        public List<PlayerIdentity> findAll() {
            throw new IllegalStateException("delegate failed");
        }

        @Override
        public WhitelistAddResult add(PlayerIdentity identity) {
            throw new IllegalStateException("delegate failed");
        }

        @Override
        public WhitelistRemoveResult remove(PlayerIdentity identity) {
            throw new IllegalStateException("delegate failed");
        }

        @Override
        public WhitelistUpdateResult update(PlayerIdentity currentIdentity, PlayerIdentity updatedIdentity) {
            throw new IllegalStateException("delegate failed");
        }
    }
}
