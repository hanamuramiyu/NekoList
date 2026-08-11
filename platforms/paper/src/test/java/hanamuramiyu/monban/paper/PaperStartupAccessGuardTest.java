package hanamuramiyu.monban.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaperStartupAccessGuardTest {
    private static final Component NOT_READY_MESSAGE = Component.text(
            "monban is not ready to verify access. Please try again later."
    );

    @Test
    void allowedLoginIsDeniedWhileAccessPlaneIsNotReady() {
        AtomicReference<AsyncPlayerPreLoginEvent.Result> deniedResult = new AtomicReference<>();
        AtomicReference<Component> deniedMessage = new AtomicReference<>();

        PaperStartupAccessGuard.enforce(
                AsyncPlayerPreLoginEvent.Result.ALLOWED,
                (result, message) -> {
                    deniedResult.set(result);
                    deniedMessage.set(message);
                }
        );

        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, deniedResult.get());
        assertEquals(NOT_READY_MESSAGE, deniedMessage.get());
    }

    @Test
    void existingDenialIsPreserved() {
        AtomicBoolean disallowCalled = new AtomicBoolean();

        PaperStartupAccessGuard.enforce(
                AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                (result, message) -> disallowCalled.set(true)
        );

        assertFalse(disallowCalled.get());
    }
}
