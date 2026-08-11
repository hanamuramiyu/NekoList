package hanamuramiyu.monban.paper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperWhitelistCommandTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void permissionDeniedMakesWhitelistSubtreeUnavailable() {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender();

        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("whitelist add offline hanamuramiyu", sender.source())
        );
        assertEquals(0, repository.addCalls);
    }

    @Test
    void offlineAndOnlineAddRemoveUseNetworkScope() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        dispatcher.execute("whitelist add offline hanamuramiyu", sender.source());
        dispatcher.execute("whitelist add online hanamuramiyu " + VERIFIED_UUID, sender.source());

        assertEquals(2, repository.delegate.findAll().size());
        assertTrue(repository.delegate.findAll().stream().allMatch(grant -> grant.scope().equals(AccessScope.network())));
        assertTrue(sender.messagesContain("Added OFFLINE hanamuramiyu"));
        assertTrue(sender.messagesContain("ONLINE hanamuramiyu " + VERIFIED_UUID));

        dispatcher.execute("whitelist remove offline hanamuramiyu", sender.source());
        dispatcher.execute("whitelist remove online hanamuramiyu_new " + VERIFIED_UUID, sender.source());
        assertTrue(repository.delegate.findAll().isEmpty());
    }

    @Test
    void duplicateAndNotFoundAreFriendly() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        dispatcher.execute("whitelist add offline hanamuramiyu", sender.source());
        dispatcher.execute("whitelist add offline hanamuramiyu", sender.source());
        dispatcher.execute("whitelist remove offline hanamuramiyu2", sender.source());

        assertTrue(sender.messagesContain("Whitelist entry already exists."));
        assertTrue(sender.messagesContain("No matching whitelist entry exists."));
    }

    @Test
    void mutationAndResponseUseInjectedSchedulingBoundaries() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor mutationExecutor = new RecordingExecutor();
        RecordingExecutor callbackExecutor = new RecordingExecutor();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                mutationExecutor,
                ignored -> callbackExecutor,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        dispatcher.execute("whitelist add offline hanamuramiyu", sender.source());

        assertEquals(0, repository.addCalls);
        assertEquals(1, mutationExecutor.tasks.size());
        assertTrue(callbackExecutor.tasks.isEmpty());

        mutationExecutor.tasks.removeFirst().run();
        assertEquals(1, repository.addCalls);
        assertEquals(1, callbackExecutor.tasks.size());
        assertFalse(sender.messagesContain("Added"));

        callbackExecutor.tasks.removeFirst().run();
        assertTrue(sender.messagesContain("Added OFFLINE hanamuramiyu"));
    }

    @Test
    void listFiltersPaginatesAndHidesOfflineTechnicalUuid() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu9", VERIFIED_UUID)));
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu", TECHNICAL_UUID)));
        for (int index = 0; index < 11; index++) {
            repository.seed(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline("hanamuramiyu" + (20 - index))
            ));
        }
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                task -> { throw new AssertionError("list must not use mutation executor"); },
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender all = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("whitelist list 2", all.source()));
        assertTrue(all.messagesContain("Whitelist — page 2/2"));
        assertTrue(all.messagesContain("3 entries shown — 13 total"));
        assertFalse(all.messagesContain(TECHNICAL_UUID.toString()));

        RecordingSender online = new RecordingSender(PaperWhitelistCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("whitelist list online", online.source()));
        assertTrue(online.messagesContain("Whitelist (ONLINE) — page 1/1"));
        assertTrue(online.messagesContain(VERIFIED_UUID.toString()));
        assertFalse(online.messagesContain("OFFLINE"));

        RecordingSender offline = new RecordingSender(PaperWhitelistCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("whitelist list offline 2", offline.source()));
        assertTrue(offline.messagesContain("Whitelist (OFFLINE) — page 2/2"));
        assertFalse(offline.messagesContain("ONLINE"));
    }

    @Test
    void invalidUuidDoesNotScheduleAndInvalidPageIsRejectedByBrigadier() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor mutationExecutor = new RecordingExecutor();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                mutationExecutor,
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("whitelist add online hanamuramiyu bad-uuid", sender.source()));
        assertTrue(mutationExecutor.tasks.isEmpty());
        assertTrue(sender.messagesContain("Invalid UUID"));

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("whitelist list 0", sender.source()));
    }

    @Test
    void pageOutsideRangeReturnsFriendlyFailure() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                new RecordingLogger()
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("whitelist list 2", sender.source()));
        assertTrue(sender.messagesContain("Page 2 is out of range. Available pages: 1-1."));
    }

    @Test
    void persistenceFailureIsLoggedAndGenericOnly() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.mutationFailure = new IllegalStateException("failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                logger
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        dispatcher.execute("whitelist add offline hanamuramiyu", sender.source());

        assertEquals(1, logger.throwableRecords);
        assertTrue(sender.messagesContain("Failed to update the monban whitelist. Check the server log."));
        assertFalse(sender.messagesContain("/secret/whitelist.yml"));
    }

    @Test
    void readFailureIsLoggedAndGenericOnly() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.readFailure = new IllegalStateException("failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                repository,
                Runnable::run,
                ignored -> Runnable::run,
                logger
        );
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("whitelist list", sender.source()));

        assertEquals(1, logger.throwableRecords);
        assertTrue(sender.messagesContain("Failed to read the monban whitelist. Check the server log."));
        assertFalse(sender.messagesContain("/secret/whitelist.yml"));
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(
            RecordingRepository repository,
            Executor mutationExecutor,
            Function<CommandSender, Executor> callbackExecutor,
            Logger logger
    ) {
        PaperWhitelistCommand command = new PaperWhitelistCommand(
                new AccessGrantAdministrationService(repository, scope -> {}),
                mutationExecutor,
                callbackExecutor,
                logger
        );
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build().build());
        return dispatcher;
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

    private static final class RecordingSender {
        private final Set<String> permissions;
        private final List<String> messages = new ArrayList<>();

        private RecordingSender(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        private CommandSourceStack source() {
            CommandSender sender = sender();
            return (CommandSourceStack) Proxy.newProxyInstance(
                    CommandSourceStack.class.getClassLoader(),
                    new Class<?>[]{CommandSourceStack.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getSender" -> sender;
                        case "toString" -> "CommandSourceStackStub";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private CommandSender sender() {
            return (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permissions.contains((String) args[0]);
                        case "sendMessage" -> {
                            if (args != null && args.length > 0 && args[0] instanceof String message) {
                                messages.add(message);
                            }
                            yield null;
                        }
                        case "getName" -> "RecordingSender";
                        case "toString" -> "RecordingSender";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private boolean messagesContain(String value) {
            return messages.stream().anyMatch(message -> message.contains(value));
        }
    }

    private static final class RecordingExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }
    }

    private static final class RecordingLogger extends Logger {
        private int throwableRecords;

        private RecordingLogger() {
            super("PaperWhitelistCommandTest", null);
            setUseParentHandlers(false);
            setLevel(Level.ALL);
            addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getThrown() != null) {
                        throwableRecords++;
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }
    }

    private static final class RecordingRepository implements AccessGrantRepository {
        private final InMemoryAccessGrantRepository delegate = new InMemoryAccessGrantRepository();
        private int addCalls;
        private RuntimeException mutationFailure;
        private RuntimeException readFailure;

        private void seed(AccessGrant grant) {
            delegate.add(grant);
        }

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            return delegate.find(scope, identity);
        }

        @Override
        public List<AccessGrant> findAll() {
            if (readFailure != null) throw readFailure;
            return delegate.findAll();
        }

        @Override
        public AccessGrantAddResult add(AccessGrant grant) {
            addCalls++;
            if (mutationFailure != null) throw mutationFailure;
            return delegate.add(grant);
        }

        @Override
        public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
            if (mutationFailure != null) throw mutationFailure;
            return delegate.remove(scope, identity);
        }
    }
}
