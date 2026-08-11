package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAccessCommandTest {
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void grantNetworkOfflineBuildsExactDomainObjects() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network offline hanamuramiyu", source.source());

        assertEquals(1, repository.addCalls);
        assertEquals(AccessScope.network(), repository.lastAdded.scope());
        assertEquals(IdentityType.OFFLINE, repository.lastAdded.identity().type());
        assertEquals("hanamuramiyu", repository.lastAdded.identity().name());
        assertTrue(source.messagesContain("Granted NETWORK access to OFFLINE hanamuramiyu."));
    }

    @Test
    void grantServerOnlinePreservesNameAndExactUuid() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute(
                "monban access grant server dev online hanamuramiyu " + PLAYER_UUID,
                source.source()
        );

        assertEquals(1, repository.addCalls);
        assertEquals(AccessScope.server("dev"), repository.lastAdded.scope());
        assertEquals(IdentityType.ONLINE, repository.lastAdded.identity().type());
        assertEquals("hanamuramiyu", repository.lastAdded.identity().name());
        assertEquals(PLAYER_UUID, repository.lastAdded.identity().verifiedUuid().orElseThrow());
        assertTrue(source.messagesContain("ONLINE hanamuramiyu (" + PLAYER_UUID + ")"));
    }

    @Test
    void revokeServerGroupOfflineCallsAdministrationService() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access revoke group testing offline hanamuramiyu", source.source());

        assertEquals(1, repository.removeCalls);
        assertEquals(AccessScope.serverGroup("testing"), repository.lastRemovedScope);
        assertEquals(PlayerIdentity.offline("hanamuramiyu"), repository.lastRemovedIdentity);
        assertTrue(source.messagesContain("Revoked SERVER_GROUP(testing) access from OFFLINE hanamuramiyu."));
    }

    @Test
    void mutationIsScheduledBeforeRepositoryIoRuns() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor executor = new RecordingExecutor();
        CommandHarness harness = harness(repository, scope -> {}, executor, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network offline hanamuramiyu", source.source());

        assertEquals(0, repository.addCalls);
        assertEquals(1, executor.tasks.size());
        executor.tasks.removeFirst().run();
        assertEquals(1, repository.addCalls);
    }

    @Test
    void badUuidDoesNotScheduleMutation() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor executor = new RecordingExecutor();
        CommandHarness harness = harness(repository, scope -> {}, executor, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network online hanamuramiyu definitely-not-a-uuid", source.source());

        assertEquals(0, repository.addCalls);
        assertTrue(executor.tasks.isEmpty());
        assertTrue(source.messagesContain("Invalid UUID"));
    }

    @Test
    void badPlayerNameDoesNotScheduleMutation() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingExecutor executor = new RecordingExecutor();
        CommandHarness harness = harness(repository, scope -> {}, executor, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network offline abcdefghijklmnopq", source.source());

        assertEquals(0, repository.addCalls);
        assertTrue(executor.tasks.isEmpty());
        assertTrue(source.messagesContain("Invalid Minecraft player name"));
    }

    @Test
    void unknownGroupValidationIsReportedWithoutSuccessMessage() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> {
                    if (scope.equals(AccessScope.serverGroup("missing"))) {
                        throw new AccessGrantScopeValidationException(
                                "Access grant administration references unknown server group: missing."
                        );
                    }
                }
        );
        CommandHarness harness = harness(service, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant group missing offline hanamuramiyu", source.source());

        assertEquals(0, repository.addCalls);
        assertTrue(source.messagesContain("unknown server group: missing"));
        assertFalse(source.messagesContain("Granted"));
    }

    @Test
    void wrongCaseServerValidationShowsExpectedCanonicalName() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> {
                    if (scope.equals(AccessScope.server("LoBbY"))) {
                        throw new AccessGrantScopeValidationException(
                                "Access grant administration uses non-canonical backend name LoBbY; expected lobby."
                        );
                    }
                }
        );
        CommandHarness harness = harness(service, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant server LoBbY offline hanamuramiyu", source.source());

        assertEquals(0, repository.addCalls);
        assertTrue(source.messagesContain("expected lobby"));
        assertFalse(source.messagesContain("Granted"));
    }

    @Test
    void grantAndRevokeResultsHaveDistinctMessages() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        repository.addResult = AccessGrantAddResult.ALREADY_EXISTS;
        harness.dispatcher.execute("monban access grant network offline hanamuramiyu", source.source());
        assertTrue(source.messagesContain("Access grant already exists."));

        source.messages.clear();
        repository.removeResult = AccessGrantRemoveResult.NOT_FOUND;
        harness.dispatcher.execute("monban access revoke network offline hanamuramiyu", source.source());
        assertTrue(source.messagesContain("No matching access grant exists."));
    }

    @Test
    void schedulingFailureIsLoggedAndOnlyGenericErrorIsSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        RecordingLogger logger = new RecordingLogger();
        Executor failingExecutor = task -> {
            throw new IllegalStateException("scheduler failed with /secret/internal/detail");
        };
        CommandHarness harness = harness(
                repository,
                scope -> {},
                failingExecutor,
                proxyServerStub(List.of(), List.of()),
                logger.logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network offline hanamuramiyu", source.source());

        assertEquals(0, repository.addCalls);
        assertEquals(1, logger.errorCalls);
        assertTrue(source.messagesContain("Failed to update access grants. Check the proxy log."));
        assertFalse(source.messagesContain("scheduler failed"));
        assertFalse(source.messagesContain("/secret/internal/detail"));
    }

    @Test
    void persistenceFailureIsLoggedAndOnlyGenericErrorIsSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.failure = new IllegalStateException("disk failed at /secret/monban/access-grants.yml");
        RecordingLogger logger = new RecordingLogger();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});
        CommandHarness harness = harness(
                service,
                Runnable::run,
                proxyServerStub(List.of(), List.of()),
                logger.logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access grant network offline hanamuramiyu", source.source());

        assertEquals(1, logger.errorCalls);
        assertTrue(source.messagesContain("Failed to update access grants. Check the proxy log."));
        assertFalse(source.messagesContain("/secret/monban"));
        assertFalse(source.messagesContain("disk failed"));
    }

    @Test
    void listAllUsesMemoryReadWithoutMutationExecutor() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.all = List.of(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")));
        RecordingExecutor executor = new RecordingExecutor();
        CommandHarness harness = harness(repository, scope -> {}, executor, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());

        assertEquals(1, repository.findAllCalls);
        assertTrue(executor.tasks.isEmpty());
        assertTrue(source.messagesContain("Access grants — page 1/1 — 1 total"));
    }

    @Test
    void listAllPaginatesTenEntriesPerPage() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        List<AccessGrant> grants = new ArrayList<>();
        for (int index = 1; index <= 13; index++) {
            grants.add(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline(index == 1 ? "hanamuramiyu" : "hanamuramiyu" + index)
            ));
        }
        repository.all = grants;
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());
        assertTrue(source.messagesContain("page 1/2 — 13 total"));
        assertEquals(10, source.messagesContaining(". NETWORK — "));
        assertFalse(source.messagesContain("hanamuramiyu7"));

        source.messages.clear();
        harness.dispatcher.execute("monban access list 2", source.source());
        assertTrue(source.messagesContain("page 2/2 — 13 total"));
        assertEquals(3, source.messagesContaining(". NETWORK — "));
        assertTrue(source.messagesContain("hanamuramiyu7"));
        assertTrue(source.messagesContain("hanamuramiyu9"));
    }

    @Test
    void listAllUsesDeterministicOrdering() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        UUID playerThreeUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID playerFiveUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        repository.all = List.of(
                new AccessGrant(AccessScope.server("private"), PlayerIdentity.offline("hanamuramiyu7")),
                new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu3", playerThreeUuid)),
                new AccessGrant(AccessScope.serverGroup("testing"), PlayerIdentity.online("hanamuramiyu5", playerFiveUuid)),
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")),
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu2")),
                new AccessGrant(AccessScope.serverGroup("staff"), PlayerIdentity.offline("hanamuramiyu4")),
                new AccessGrant(AccessScope.server("lobby"), PlayerIdentity.offline("hanamuramiyu6"))
        );
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());

        assertTrue(source.messageIndexContaining("NETWORK — OFFLINE hanamuramiyu")
                < source.messageIndexContaining("NETWORK — OFFLINE hanamuramiyu2"));
        assertTrue(source.messageIndexContaining("NETWORK — OFFLINE hanamuramiyu2")
                < source.messageIndexContaining("NETWORK — ONLINE hanamuramiyu3"));
        assertTrue(source.messageIndexContaining("NETWORK — ONLINE hanamuramiyu3")
                < source.messageIndexContaining("SERVER_GROUP(staff)"));
        assertTrue(source.messageIndexContaining("SERVER_GROUP(staff)")
                < source.messageIndexContaining("SERVER_GROUP(testing)"));
        assertTrue(source.messageIndexContaining("SERVER_GROUP(testing)")
                < source.messageIndexContaining("SERVER(lobby)"));
        assertTrue(source.messageIndexContaining("SERVER(lobby)")
                < source.messageIndexContaining("SERVER(private)"));
    }

    @Test
    void listNetworkShowsOnlyNetworkGrants() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.all = List.of(
                new AccessGrant(AccessScope.server("private"), PlayerIdentity.offline("hanamuramiyu6")),
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))
        );
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list network", source.source());

        assertTrue(source.messagesContain("Access grants for NETWORK"));
        assertTrue(source.messagesContain("OFFLINE hanamuramiyu"));
        assertFalse(source.messagesContain("hanamuramiyu6"));
    }

    @Test
    void listGroupShowsOnlyExactGroup() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.all = List.of(
                new AccessGrant(AccessScope.serverGroup("staff"), PlayerIdentity.offline("hanamuramiyu2")),
                new AccessGrant(AccessScope.serverGroup("testing"), PlayerIdentity.offline("hanamuramiyu4"))
        );
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list group testing", source.source());

        assertTrue(source.messagesContain("Access grants for SERVER_GROUP(testing)"));
        assertTrue(source.messagesContain("OFFLINE hanamuramiyu4"));
        assertFalse(source.messagesContain("hanamuramiyu2"));
    }

    @Test
    void listServerShowsOnlyExactServerIncludingQuotedName() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.all = List.of(
                new AccessGrant(AccessScope.server("lobby"), PlayerIdentity.offline("hanamuramiyu5")),
                new AccessGrant(AccessScope.server("test server"), PlayerIdentity.offline("hanamuramiyu7"))
        );
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list server \"test server\"", source.source());

        assertTrue(source.messagesContain("Access grants for SERVER(test server)"));
        assertTrue(source.messagesContain("OFFLINE hanamuramiyu7"));
        assertFalse(source.messagesContain("hanamuramiyu5"));
    }

    @Test
    void unknownGroupListingShowsValidationError() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> {
                    if (scope.equals(AccessScope.serverGroup("missing"))) {
                        throw new AccessGrantScopeValidationException(
                                "Access grant administration references unknown server group: missing."
                        );
                    }
                }
        );
        CommandHarness harness = harness(service, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list group missing", source.source());

        assertEquals(0, repository.findAllCalls);
        assertTrue(source.messagesContain("unknown server group: missing"));
        assertFalse(source.messagesContain("No access grants found"));
    }

    @Test
    void unknownServerListingShowsValidationError() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> {
                    if (scope.equals(AccessScope.server("missing"))) {
                        throw new AccessGrantScopeValidationException(
                                "Access grant administration references unknown backend: missing."
                        );
                    }
                }
        );
        CommandHarness harness = harness(service, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list server missing", source.source());

        assertEquals(0, repository.findAllCalls);
        assertTrue(source.messagesContain("unknown backend: missing"));
    }

    @Test
    void wrongCaseServerListingShowsCanonicalName() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(
                repository,
                scope -> {
                    if (scope.equals(AccessScope.server("LoBbY"))) {
                        throw new AccessGrantScopeValidationException(
                                "Access grant administration uses non-canonical backend name LoBbY; expected lobby."
                        );
                    }
                }
        );
        CommandHarness harness = harness(service, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list server LoBbY", source.source());

        assertEquals(0, repository.findAllCalls);
        assertTrue(source.messagesContain("expected lobby"));
    }

    @Test
    void emptyListingShowsFriendlyMessage() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());
        assertTrue(source.messagesContain("No access grants found."));

        source.messages.clear();
        harness.dispatcher.execute("monban access list group testing", source.source());
        assertTrue(source.messagesContain("No access grants found for SERVER_GROUP(testing)."));
    }

    @Test
    void pageOutsideRangeShowsFriendlyError() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        List<AccessGrant> grants = new ArrayList<>();
        for (int index = 1; index <= 13; index++) {
            grants.add(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline(index == 1 ? "hanamuramiyu" : "hanamuramiyu" + index)
            ));
        }
        repository.all = grants;
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list 3", source.source());

        assertTrue(source.messagesContain("Page 3 is out of range. Available pages: 1-2."));
        assertFalse(source.messagesContain("Access grants — page 3"));
    }

    @Test
    void pageZeroIsRejectedByBrigadier() {
        RecordingRepository repository = new RecordingRepository();
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        assertThrows(
                CommandSyntaxException.class,
                () -> harness.dispatcher.execute("monban access list 0", source.source())
        );
        assertEquals(0, repository.findAllCalls);
    }

    @Test
    void offlineTechnicalUuidIsNotDisplayed() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        UUID technicalUuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        repository.all = List.of(new AccessGrant(
                AccessScope.network(),
                PlayerIdentity.offline("hanamuramiyu", technicalUuid)
        ));
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());

        assertTrue(source.messagesContain("OFFLINE hanamuramiyu"));
        assertFalse(source.messagesContain(technicalUuid.toString()));
    }

    @Test
    void onlineUuidIsDisplayed() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.all = List.of(new AccessGrant(
                AccessScope.network(),
                PlayerIdentity.online("hanamuramiyu", PLAYER_UUID)
        ));
        CommandHarness harness = harness(repository, scope -> {}, Runnable::run, proxyServerStub(List.of(), List.of()));
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());

        assertTrue(source.messagesContain("ONLINE hanamuramiyu (" + PLAYER_UUID + ")"));
    }

    @Test
    void listingFailureIsLoggedAndOnlyGenericReadErrorIsSent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        repository.failure = new IllegalStateException("failed at /secret/path");
        RecordingLogger logger = new RecordingLogger();
        CommandHarness harness = harness(
                repository,
                scope -> {},
                task -> { throw new AssertionError("list must not use mutation executor"); },
                proxyServerStub(List.of(), List.of()),
                logger.logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(true);

        harness.dispatcher.execute("monban access list", source.source());

        assertEquals(1, logger.errorCalls);
        assertTrue(source.messagesContain("Failed to read access grants. Check the proxy log."));
        assertFalse(source.messagesContain("failed at"));
        assertFalse(source.messagesContain("/secret/path"));
        assertFalse(source.messagesContain("Failed to update access grants"));
    }

    @Test
    void suggestionsComeFromResolvedGroupsServersAndConnectedPlayers() throws Exception {
        RegisteredServer lobby = registeredServerStub("lobby");
        RegisteredServer spaced = registeredServerStub("test server");
        Player player = playerStub("hanamuramiyu");
        ProxyServer proxy = proxyServerStub(List.of(lobby, spaced), List.of(player));
        ServerGroupCatalog groups = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("testing", List.of("lobby"))
        ));
        RecordingRepository repository = new RecordingRepository();
        AccessGrantAdministrationService service = new AccessGrantAdministrationService(repository, scope -> {});
        Logger logger = new RecordingLogger().logger();
        VelocityWhitelistCommand whitelistCommand = new VelocityWhitelistCommand(
                service,
                proxy,
                Runnable::run,
                logger
        );
        VelocityAccessCommand accessCommand = new VelocityAccessCommand(
                service,
                groups,
                proxy,
                Runnable::run,
                logger
        );
        VelocityStatusCommand statusCommand = new VelocityStatusCommand(
                MonbanConfig.defaults(),
                repository,
                groups,
                new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, Map.of(), Map.of()),
                false,
                logger
        );
        BrigadierCommand command = VelocityMonbanCommand.create(whitelistCommand, accessCommand, statusCommand);
        CommandDispatcher<CommandSource> dispatcher = dispatcher(command);
        CommandSource source = new RecordingCommandSource(true).source();

        assertTrue(suggestions(dispatcher, "monban access grant group ", source).contains("testing"));
        assertTrue(suggestions(dispatcher, "monban access list group ", source).contains("testing"));
        List<String> serverSuggestions = suggestions(dispatcher, "monban access grant server ", source);
        assertTrue(serverSuggestions.contains("lobby"));
        assertTrue(serverSuggestions.contains("\"test server\""));
        List<String> listServerSuggestions = suggestions(dispatcher, "monban access list server ", source);
        assertTrue(listServerSuggestions.contains("lobby"));
        assertTrue(listServerSuggestions.contains("\"test server\""));
        assertTrue(suggestions(dispatcher, "monban access grant network offline ", source).contains("hanamuramiyu"));
    }

    private static List<String> suggestions(
            CommandDispatcher<CommandSource> dispatcher,
            String input,
            CommandSource source
    ) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source))
                .get()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .toList();
    }

    private static CommandHarness harness(
            RecordingRepository repository,
            hanamuramiyu.monban.access.admin.AccessGrantScopeValidator validator,
            Executor executor,
            ProxyServer proxy
    ) {
        return harness(new AccessGrantAdministrationService(repository, validator), executor, proxy);
    }

    private static CommandHarness harness(
            AccessGrantAdministrationService service,
            Executor executor,
            ProxyServer proxy
    ) {
        return harness(service, executor, proxy, new RecordingLogger().logger());
    }

    private static CommandHarness harness(
            RecordingRepository repository,
            hanamuramiyu.monban.access.admin.AccessGrantScopeValidator validator,
            Executor executor,
            ProxyServer proxy,
            Logger logger
    ) {
        return harness(
                new AccessGrantAdministrationService(repository, validator),
                repository,
                executor,
                proxy,
                logger
        );
    }

    private static CommandHarness harness(
            AccessGrantAdministrationService service,
            Executor executor,
            ProxyServer proxy,
            Logger logger
    ) {
        return harness(service, List::of, executor, proxy, logger);
    }

    private static CommandHarness harness(
            AccessGrantAdministrationService service,
            AccessGrantInventory grantInventory,
            Executor executor,
            ProxyServer proxy,
            Logger logger
    ) {
        ServerGroupCatalog serverGroupCatalog = new ServerGroupCatalog(List.of());
        VelocityWhitelistCommand whitelistCommand = new VelocityWhitelistCommand(
                service,
                proxy,
                executor,
                logger
        );
        VelocityAccessCommand accessCommand = new VelocityAccessCommand(
                service,
                serverGroupCatalog,
                proxy,
                executor,
                logger
        );
        VelocityStatusCommand statusCommand = new VelocityStatusCommand(
                MonbanConfig.defaults(),
                grantInventory,
                serverGroupCatalog,
                new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, Map.of(), Map.of()),
                false,
                logger
        );
        BrigadierCommand command = VelocityMonbanCommand.create(whitelistCommand, accessCommand, statusCommand);
        return new CommandHarness(dispatcher(command));
    }

    private static CommandDispatcher<CommandSource> dispatcher(BrigadierCommand command) {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.getNode());
        return dispatcher;
    }

    private record CommandHarness(CommandDispatcher<CommandSource> dispatcher) {
    }

    private static final class RecordingRepository implements AccessGrantRepository {
        private int findAllCalls;
        private int addCalls;
        private int removeCalls;
        private List<AccessGrant> all = List.of();
        private AccessGrant lastAdded;
        private AccessScope lastRemovedScope;
        private PlayerIdentity lastRemovedIdentity;
        private AccessGrantAddResult addResult = AccessGrantAddResult.ADDED;
        private AccessGrantRemoveResult removeResult = AccessGrantRemoveResult.REMOVED;
        private RuntimeException failure;

        @Override
        public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            return Optional.empty();
        }

        @Override
        public List<AccessGrant> findAll() {
            failIfNeeded();
            findAllCalls++;
            return all;
        }

        @Override
        public AccessGrantAddResult add(AccessGrant grant) {
            failIfNeeded();
            addCalls++;
            lastAdded = grant;
            return addResult;
        }

        @Override
        public AccessGrantRemoveResult remove(AccessScope scope, PlayerIdentity identity) {
            failIfNeeded();
            removeCalls++;
            lastRemovedScope = scope;
            lastRemovedIdentity = identity;
            return removeResult;
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
