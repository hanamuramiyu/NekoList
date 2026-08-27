package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerAccessStateCodecTest {
    private static final PlayerIdentity ONLINE = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );

    @Test
    void roundTripsAllStateTypes() {
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                7,
                List.of(
                        new AccessGrant(AccessScope.network(), ONLINE),
                        new AccessGrant(AccessScope.server("survival"), PlayerIdentity.offline("offline"))
                ),
                List.of(new PlayerGroupDefinition(
                        "moderator",
                        List.of(AccessScope.serverGroup("public")),
                        List.of(PermissionGrant.server("survival", "coreprotect.inspect"))
                )),
                List.of(new PlayerGroupAssignment(ONLINE, "moderator")),
                List.of(new PlayerPermissionGrant(ONLINE, PermissionGrant.network("proxy.command")))
        );

        PlayerAccessStateSnapshot decoded = new PlayerAccessStateCodec().decode(
                new PlayerAccessStateCodec().encode(snapshot, SECRET),
                SECRET
        );

        assertEquals(snapshot, decoded);
    }

    @Test
    void rejectsWrongSecretAndTampering() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        byte[] payload = codec.encode(snapshot, SECRET);
        payload[0] ^= 1;

        assertThrows(IllegalArgumentException.class, () -> codec.decode(payload, SECRET));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(codec.encode(snapshot, SECRET), SyncSecret.fromBase64(
                        Base64.getEncoder().encodeToString("another-sync-secret".getBytes(StandardCharsets.UTF_8))
                ))
        );
    }

    @Test
    void rejectsTruncatedPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(new byte[32], SECRET)
        );
    }
}
