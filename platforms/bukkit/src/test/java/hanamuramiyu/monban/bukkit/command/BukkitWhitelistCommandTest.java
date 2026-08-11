package hanamuramiyu.monban.bukkit.command;

import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitWhitelistCommandTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void permissionDeniedDoesNotScheduleMutation() {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor mutationExecutor = new RecordingExecutor();
        BukkitWhitelistCommand command = command(repository, mutationExecutor, new RecordingLogger());
        RecordingSender sender = new RecordingSender();

        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});

        assertTrue(mutationExecutor.tasks.isEmpty());
        assertEquals(0, repository.addCalls);
        assertTrue(sender.messagesContain("do not have permission"));
    }

    @Test
    void offlineAndOnlineAddRemoveUseOnlyNetworkScope() {
        RecordingRepository repository = new RecordingRepository();
        BukkitWhitelistCommand command = command(repository, Runnable::run, new RecordingLogger());
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});
        command.execute(sender.sender(), new String[]{"add", "online", "hanamuramiyu", VERIFIED_UUID.toString()});

        assertEquals(2, repository.delegate.findAll().size());
        assertTrue(repository.delegate.findAll().stream().allMatch(grant -> grant.scope().equals(AccessScope.network())));
        assertTrue(repository.delegate.findAll().stream().anyMatch(grant -> grant.identity().equals(PlayerIdentity.offline("hanamuramiyu"))));
        assertTrue(repository.delegate.findAll().stream().anyMatch(
                grant -> grant.identity().verifiedUuid().filter(VERIFIED_UUID::equals).isPresent()
        ));

        command.execute(sender.sender(), new String[]{"remove", "offline", "hanamuramiyu"});
        command.execute(sender.sender(), new String[]{"remove", "online", "hanamuramiyu_new", VERIFIED_UUID.toString()});
        assertTrue(repository.delegate.findAll().isEmpty());
    }

    @Test
    void duplicateAndNotFoundAreFriendlyOperatorResults() {
        RecordingRepository repository = new RecordingRepository();
        BukkitWhitelistCommand command = command(repository, Runnable::run, new RecordingLogger());
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});
        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});
        command.execute(sender.sender(), new String[]{"remove", "offline", "hanamuramiyu2"});

        assertTrue(sender.messagesContain("Whitelist entry already exists."));
        assertTrue(sender.messagesContain("No matching whitelist entry exists."));
    }

    @Test
    void mutationRunsOnlyAfterInjectedAsyncBoundaryExecutes() {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor mutationExecutor = new RecordingExecutor();
        BukkitWhitelistCommand command = command(repository, mutationExecutor, new RecordingLogger());
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});

        assertEquals(0, repository.addCalls);
        assertEquals(1, mutationExecutor.tasks.size());
        mutationExecutor.tasks.removeFirst().run();
        assertEquals(1, repository.addCalls);
        assertTrue(sender.messagesContain("Added OFFLINE hanamuramiyu"));
    }

    @Test
    void listFiltersPaginatesAndNeverDisplaysOfflineTechnicalUuid() {
        RecordingRepository repository = new RecordingRepository();
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu9", VERIFIED_UUID)));
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu", TECHNICAL_UUID)));
        for (int index = 0; index < 11; index++) {
            repository.seed(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline("hanamuramiyu" + (20 - index))
            ));
        }
        BukkitWhitelistCommand command = command(repository, Runnable::run, new RecordingLogger());
        RecordingSender all = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(all.sender(), new String[]{"list", "2"});
        assertTrue(all.messagesContain("Whitelist — page 2/2"));
        assertTrue(all.messagesContain("3 entries shown — 13 total"));
        assertFalse(all.messagesContain(TECHNICAL_UUID.toString()));

        RecordingSender online = new RecordingSender(BukkitWhitelistCommand.PERMISSION);
        command.execute(online.sender(), new String[]{"list", "online"});
        assertTrue(online.messagesContain("Whitelist (ONLINE) — page 1/1"));
        assertTrue(online.messagesContain(VERIFIED_UUID.toString()));
        assertFalse(online.messagesContain("OFFLINE"));

        RecordingSender offline = new RecordingSender(BukkitWhitelistCommand.PERMISSION);
        command.execute(offline.sender(), new String[]{"list", "offline", "2"});
        assertTrue(offline.messagesContain("Whitelist (OFFLINE) — page 2/2"));
        assertFalse(offline.messagesContain("ONLINE"));
    }

    @Test
    void invalidUuidAndPageDoNotScheduleMutation() {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor mutationExecutor = new RecordingExecutor();
        BukkitWhitelistCommand command = command(repository, mutationExecutor, new RecordingLogger());
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"add", "online", "hanamuramiyu", "bad-uuid"});
        command.execute(sender.sender(), new String[]{"list", "0"});

        assertTrue(mutationExecutor.tasks.isEmpty());
        assertEquals(0, repository.addCalls);
        assertTrue(sender.messagesContain("Invalid UUID"));
        assertTrue(sender.messagesContain("Invalid page"));
    }

    @Test
    void persistenceFailuresAreLoggedAndDoNotLeakPaths() {
        RecordingRepository repository = new RecordingRepository();
        repository.mutationFailure = new IllegalStateException("failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        BukkitWhitelistCommand command = command(repository, Runnable::run, logger);
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"add", "offline", "hanamuramiyu"});

        assertEquals(1, logger.throwableRecords);
        assertTrue(sender.messagesContain("Failed to update the monban whitelist. Check the server log."));
        assertFalse(sender.messagesContain("/secret/whitelist.yml"));
    }

    @Test
    void readFailuresAreLoggedAndDoNotLeakPaths() {
        RecordingRepository repository = new RecordingRepository();
        repository.readFailure = new IllegalStateException("failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        BukkitWhitelistCommand command = command(repository, Runnable::run, logger);
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);

        command.execute(sender.sender(), new String[]{"list"});

        assertEquals(1, logger.throwableRecords);
        assertTrue(sender.messagesContain("Failed to read the monban whitelist. Check the server log."));
        assertFalse(sender.messagesContain("/secret/whitelist.yml"));
    }

    private static BukkitWhitelistCommand command(
            RecordingRepository repository,
            Executor mutationExecutor,
            Logger logger
    ) {
        return new BukkitWhitelistCommand(
                new AccessGrantAdministrationService(repository, scope -> {}),
                mutationExecutor,
                ignored -> Runnable::run,
                logger
        );
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
            super("BukkitWhitelistCommandTest", null);
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
