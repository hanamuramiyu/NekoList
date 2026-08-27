package hanamuramiyu.monban.access;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.identity.IdentityResolutionMode;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;
import hanamuramiyu.monban.sync.SyncSecret;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerAccessServiceTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void rejectsMismatchedIdentityResolverMode() {
        MonbanConfig config = new MonbanConfig(
                DeploymentSettings.defaults(),
                new WhitelistSettings(true),
                new IdentitySettings(IdentityResolutionMode.AUTO, HybridIdentitySettings.defaults())
        );
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(IdentityResolutionMode.OFFLINE);
        AccessGrantLookup lookup = (scope, identity) -> Optional.empty();

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessService(config, resolver, lookup)
        );
    }

    @Test
    void disabledWhitelistStillResolvesIdentityWithoutCallingGrantLookup() {
        CountingAccessGrantLookup lookup = new CountingAccessGrantLookup();
        PlayerAccessService service = createService(false, IdentityResolutionMode.AUTO, lookup);

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu", UUID_ONE, true);

        assertEquals(AccessDecision.ALLOWED, evaluation.decision());
        assertEquals(IdentityType.ONLINE, evaluation.identity().type());
        assertEquals("hanamuramiyu", evaluation.identity().name());
        assertEquals(UUID_ONE, evaluation.identity().verifiedUuid().orElseThrow());
        assertEquals(0, lookup.calls());
    }

    @Test
    void enabledNetworkGrantReturnsCurrentConnectionIdentity() {
        AccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_ONE)));
        PlayerAccessService service = createService(true, IdentityResolutionMode.AUTO, repository);

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu", UUID_ONE, true);

        assertEquals(AccessDecision.ALLOWED, evaluation.decision());
        assertEquals("hanamuramiyu", evaluation.identity().name());
        assertEquals(UUID_ONE, evaluation.identity().verifiedUuid().orElseThrow());
    }

    @Test
    void networkAccessCanComeFromAssignedGroup() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        InMemoryAccessGrantRepository directGrants = new InMemoryAccessGrantRepository();
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        groups.add(new PlayerGroupDefinition("moderator", List.of(AccessScope.network()), List.of()));
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                directGrants,
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
        PlayerAccessService service = new PlayerAccessService(
                new MonbanConfig(
                        DeploymentSettings.defaults(),
                        new WhitelistSettings(true),
                        new IdentitySettings(IdentityResolutionMode.AUTO, HybridIdentitySettings.defaults())
                ),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                directGrants,
                new WhitelistPolicy(true),
                resolver
        );

        assertEquals(AccessDecision.ALLOWED, service.evaluate(identity.name(), UUID_ONE, true).decision());
    }

    @Test
    void synchronizedNetworkAccessUsesCentralStateInsteadOfLocalRepositories() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        InMemoryAccessGrantRepository localGrants = new InMemoryAccessGrantRepository();
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                localGrants,
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository()
        );
        PlayerAccessStateReceiver receiver = newReceiver();
        receiver.accept(new PlayerAccessStateCodec().encode(
                new PlayerAccessStateSnapshot(
                        1,
                        List.of(new AccessGrant(AccessScope.network(), identity)),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                syncSecret()
        ));
        PlayerAccessService service = new PlayerAccessService(
                networkConfig(),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                localGrants,
                new WhitelistPolicy(true),
                resolver,
                receiver
        );

        assertEquals(AccessDecision.ALLOWED, service.evaluate(identity.name(), UUID_ONE, true).decision());
    }

    @Test
    void synchronizedNetworkAccessUsesCentralGroupAssignment() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        InMemoryAccessGrantRepository localGrants = new InMemoryAccessGrantRepository();
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                localGrants,
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository()
        );
        PlayerAccessStateReceiver receiver = newReceiver();
        receiver.accept(new PlayerAccessStateCodec().encode(
                new PlayerAccessStateSnapshot(
                        1,
                        List.of(),
                        List.of(new PlayerGroupDefinition("moderator", List.of(AccessScope.network()), List.of())),
                        List.of(new PlayerGroupAssignment(identity, "moderator")),
                        List.of()
                ),
                syncSecret()
        ));
        PlayerAccessService service = new PlayerAccessService(
                networkConfig(),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                localGrants,
                new WhitelistPolicy(true),
                resolver,
                receiver
        );

        assertEquals(AccessDecision.ALLOWED, service.evaluate(identity.name(), UUID_ONE, true).decision());
    }

    @Test
    void synchronizedNetworkAccessFailsClosedBeforeFirstSnapshot() {
        InMemoryAccessGrantRepository localGrants = new InMemoryAccessGrantRepository();
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                localGrants,
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository()
        );
        PlayerAccessService service = new PlayerAccessService(
                networkConfig(),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                localGrants,
                new WhitelistPolicy(true),
                resolver,
                newReceiver()
        );

        assertEquals(
                AccessDecision.NOT_WHITELISTED,
                service.evaluate("hanamuramiyu", UUID_ONE, true).decision()
        );
    }

    @Test
    void onlineRenameReturnsIncomingIdentityInsteadOfStoredMetadata() {
        AccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu_old", UUID_ONE)));
        PlayerAccessService service = createService(true, IdentityResolutionMode.AUTO, repository);

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu_new", UUID_ONE, true);

        assertEquals(AccessDecision.ALLOWED, evaluation.decision());
        assertEquals("hanamuramiyu_new", evaluation.identity().name());
        assertEquals(UUID_ONE, evaluation.identity().verifiedUuid().orElseThrow());
        assertEquals("hanamuramiyu_old", repository.find(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_ONE))
                .orElseThrow()
                .identity()
                .name());
    }

    @Test
    void missingNetworkGrantStillReturnsResolvedIdentity() {
        PlayerAccessService service = createService(
                true,
                IdentityResolutionMode.AUTO,
                new InMemoryAccessGrantRepository()
        );

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu", UUID_ONE, true);

        assertEquals(AccessDecision.NOT_WHITELISTED, evaluation.decision());
        assertEquals(IdentityType.ONLINE, evaluation.identity().type());
        assertEquals("hanamuramiyu", evaluation.identity().name());
        assertEquals(UUID_ONE, evaluation.identity().verifiedUuid().orElseThrow());
    }

    @Test
    void offlineEvaluationPreservesCurrentOfflineIdentity() {
        AccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu", UUID_ONE)));
        PlayerAccessService service = createService(true, IdentityResolutionMode.OFFLINE, repository);

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu", UUID_TWO, true);

        assertEquals(AccessDecision.ALLOWED, evaluation.decision());
        assertEquals(IdentityType.OFFLINE, evaluation.identity().type());
        assertEquals("hanamuramiyu", evaluation.identity().name());
        assertEquals(UUID_TWO, evaluation.identity().technicalUuid().orElseThrow());
    }

    @Test
    void enabledWhitelistChecksOnlyNetworkScopeWithCurrentIdentity() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup();
        PlayerAccessService service = createService(true, IdentityResolutionMode.AUTO, lookup);

        PlayerAccessEvaluation evaluation = service.evaluate("hanamuramiyu", UUID_ONE, true);

        assertEquals(AccessDecision.NOT_WHITELISTED, evaluation.decision());
        assertEquals(AccessScope.network(), lookup.scope());
        assertEquals("hanamuramiyu", lookup.identity().name());
        assertEquals(UUID_ONE, lookup.identity().verifiedUuid().orElseThrow());
    }

    private static PlayerAccessService createService(
            boolean whitelistEnabled,
            IdentityResolutionMode identityMode,
            AccessGrantLookup lookup
    ) {
        MonbanConfig config = new MonbanConfig(
                DeploymentSettings.defaults(),
                new WhitelistSettings(whitelistEnabled),
                new IdentitySettings(identityMode, HybridIdentitySettings.defaults())
        );
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(identityMode);
        return new PlayerAccessService(config, resolver, lookup);
    }

    private static MonbanConfig networkConfig() {
        return new MonbanConfig(
                DeploymentSettings.defaults(),
                new WhitelistSettings(true),
                new IdentitySettings(IdentityResolutionMode.AUTO, HybridIdentitySettings.defaults())
        );
    }

    @Test
    void synchronizedBackendUsesProxyWhitelistState() {
        PlayerAccessStateReceiver receiver = newReceiver();
        receiver.accept(new PlayerAccessStateCodec().encode(
                new PlayerAccessStateSnapshot(1, false, List.of(), List.of(), List.of(), List.of()),
                syncSecret()
        ));

        PlayerAccessResolver resolver = new PlayerAccessResolver(
                new InMemoryAccessGrantRepository(),
                new InMemoryPlayerGroupRepository(),
                new InMemoryPlayerGroupAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository()
        );
        PlayerAccessService service = new PlayerAccessService(
                networkConfig(),
                new PlayerIdentityResolver(IdentityResolutionMode.AUTO),
                (scope, checkedIdentity) -> Optional.empty(),
                new WhitelistPolicy(true),
                resolver,
                receiver
        );

        assertEquals(AccessDecision.ALLOWED, service.evaluate(
                "Miyu",
                UUID_ONE,
                false
        ).decision());
    }

    private static PlayerAccessStateReceiver newReceiver() {
        return new PlayerAccessStateReceiver(new PlayerAccessStateCodec(), syncSecret());
    }

    private static SyncSecret syncSecret() {
        return SyncSecret.of("monban-test-sync-secret".getBytes(StandardCharsets.UTF_8));
    }

    private static final class CountingAccessGrantLookup implements AccessGrantLookup {
        private int calls;

        int calls() {
            return calls;
        }

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            calls++;
            return Optional.empty();
        }
    }

    private static final class RecordingAccessGrantLookup implements AccessGrantLookup {
        private AccessScope scope;
        private PlayerIdentity identity;

        AccessScope scope() {
            return scope;
        }

        PlayerIdentity identity() {
            return identity;
        }

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            this.scope = scope;
            this.identity = identity;
            return Optional.empty();
        }
    }
}
