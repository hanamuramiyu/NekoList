package hanamuramiyu.monban.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityResolverTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void autoAuthenticatedCreatesOnlineIdentity() {
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(IdentityResolutionMode.AUTO);

        PlayerIdentity identity = resolver.resolve("hanamuramiyu", UUID_ONE, true);

        assertEquals(IdentityType.ONLINE, identity.type());
        assertEquals(UUID_ONE, identity.verifiedUuid().orElseThrow());
    }

    @Test
    void autoUnauthenticatedCreatesOfflineIdentity() {
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(IdentityResolutionMode.AUTO);

        PlayerIdentity identity = resolver.resolve("hanamuramiyu", UUID_ONE, false);

        assertEquals(IdentityType.OFFLINE, identity.type());
        assertEquals(UUID_ONE, identity.technicalUuid().orElseThrow());
        assertFalse(identity.hasVerifiedUuid());
    }

    @Test
    void offlineModeNeverBecomesOnlineBecauseUuidExists() {
        PlayerIdentityResolver resolver = new PlayerIdentityResolver(IdentityResolutionMode.OFFLINE);

        PlayerIdentity identity = resolver.resolve("hanamuramiyu", UUID_ONE, true);

        assertEquals(IdentityType.OFFLINE, identity.type());
        assertEquals(UUID_ONE, identity.technicalUuid().orElseThrow());
        assertTrue(identity.verifiedUuid().isEmpty());
    }

    @Test
    void backendTreatsOfflineForwardedPlayerAsOfflineDespiteProxyOnlineMode() {
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                "OfflinePlayer:hanamuramiyu".getBytes(StandardCharsets.UTF_8)
        );
        PlayerIdentity identity = new PlayerIdentityResolver(IdentityResolutionMode.AUTO)
                .resolveBackend("hanamuramiyu", offlineUuid, true);

        assertEquals(IdentityType.OFFLINE, identity.type());
    }

    @Test
    void backendKeepsAuthenticatedForwardedPlayerOnline() {
        PlayerIdentity identity = new PlayerIdentityResolver(IdentityResolutionMode.AUTO)
                .resolveBackend("hanamuramiyu", UUID_ONE, true);

        assertEquals(IdentityType.ONLINE, identity.type());
    }

    @Test
    void backendKeepsOnlineForwardedPlayerOnlineWhenProxyModeIsOffline() {
        PlayerIdentity identity = new PlayerIdentityResolver(IdentityResolutionMode.AUTO)
                .resolveBackend("hanamuramiyu", UUID_ONE, false);

        assertEquals(IdentityType.ONLINE, identity.type());
    }
}
