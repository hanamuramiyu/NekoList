package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessGrantAdministrationServiceTest {
    private static final PlayerIdentity IDENTITY =
            PlayerIdentity.online("hanamuramiyu", UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void grantValidatesScopeBeforeMutationAndPropagatesAdded() {
        RecordingRepository repository = new RecordingRepository();
        RecordingValidator validator = new RecordingValidator();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, validator);
        AccessGrant grant = new AccessGrant(AccessScope.server("dev"), IDENTITY);

        assertEquals(AccessGrantAddResult.ADDED, service.grant(grant));
        assertSame(grant.scope(), validator.lastScope);
        assertEquals(1, validator.calls);
        assertEquals(1, repository.addCalls);
    }

    @Test
    void duplicateGrantResultIsPropagated() {
        RecordingRepository repository = new RecordingRepository();
        repository.addResult = AccessGrantAddResult.ALREADY_EXISTS;
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        assertEquals(
                AccessGrantAddResult.ALREADY_EXISTS,
                service.grant(new AccessGrant(AccessScope.network(), IDENTITY))
        );
    }

    @Test
    void revokeValidatesScopeBeforeMutationAndPropagatesRemoved() {
        RecordingRepository repository = new RecordingRepository();
        RecordingValidator validator = new RecordingValidator();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, validator);
        AccessScope scope = AccessScope.serverGroup("testing");

        assertEquals(AccessGrantRemoveResult.REMOVED, service.revoke(scope, IDENTITY));
        assertSame(scope, validator.lastScope);
        assertEquals(1, validator.calls);
        assertEquals(1, repository.removeCalls);
    }

    @Test
    void missingRevokeResultIsPropagated() {
        RecordingRepository repository = new RecordingRepository();
        repository.removeResult = AccessGrantRemoveResult.NOT_FOUND;
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        assertEquals(
                AccessGrantRemoveResult.NOT_FOUND,
                service.revoke(AccessScope.server("dev"), IDENTITY)
        );
    }

    @Test
    void invalidGrantScopePreventsRepositoryMutation() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> { throw new AccessGrantScopeValidationException("invalid scope"); }
        );

        assertThrows(
                AccessGrantScopeValidationException.class,
                () -> service.grant(new AccessGrant(AccessScope.server("missing"), IDENTITY))
        );
        assertEquals(0, repository.addCalls);
    }

    @Test
    void invalidRevokeScopePreventsRepositoryMutation() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> { throw new AccessGrantScopeValidationException("invalid scope"); }
        );

        assertThrows(
                AccessGrantScopeValidationException.class,
                () -> service.revoke(AccessScope.server("missing"), IDENTITY)
        );
        assertEquals(0, repository.removeCalls);
    }

    @Test
    void findAllIsPropagated() {
        RecordingRepository repository = new RecordingRepository();
        List<AccessGrant> grants = List.of(new AccessGrant(AccessScope.network(), IDENTITY));
        repository.all = grants;
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        assertSame(grants, service.findAll());
        assertEquals(1, repository.findAllCalls);
    }

    @Test
    void findAllScopeValidatesBeforeRepositoryRead() {
        RecordingRepository repository = new RecordingRepository();
        RecordingValidator validator = new RecordingValidator();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, validator);
        AccessScope scope = AccessScope.serverGroup("testing");

        service.findAll(scope);

        assertSame(scope, validator.lastScope);
        assertEquals(1, validator.calls);
        assertEquals(1, repository.findAllCalls);
    }

    @Test
    void findAllScopeReturnsOnlyExactScope() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrant network = new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"));
        AccessGrant testing = new AccessGrant(AccessScope.serverGroup("testing"), PlayerIdentity.offline("hanamuramiyu4"));
        AccessGrant staff = new AccessGrant(AccessScope.serverGroup("staff"), PlayerIdentity.offline("hanamuramiyu2"));
        AccessGrant server = new AccessGrant(AccessScope.server("testing"), PlayerIdentity.offline("hanamuramiyu3"));
        repository.all = List.of(network, staff, server, testing);
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        assertEquals(List.of(testing), service.findAll(AccessScope.serverGroup("testing")));
    }

    @Test
    void findAllNetworkDoesNotIncludeServerGrants() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrant network = new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"));
        AccessGrant server = new AccessGrant(AccessScope.server("network"), PlayerIdentity.offline("hanamuramiyu2"));
        repository.all = List.of(server, network);
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        assertEquals(List.of(network), service.findAll(AccessScope.network()));
    }

    @Test
    void invalidFindAllScopePreventsRepositoryRead() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> { throw new AccessGrantScopeValidationException("invalid scope"); }
        );

        assertThrows(
                AccessGrantScopeValidationException.class,
                () -> service.findAll(AccessScope.server("missing"))
        );
        assertEquals(0, repository.findAllCalls);
    }

    @Test
    void scopedFindAllRepositoryFailurePropagatesUnchanged() {
        RecordingRepository repository = new RecordingRepository();
        IllegalStateException failure = new IllegalStateException("read failed");
        repository.failure = failure;
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.findAll(AccessScope.network())
        );

        assertSame(failure, thrown);
    }

    @Test
    void scopedFindAllReturnsImmutableList() {
        RecordingRepository repository = new RecordingRepository();
        AccessGrant grant = new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"));
        repository.all = new java.util.ArrayList<>(List.of(grant));
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        List<AccessGrant> result = service.findAll(AccessScope.network());

        assertEquals(List.of(grant), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(grant));
    }

    @Test
    void persistenceFailureIsNotSwallowed() {
        RecordingRepository repository = new RecordingRepository();
        UncheckedIOException failure = new UncheckedIOException(new IOException("disk failed"));
        repository.failure = failure;
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});

        UncheckedIOException thrown = assertThrows(
                UncheckedIOException.class,
                () -> service.grant(new AccessGrant(AccessScope.network(), IDENTITY))
        );

        assertSame(failure, thrown);
    }

    private static final class RecordingValidator implements AccessGrantScopeValidator {
        private int calls;
        private AccessScope lastScope;

        @Override
        public void validate(AccessScope scope) {
            calls++;
            lastScope = scope;
        }
    }

    private static final class RecordingRepository implements AccessGrantRepository {
        private int findAllCalls;
        private int addCalls;
        private int removeCalls;
        private List<AccessGrant> all = List.of();
        private AccessGrantAddResult addResult = AccessGrantAddResult.ADDED;
        private AccessGrantRemoveResult removeResult = AccessGrantRemoveResult.REMOVED;
        private RuntimeException failure;

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            return Optional.empty();
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
            return addResult;
        }

        @Override
        public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
            failIfNeeded();
            removeCalls++;
            return removeResult;
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
