package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.OnlineProfile;
import hanamuramiyu.monban.identity.OnlineProfileResolutionException;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VelocityLookupCommandTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void lookupShowsOnlineAndOfflineIdentityAndNetworkStatus() throws Exception {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.online("hanamuramiyu", VERIFIED_UUID)));
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, name ->
                CompletableFuture.completedFuture(new OnlineProfile(name, VERIFIED_UUID)));
        RecordingCommandSource source = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban lookup hanamuramiyu", source.source()));

        assertTrue(source.messagesContain("Player · hanamuramiyu"));
        assertTrue(source.messagesContain("ONLINE 00000000…0001"));
        assertTrue(source.messagesContain("OFFLINE hanamuramiyu"));
        assertTrue(source.messagesContain("Whitelisted as ONLINE."));
    }

    @Test
    void lookupUsesOfflineIdentityWhenOnlineProfileDoesNotExist() throws Exception {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("miyu2")));
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, name ->
                CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                        OnlineProfileResolutionException.Kind.NOT_FOUND,
                        "missing"
                )));
        RecordingCommandSource source = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban lookup miyu2", source.source()));

        assertTrue(source.messagesContain("OFFLINE miyu2"));
        assertTrue(source.messagesContain("Whitelisted as OFFLINE."));
    }

    @Test
    void unavailableOnlineProfileStillReturnsOfflineResult() throws Exception {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, name ->
                CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                        OnlineProfileResolutionException.Kind.UNAVAILABLE,
                        "unavailable"
                )));
        RecordingCommandSource source = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban lookup miyu2", source.source()));

        assertTrue(source.messagesContain("Online profile lookup is temporarily unavailable."));
        assertTrue(source.messagesContain("OFFLINE miyu2"));
        assertTrue(source.messagesContain("Not whitelisted."));
    }

    @Test
    void lookupRequiresPermissionAndValidPlayerName() throws Exception {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, OnlineProfileResolver.unavailable());

        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("monban lookup miyu2", new RecordingCommandSource().source())
        );

        RecordingCommandSource source = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);
        assertEquals(0, dispatcher.execute("monban lookup invalid-name", source.source()));
        assertTrue(source.messagesContain("Invalid Minecraft player name"));
    }

    @Test
    void lookupShowsEffectiveGroupsAccessAndPermissions() throws Exception {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        repository.add(new AccessGrant(AccessScope.network(), PlayerIdentity.online("miyu2", VERIFIED_UUID)));
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        groups.add(new PlayerGroupDefinition(
                "moderator",
                List.of(AccessScope.server("staff")),
                List.of(PermissionGrant.server("survival", "coreprotect.inspect"))
        ));
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerIdentity identity = PlayerIdentity.online("miyu2", VERIFIED_UUID);
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                repository,
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
        CommandDispatcher<CommandSource> dispatcher = dispatcher(repository, name ->
                CompletableFuture.completedFuture(new OnlineProfile(name, VERIFIED_UUID)), resolver);
        RecordingCommandSource source = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("monban lookup miyu2", source.source()));

        assertTrue(source.messagesContain("Groups"));
        assertTrue(source.messagesContain("moderator"));
        assertTrue(source.messagesContain("SERVER(staff) — GROUP:moderator"));
        assertTrue(source.messagesContain("coreprotect.inspect — GROUP:moderator"));
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AccessGrantRepository repository,
            OnlineProfileResolver profileResolver
    ) {
        return dispatcher(repository, profileResolver, null);
    }

    private static CommandDispatcher<CommandSource> dispatcher(
            AccessGrantRepository repository,
            OnlineProfileResolver profileResolver,
            PlayerAccessResolver accessResolver
    ) {
        AccessGrantAdministrationService administration = new AccessGrantAdministrationService(repository, scope -> {});
        ProxyServer proxy = proxyServerStub(List.of(), List.of());
        VelocityLookupCommand lookup = new VelocityLookupCommand(
                administration,
                proxy,
                Runnable::run,
                new RecordingLogger().logger(),
                profileResolver,
                accessResolver
        );
        BrigadierCommand command = lookupCommand(lookup);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.getNode());
        return dispatcher;
    }

    private static BrigadierCommand lookupCommand(VelocityLookupCommand lookup) {
        var root = com.velocitypowered.api.command.BrigadierCommand.literalArgumentBuilder("monban");
        root.then(lookup.build());
        return new BrigadierCommand(root);
    }
}
