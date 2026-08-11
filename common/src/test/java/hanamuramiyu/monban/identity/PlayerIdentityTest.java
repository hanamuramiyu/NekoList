package hanamuramiyu.monban.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void acceptsValidName() {
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu_21");

        assertEquals("hanamuramiyu_21", identity.name());
        assertEquals("hanamuramiyu_21", identity.normalizedName());
    }

    @Test
    void acceptsNameWithExactlySixteenCharacters() {
        PlayerIdentity identity = PlayerIdentity.offline("abcdefghijklmnop");

        assertEquals("abcdefghijklmnop", identity.name());
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> PlayerIdentity.offline(null));
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.offline(""));
    }

    @Test
    void rejectsNameLongerThanSixteenCharacters() {
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.offline("abcdefghijklmnopq"));
    }

    @Test
    void rejectsForbiddenCharacters() {
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.offline("hanamuramiyu-21"));
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.offline("hanamuramiyu 21"));
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.offline("みゆ"));
    }

    @Test
    void comparesNamesCaseInsensitively() {
        PlayerIdentity identity = PlayerIdentity.offline("HanamuraMiyu");

        assertTrue(identity.matchesName("hanamuramiyu"));
        assertTrue(identity.matchesName("HANAMURAMIYU"));
        assertEquals(PlayerIdentity.normalizeName("HanamuraMiyu"), PlayerIdentity.normalizeName("hANAMURAmIYU"));
    }

    @Test
    void preservesOriginalNameCase() {
        PlayerIdentity identity = PlayerIdentity.offline("HanamuraMiyu");

        assertEquals("HanamuraMiyu", identity.name());
        assertEquals("hanamuramiyu", identity.normalizedName());
    }

    @Test
    void onlineIdentityRequiresVerifiedUuid() {
        assertThrows(NullPointerException.class, () -> PlayerIdentity.online("hanamuramiyu", null));
    }

    @Test
    void onlineIdentityExposesTechnicalAndVerifiedUuid() {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);

        assertEquals(IdentityType.ONLINE, identity.type());
        assertEquals(UUID_ONE, identity.technicalUuid().orElseThrow());
        assertEquals(UUID_ONE, identity.verifiedUuid().orElseThrow());
        assertTrue(identity.hasVerifiedUuid());
    }

    @Test
    void offlineIdentityCanExistWithoutTechnicalUuid() {
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu");

        assertEquals(IdentityType.OFFLINE, identity.type());
        assertTrue(identity.technicalUuid().isEmpty());
        assertTrue(identity.verifiedUuid().isEmpty());
        assertFalse(identity.hasVerifiedUuid());
    }

    @Test
    void onlineIdentitiesWithSameUuidAreSameIdentityDespiteDifferentNames() {
        PlayerIdentity original = PlayerIdentity.online("hanamuramiyu_old", UUID_ONE);
        PlayerIdentity renamed = PlayerIdentity.online("hanamuramiyu_new", UUID_ONE);

        assertEquals(original, renamed);
        assertTrue(original.sameIdentityAs(renamed));
        assertEquals(original.hashCode(), renamed.hashCode());
    }

    @Test
    void onlineIdentitiesWithDifferentUuidsAreDifferentEvenWithSameName() {
        PlayerIdentity first = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity second = PlayerIdentity.online("hanamuramiyu", UUID_TWO);

        assertNotEquals(first, second);
    }

    @Test
    void offlineIdentityUsesNormalizedNameAsDurableIdentity() {
        PlayerIdentity first = PlayerIdentity.offline("HanamuraMiyu");
        PlayerIdentity second = PlayerIdentity.offline("HANAMURAMIYU");

        assertEquals(first, second);
        assertTrue(first.sameIdentityAs(second));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void offlineTechnicalUuidIsNeverVerified() {
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);

        assertEquals(IdentityType.OFFLINE, identity.type());
        assertEquals(UUID_ONE, identity.technicalUuid().orElseThrow());
        assertTrue(identity.verifiedUuid().isEmpty());
        assertFalse(identity.hasVerifiedUuid());
    }

    @Test
    void offlineTechnicalUuidIsNotPartOfDurableIdentity() {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);
        PlayerIdentity second = PlayerIdentity.offline("HanamuraMiyu", UUID_TWO);

        assertEquals(first, second);
        assertTrue(first.sameIdentityAs(second));
    }

    @Test
    void offlineIdentityNeverEqualsOnlineIdentity() {
        PlayerIdentity online = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity offline = PlayerIdentity.offline("hanamuramiyu", UUID_ONE);

        assertNotEquals(online, offline);
    }
}
