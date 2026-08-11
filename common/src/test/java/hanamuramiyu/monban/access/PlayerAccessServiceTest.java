package hanamuramiyu.monban.access;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
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
import org.junit.jupiter.api.Test;

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
