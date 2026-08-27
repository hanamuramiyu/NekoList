package hanamuramiyu.monban.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

final class VelocityCommandTestSupport {
    private VelocityCommandTestSupport() {
    }

    static ProxyServer proxyServerStub(Collection<RegisteredServer> servers, Collection<Player> players) {
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAllServers" -> servers;
                    case "getAllPlayers" -> players;
                    case "toString" -> "ProxyServerStub";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    static RegisteredServer registeredServerStub(String name) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25565));
        return (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "toString" -> "RegisteredServerStub[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    static Player playerStub(String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUsername" -> name;
                    case "toString" -> "PlayerStub[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static String visibleText(Component component) {
        StringBuilder builder = new StringBuilder();
        appendVisibleText(component, builder);
        return builder.toString();
    }

    private static void appendVisibleText(Component component, StringBuilder builder) {
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, builder));
    }

    static final class RecordingExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }
    }

    static final class RecordingCommandSource {
        private final Set<String> permissions;
        final List<String> messages = new ArrayList<>();

        RecordingCommandSource(boolean allowAllMonbanCommands) {
            this.permissions = allowAllMonbanCommands
                    ? Set.of(
                            VelocityWhitelistCommand.PERMISSION,
                            VelocityLookupCommand.PERMISSION,
                            VelocityAccessCommand.PERMISSION,
                            VelocityStatusCommand.PERMISSION,
                            VelocityGroupCommand.PERMISSION
                    )
                    : Set.of();
        }

        RecordingCommandSource(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        CommandSource source() {
            return (CommandSource) Proxy.newProxyInstance(
                    CommandSource.class.getClassLoader(),
                    new Class<?>[]{CommandSource.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permissions.contains((String) args[0]);
                        case "sendMessage" -> {
                            if (args != null && args.length > 0 && args[0] instanceof Component component) {
                                messages.add(visibleText(component));
                            }
                            yield null;
                        }
                        case "toString" -> "RecordingCommandSource[permissions=" + permissions + "]";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        boolean messagesContain(String value) {
            return messages.stream().anyMatch(message -> message.contains(value));
        }

        long messagesContaining(String value) {
            return messages.stream().filter(message -> message.contains(value)).count();
        }

        int messageIndexContaining(String value) {
            for (int index = 0; index < messages.size(); index++) {
                if (messages.get(index).contains(value)) {
                    return index;
                }
            }
            return Integer.MAX_VALUE;
        }
    }

    static final class RecordingLogger {
        int errorCalls;
        Throwable lastThrowable;

        Logger logger() {
            return (Logger) Proxy.newProxyInstance(
                    Logger.class.getClassLoader(),
                    new Class<?>[]{Logger.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("error")) {
                            errorCalls++;
                            if (args != null) {
                                for (Object arg : args) {
                                    if (arg instanceof Throwable throwable) {
                                        lastThrowable = throwable;
                                    }
                                }
                            }
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }
    }
}
