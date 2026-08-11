package hanamuramiyu.monban.velocity.hybrid;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VelocityHybridIdentitySelectorTest {
    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void onlineNameOnlySelectsOnline() {
        assertEquals(
                HybridIdentitySelection.ONLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.network(),
                        PlayerIdentity.online("hanamuramiyu", UUID_A)
                )).select("hanamuramiyu", null)
        );
    }

    @Test
    void offlineNameOnlySelectsOffline() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.network(),
                        PlayerIdentity.offline("hanamuramiyu")
                )).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void bothUsesOnlinePreference() {
        assertEquals(
                HybridIdentitySelection.ONLINE,
                selector(
                        preference(HybridIdentityPreference.ONLINE),
                        grant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_A)),
                        grant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))
                ).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void bothUsesOfflinePreference() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(
                        preference(HybridIdentityPreference.OFFLINE),
                        grant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", UUID_A)),
                        grant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))
                ).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void noGrantsSelectsOffline() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE)).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void onlineGrantOnlyInServerScopeStillSelectsOnline() {
        assertEquals(
                HybridIdentitySelection.ONLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.server("private"),
                        PlayerIdentity.online("hanamuramiyu", UUID_A)
                )).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void offlineGrantOnlyInServerGroupScopeStillSelectsOffline() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.serverGroup("testing"),
                        PlayerIdentity.offline("hanamuramiyu")
                )).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void networkOfflineAndServerOnlineUsesPreference() {
        assertEquals(
                HybridIdentitySelection.ONLINE,
                selector(
                        preference(HybridIdentityPreference.ONLINE),
                        grant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")),
                        grant(AccessScope.server("private"), PlayerIdentity.online("hanamuramiyu", UUID_A))
                ).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void preLoginUuidPreservesOnlineRenameMatching() {
        assertEquals(
                HybridIdentitySelection.ONLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.server("private"),
                        PlayerIdentity.online("hanamuramiyu_old", UUID_A)
                )).select("hanamuramiyu_new", UUID_A)
        );
    }

    @Test
    void preLoginUuidAloneDoesNotCreateOnlineSelection() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE)).select("hanamuramiyu", UUID_A)
        );
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.server("private"),
                        PlayerIdentity.online("hanamuramiyu2", UUID_B)
                )).select("hanamuramiyu", UUID_A)
        );
    }

    @Test
    void offlineTechnicalUuidIsNotUsedAsAnOnlineRoutingHint() {
        assertEquals(
                HybridIdentitySelection.OFFLINE,
                selector(preference(HybridIdentityPreference.ONLINE), grant(
                        AccessScope.server("private"),
                        PlayerIdentity.offline("hanamuramiyu2", UUID_A)
                )).select("hanamuramiyu", UUID_A)
        );
    }

    private static VelocityHybridIdentitySelector selector(
            HybridIdentitySettings settings,
            AccessGrant... grants
    ) {
        AccessGrantInventory inventory = () -> List.of(grants);
        return new VelocityHybridIdentitySelector(inventory, settings);
    }

    private static HybridIdentitySettings preference(HybridIdentityPreference preference) {
        return new HybridIdentitySettings(true, preference);
    }

    private static AccessGrant grant(AccessScope scope, PlayerIdentity identity) {
        return new AccessGrant(scope, identity);
    }
}
