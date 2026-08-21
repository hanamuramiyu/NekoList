package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.config.MonbanConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityMonbanCommandTest {
    @Test
    void permissionDeniedMakesAccessSubtreeUnavailable() {
        CommandDispatcher<CommandSource> dispatcher = dispatcher();
        RecordingCommandSource source = new RecordingCommandSource();

        assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("monban access grant network offline hanamuramiyu", source.source())
        );
    }

    @Test
    void accessRootShowsUsageOnlyToOperators() throws Exception {
        CommandDispatcher<CommandSource> dispatcher = dispatcher();

        RecordingCommandSource operator = new RecordingCommandSource(VelocityAccessCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban access", operator.source()));
        assertTrue(operator.messagesContain("/monban access grant network offline <name>"));

        RecordingCommandSource player = new RecordingCommandSource();
        assertEquals(1, dispatcher.execute("monban access", player.source()));
        assertTrue(player.messagesContain("Unknown command. Type \"/help\" for help."));
        assertFalse(player.messagesContain("/monban access grant"));
    }

    @Test
    void rootComposesWhitelistAccessAndStatusWithIndependentPermissions() throws Exception {
        CommandDispatcher<CommandSource> dispatcher = dispatcher();

        RecordingCommandSource whitelistOnly = new RecordingCommandSource(VelocityWhitelistCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban whitelist list", whitelistOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban access list", whitelistOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban status", whitelistOnly.source()));

        RecordingCommandSource lookupOnly = new RecordingCommandSource(VelocityLookupCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban lookup hanamuramiyu", lookupOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban whitelist list", lookupOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban access list", lookupOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban status", lookupOnly.source()));

        RecordingCommandSource accessOnly = new RecordingCommandSource(VelocityAccessCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban access list", accessOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban whitelist list", accessOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban status", accessOnly.source()));

        RecordingCommandSource statusOnly = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);
        assertEquals(1, dispatcher.execute("monban status", statusOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban whitelist list", statusOnly.source()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("monban access list", statusOnly.source()));

        RecordingCommandSource allCommands = new RecordingCommandSource(true);
        assertTrue(dispatcher.execute("monban", allCommands.source()) > 0);
        assertTrue(allCommands.messagesContain("/monban lookup <player>"));
    }

    private static CommandDispatcher<CommandSource> dispatcher() {
        InMemoryAccessGrantRepository repository = new InMemoryAccessGrantRepository();
        AccessGrantAdministrationService administration = new AccessGrantAdministrationService(repository, scope -> {});
        ServerGroupCatalog serverGroups = new ServerGroupCatalog(List.of());
        ProxyServer proxyServer = proxyServerStub(List.of(), List.of());
        Logger logger = new RecordingLogger().logger();

        VelocityWhitelistCommand whitelist = new VelocityWhitelistCommand(administration, proxyServer, Runnable::run, logger);
        VelocityLookupCommand lookup = new VelocityLookupCommand(
                administration,
                proxyServer,
                Runnable::run,
                logger
        );
        VelocityAccessCommand access = new VelocityAccessCommand(
                administration,
                serverGroups,
                proxyServer,
                Runnable::run,
                logger
        );
        VelocityStatusCommand status = new VelocityStatusCommand(
                MonbanConfig.defaults(),
                repository,
                serverGroups,
                new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, Map.of(), Map.of()),
                false,
                logger
        );

        BrigadierCommand root = VelocityMonbanCommand.create(whitelist, lookup, access, status);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(root.getNode());
        return dispatcher;
    }

}
