package hanamuramiyu.monban.bukkit;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BukkitStartupAccessGuardTest {
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String NOT_READY_MESSAGE =
            "monban is not ready to verify access. Please try again later.";

    private final BukkitStartupAccessGuard guard = new BukkitStartupAccessGuard();

    @Test
    void allowedLoginIsDeniedWhileAccessPlaneIsNotReady() {
        AsyncPlayerPreLoginEvent event = newEvent();

        guard.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertEquals(NOT_READY_MESSAGE, event.getKickMessage());
    }

    @Test
    void existingDenialIsPreserved() {
        AsyncPlayerPreLoginEvent event = newEvent();
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "Original denial.");

        guard.onAsyncPlayerPreLogin(event);

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, event.getLoginResult());
        assertEquals("Original denial.", event.getKickMessage());
    }

    private static AsyncPlayerPreLoginEvent newEvent() {
        return new AsyncPlayerPreLoginEvent(
                "hanamuramiyu",
                InetAddress.getLoopbackAddress(),
                PLAYER_UUID,
                false
        );
    }
}
