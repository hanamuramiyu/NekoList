package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.PlayerGroupAdministrationService;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.proxyServerStub;

class VelocityGroupCommandTest {
    @Test
    void managesGroupAndUserCommands() throws Exception {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        InMemoryPlayerPermissionGrantRepository permissions = new InMemoryPlayerPermissionGrantRepository();
        PlayerGroupAdministrationService administration = new PlayerGroupAdministrationService(
                groups,
                assignments,
                permissions,
                scope -> {
                }
        );
        ProxyServer server = proxyServerStub(List.of(), List.of());
        Logger logger = new VelocityCommandTestSupport.RecordingLogger().logger();
        AtomicInteger broadcasts = new AtomicInteger();
        VelocityGroupCommand command = new VelocityGroupCommand(
                administration,
                new ServerGroupCatalog(List.of()),
                server,
                Runnable::run,
                logger,
                hanamuramiyu.monban.identity.OnlineProfileResolver.unavailable(),
                () -> broadcasts.incrementAndGet()
        );
        BrigadierCommand root = new BrigadierCommand(
                BrigadierCommand.literalArgumentBuilder("monban")
                        .then(command.buildGroup())
                        .then(command.buildUser())
        );
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(root.getNode());
        VelocityCommandTestSupport.RecordingCommandSource source = new VelocityCommandTestSupport.RecordingCommandSource(
                VelocityGroupCommand.PERMISSION
        );

        assertEquals(1, dispatcher.execute("monban group create moderator", source.source()));
        assertEquals(1, dispatcher.execute(
                "monban group moderator permission add network coreprotect.inspect",
                source.source()
        ));
        assertEquals(1, dispatcher.execute("monban user Miyu group add moderator", source.source()));

        assertTrue(groups.find("moderator").orElseThrow().permissions().stream()
                .anyMatch(permission -> permission.node().equals("coreprotect.inspect")));
        assertEquals(1, assignments.findAll().size());
        assertEquals("Miyu", assignments.findAll().getFirst().identity().name());
        assertEquals(3, broadcasts.get());

        assertEquals(1, dispatcher.execute(
                "monban group moderator permission add network coreprotect.inspect",
                source.source()
        ));
        assertEquals(3, broadcasts.get());
    }

    @Test
    void groupCommandsRequirePermission() {
        InMemoryPlayerGroupAdministrationFixture fixture = new InMemoryPlayerGroupAdministrationFixture();
        CommandDispatcher<CommandSource> dispatcher = fixture.dispatcher();

        assertThrows(
                com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("monban group create moderator", new VelocityCommandTestSupport.RecordingCommandSource().source())
        );
    }

    private static final class InMemoryPlayerGroupAdministrationFixture {
        private final InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();

        private CommandDispatcher<CommandSource> dispatcher() {
            PlayerGroupAdministrationService administration = new PlayerGroupAdministrationService(
                    groups,
                    new InMemoryPlayerGroupAssignmentRepository(),
                    new InMemoryPlayerPermissionGrantRepository(),
                    scope -> {
                    }
            );
            VelocityGroupCommand command = new VelocityGroupCommand(
                    administration,
                    new ServerGroupCatalog(List.of()),
                    proxyServerStub(List.of(), List.of()),
                    Runnable::run,
                    new VelocityCommandTestSupport.RecordingLogger().logger(),
                    hanamuramiyu.monban.identity.OnlineProfileResolver.unavailable()
            );
            CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
            BrigadierCommand root = new BrigadierCommand(
                    BrigadierCommand.literalArgumentBuilder("monban")
                            .then(command.buildGroup())
            );
            dispatcher.getRoot().addChild(root.getNode());
            return dispatcher;
        }
    }
}
