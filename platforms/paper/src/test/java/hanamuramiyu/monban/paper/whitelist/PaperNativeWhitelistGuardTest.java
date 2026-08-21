package hanamuramiyu.monban.paper.whitelist;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperNativeWhitelistGuardTest {
    @Test
    void nativeWhitelistEnabledCausesSafeStartupFailureWithoutChangingIt() {
        ServerStub state = new ServerStub(true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> PaperNativeWhitelistGuard.requireDisabledAtStartup(state.server())
        );

        assertTrue(failure.getMessage().contains("white-list=false"));
        assertTrue(failure.getMessage().contains("monban must be the only whitelist authority"));
        assertTrue(state.whitelistEnabled);
    }

    @Test
    void playerNativeWhitelistCommandIsRecognizedWithReplacementGuidance() {
        RecordingSender state = new RecordingSender(true);

        assertTrue(PaperNativeWhitelistGuard.isNativeWhitelistCommand(
                "/minecraft:whitelist add hanamuramiyu"
        ));
        PaperNativeWhitelistGuard.sendBlockedMessage(state.player());

        assertTrue(state.messagesContain("/monban whitelist add offline <name>"));
    }

    @Test
    void consoleNativeWhitelistCommandIsRecognizedWithReplacementGuidance() {
        RecordingSender state = new RecordingSender(true);

        assertTrue(PaperNativeWhitelistGuard.isNativeWhitelistCommand(
                "bukkit:whitelist list"
        ));
        PaperNativeWhitelistGuard.sendBlockedMessage(state.sender());

        assertTrue(state.messagesContain("vanilla Minecraft whitelist is disabled"));
    }

    @Test
    void nativeWhitelistWithoutPermissionLooksLikeUnknownCommand() {
        RecordingSender state = new RecordingSender();

        PaperNativeWhitelistGuard.sendBlockedMessage(state.player());

        assertTrue(state.messagesContain("Unknown command. Type \"/help\" for help."));
        assertFalse(state.messagesContain("monban whitelist"));
    }

    @Test
    void allNativeLabelsAreRecognizedForPlayerAndConsoleCommandLines() {
        for (String label : List.of("whitelist", "minecraft:whitelist", "bukkit:whitelist")) {
            assertTrue(
                    PaperNativeWhitelistGuard.isNativeWhitelistCommand("/" + label + " list"),
                    "player command line should be blocked: " + label
            );
            assertTrue(
                    PaperNativeWhitelistGuard.isNativeWhitelistCommand(label + " list"),
                    "console command line should be blocked: " + label
            );
        }
    }

    @Test
    void unrelatedNamespacedWhitelistCommandIsNotRecognized() {
        assertFalse(PaperNativeWhitelistGuard.isNativeWhitelistCommand(
                "otherplugin:whitelist list"
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class ServerStub {
        private boolean whitelistEnabled;

        private ServerStub(boolean whitelistEnabled) {
            this.whitelistEnabled = whitelistEnabled;
        }

        private Server server() {
            return (Server) Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasWhitelist" -> whitelistEnabled;
                        case "setWhitelist" -> {
                            whitelistEnabled = (boolean) args[0];
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static final class RecordingSender {
        private final boolean permission;
        private final List<String> messages = new ArrayList<>();

        private RecordingSender() {
            this(false);
        }

        private RecordingSender(boolean permission) {
            this.permission = permission;
        }

        private CommandSender sender() {
            return proxy(CommandSender.class);
        }

        private Player player() {
            return proxy(Player.class);
        }

        @SuppressWarnings("unchecked")
        private <T> T proxy(Class<T> type) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permission;
                        case "sendMessage" -> {
                            if (args != null && args.length > 0 && args[0] instanceof String message) {
                                messages.add(message);
                            }
                            yield null;
                        }
                        case "getName" -> "RecordingSender";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private boolean messagesContain(String value) {
            return messages.stream().anyMatch(message -> message.contains(value));
        }
    }
}
