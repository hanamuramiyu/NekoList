package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityWhitelistCommandTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TECHNICAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void offlineAddDuplicateRemoveAndNotFoundUseNetworkGrantSemantics() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban whitelist add offline hanamuramiyu", source.source()));
        assertEquals(AccessScope.network(), repository.delegate.findAll().getFirst().scope());
        assertEquals(PlayerIdentity.offline("hanamuramiyu"), repository.delegate.findAll().getFirst().identity());
        assertTrue(source.messagesContain("Added [OFFLINE] hanamuramiyu"));

        assertEquals(1, dispatcher.execute("monban whitelist add offline hanamuramiyu", source.source()));
        assertTrue(source.messagesContain("Whitelist entry already exists."));

        assertEquals(1, dispatcher.execute("monban whitelist remove offline HANAMURAMIYU", source.source()));
        assertTrue(repository.delegate.findAll().isEmpty());
        assertTrue(source.messagesContain("Removed [OFFLINE] HANAMURAMIYU"));

        assertEquals(1, dispatcher.execute("monban whitelist remove offline hanamuramiyu", source.source()));
        assertTrue(source.messagesContain("No matching whitelist entry exists."));
    }

    @Test
    void onlineAddAndRemoveUseExplicitVerifiedUuid() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        dispatcher.execute("monban whitelist add online hanamuramiyu " + VERIFIED_UUID, source.source());

        AccessGrant stored = repository.delegate.findAll().getFirst();
        assertEquals(AccessScope.network(), stored.scope());
        assertEquals(VERIFIED_UUID, stored.identity().verifiedUuid().orElseThrow());
        assertTrue(source.messagesContain("[ONLINE] hanamuramiyu · 00000000…0001"));
        assertFalse(source.messagesContain(VERIFIED_UUID.toString()));

        dispatcher.execute("monban whitelist remove online hanamuramiyu_new " + VERIFIED_UUID, source.source());
        assertTrue(repository.delegate.findAll().isEmpty());
    }

    @Test
    void mutationIsScheduledBeforeRepositoryWriteRuns() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor executor = new RecordingExecutor();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, executor, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        dispatcher.execute("monban whitelist add offline hanamuramiyu", source.source());

        assertEquals(0, repository.addCalls);
        assertEquals(1, executor.tasks.size());
        executor.tasks.removeFirst().run();
        assertEquals(1, repository.addCalls);
    }

    @Test
    void invalidUuidAndInvalidPageDoNotMutate() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor executor = new RecordingExecutor();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, executor, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("monban whitelist add online hanamuramiyu bad-uuid", source.source()));
        assertTrue(executor.tasks.isEmpty());
        assertTrue(source.messagesContain("Invalid UUID"));

        assertEquals(0, dispatcher.execute("monban whitelist list 0", source.source()));
        assertTrue(source.messagesContain("Invalid page: 0. Page must be at least 1."));
        assertEquals(0, dispatcher.execute("monban whitelist list nope", source.source()));
        assertTrue(source.messagesContain("Invalid page: nope. Page must be at least 1."));
        assertEquals(0, repository.addCalls);
    }

    @Test
    void listFiltersPaginatesDeterministicallyAndHidesFullUuid() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu9", VERIFIED_UUID)));
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu", TECHNICAL_UUID)));
        for (int index = 0; index < 11; index++) {
            repository.seed(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline("hanamuramiyu" + (20 - index))
            ));
        }
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban whitelist list 2", source.source()));
        assertTrue(source.messagesContain("page 2/2"));
        assertTrue(source.messagesContain("11–13 of 13"));
        assertFalse(source.messagesContain(TECHNICAL_UUID.toString()));
        assertTrue(source.messageIndexContaining("[OFFLINE] hanamuramiyu") < source.messageIndexContaining("[ONLINE] hanamuramiyu9"));

        RecordingCommandSource online = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban whitelist list online", online.source()));
        assertTrue(online.messagesContain("[ONLINE]"));
        assertTrue(online.messagesContain("00000000…0001"));
        assertFalse(online.messagesContain(VERIFIED_UUID.toString()));
        assertFalse(online.messagesContain("[OFFLINE]"));

        RecordingCommandSource offline = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban whitelist list offline 2", offline.source()));
        assertTrue(offline.messagesContain("page 2/2"));
        assertFalse(offline.messagesContain("[ONLINE]"));
    }

    @Test
    void pageOutsideRangeIsFriendlyFailure() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.seed(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")));
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("monban whitelist list 2", source.source()));
        assertTrue(source.messagesContain("Page 2 is out of range. Available pages: 1-1."));
    }

    @Test
    void persistenceFailureIsLoggedAndOnlyGenericMutationMessageIsSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.mutationFailure = new IllegalStateException("write failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, logger.logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        dispatcher.execute("monban whitelist add offline hanamuramiyu", source.source());

        assertEquals(1, logger.errorCalls);
        assertTrue(source.messagesContain("Failed to update the monban whitelist. Check the proxy log."));
        assertFalse(source.messagesContain("/secret/whitelist.yml"));
        assertFalse(source.messagesContain("write failed"));
    }

    @Test
    void readFailureIsLoggedAndOnlyGenericReadMessageIsSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.readFailure = new IllegalStateException("read failed at /secret/whitelist.yml");
        RecordingLogger logger = new RecordingLogger();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, logger.logger());
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(0, dispatcher.execute("monban whitelist list", source.source()));

        assertEquals(1, logger.errorCalls);
        assertTrue(source.messagesContain("Failed to read the monban whitelist. Check the proxy log."));
        assertFalse(source.messagesContain("/secret/whitelist.yml"));
    }

    @Test
    void whitelistAndAccessNetworkCommandsShareOneSourceOfTruth() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, Runnable::run, new RecordingLogger().logger());
        RecordingCommandSource source = new RecordingCommandSource(
                VelocityWhitelistCommand.PERMISSION,
                VelocityAccessCommand.PERMISSION,
                VelocityStatusCommand.PERMISSION
        );

        dispatcher.execute("monban whitelist add offline hanamuramiyu", source.source());
        dispatcher.execute("monban access list network", source.source());
        assertTrue(source.messagesContain("Access grants for NETWORK"));
        assertTrue(source.messagesContain("OFFLINE hanamuramiyu"));

        dispatcher.execute("monban access grant network offline hanamuramiyu2", source.source());
        RecordingCommandSource whitelistView = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);
        dispatcher.execute("monban whitelist list", whitelistView.source());
        assertTrue(whitelistView.messagesContain("[OFFLINE] hanamuramiyu"));
        assertTrue(whitelistView.messagesContain("[OFFLINE] hanamuramiyu2"));
    }

    @Test
    void enableAndDisablePersistBeforeChangingRuntimePolicy() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        WhitelistPolicy policy = new WhitelistPolicy(false);
        List<Boolean> persisted = new java.util.ArrayList<>();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(
                repository,
                Runnable::run,
                new RecordingLogger().logger(),
                policy,
                persisted::add
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban whitelist enable", source.source()));
        assertTrue(policy.enabled());
        assertEquals(List.of(true), persisted);
        assertTrue(source.messagesContain("The monban whitelist is now enabled."));

        assertEquals(1, dispatcher.execute("monban whitelist enable", source.source()));
        assertEquals(List.of(true), persisted);
        assertTrue(source.messagesContain("The monban whitelist is already enabled."));

        assertEquals(1, dispatcher.execute("monban whitelist disable", source.source()));
        assertFalse(policy.enabled());
        assertEquals(List.of(true, false), persisted);
        assertTrue(source.messagesContain("The monban whitelist is now disabled."));
    }

    @Test
    void failedPolicyPersistenceLeavesRuntimePolicyUnchanged() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        WhitelistPolicy policy = new WhitelistPolicy(false);
        IllegalStateException failure = new IllegalStateException("write failed at /secret/config.yml");
        RecordingLogger logger = new RecordingLogger();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(
                repository,
                Runnable::run,
                logger.logger(),
                policy,
                enabled -> {
                    throw failure;
                }
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban whitelist enable", source.source()));

        assertFalse(policy.enabled());
        assertSame(failure, logger.lastThrowable);
        assertTrue(source.messagesContain("Failed to update the whitelist policy. Check the proxy log."));
        assertFalse(source.messagesContain("/secret/config.yml"));
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            RecordingRepository repository,
            Executor executor,
            Logger logger
    ) {
        return dispatcher(repository, executor, logger, new WhitelistPolicy(false), enabled -> {
        });
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            RecordingRepository repository,
            Executor executor,
            Logger logger,
            WhitelistPolicy whitelistPolicy,
            VelocityWhitelistCommand.WhitelistStateStore whitelistStateStore
    ) {
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});
        ProxyServer proxy = proxyServerStub(List.of(), List.of());
        ServerGroupCatalog groups = new ServerGroupCatalog(List.of());
        VelocityWhitelistCommand whitelist = new VelocityWhitelistCommand(
                service,
                proxy,
                executor,
                logger,
                OnlineProfileResolver.unavailable(),
                whitelistPolicy,
                whitelistStateStore
        );
        VelocityAccessCommand access = new VelocityAccessCommand(service, groups, proxy, executor, logger);
        VelocityStatusCommand status = new VelocityStatusCommand(
                MonbanConfig.defaults(),
                repository,
                groups,
                new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, Map.of(), Map.of()),
                false,
                logger
        );
        BrigadierCommand command = VelocityMonbanCommand.create(whitelist, access, status);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.getNode());
        return dispatcher;
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
            if (readFailure != null) {
                throw readFailure;
            }
            return delegate.findAll();
        }

        @Override
        public AccessGrantAddResult add(AccessGrant grant) {
            addCalls++;
            if (mutationFailure != null) {
                throw mutationFailure;
            }
            return delegate.add(grant);
        }

        @Override
        public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
            if (mutationFailure != null) {
                throw mutationFailure;
            }
            return delegate.remove(scope, identity);
        }
    }
}
