package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.backend.BackendAccessMode;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.IdentityResolutionMode;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.MonbanVelocityPluginMetadata;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static hanamuramiyu.monban.velocity.command.VelocityCommandTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityStatusCommandTest {
    @Test
    void statusRequiresStatusPermission() {
        RecordingInventory inventory = new RecordingInventory(List.of());
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                inventory,
                new ServerGroupCatalog(List.of()),
                policies(),
                false,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource();

        assertThrows(CommandSyntaxException.class, () -> harness.dispatcher.execute("status", source.source()));
        assertEquals(0, inventory.findAllCalls);
    }

    @Test
    void hybridEnabledShowsActivePreference() throws Exception {
        StatusHarness harness = harness(
                config(true, true, HybridIdentityPreference.OFFLINE),
                new RecordingInventory(List.of()),
                new ServerGroupCatalog(List.of()),
                policies(),
                false,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(1, harness.dispatcher.execute("status", source.source()));

        assertTrue(source.messagesContain("Whitelist: enabled"));
        assertTrue(source.messagesContain("Identity mode: AUTO"));
        assertTrue(source.messagesContain("Hybrid: enabled (preference: OFFLINE)"));
        assertTrue(source.messagesContain("Velocity online-mode: disabled"));
    }

    @Test
    void hybridDisabledDoesNotShowInactivePreference() throws Exception {
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                new RecordingInventory(List.of()),
                new ServerGroupCatalog(List.of()),
                policies(),
                true,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(1, harness.dispatcher.execute("status", source.source()));

        assertTrue(source.messagesContain("Hybrid: disabled"));
        assertFalse(source.messagesContain("preference:"));
        assertTrue(source.messagesContain("Velocity online-mode: enabled"));
    }

    @Test
    void statusReportsGrantTopologyAndPolicyCountsFromOneSnapshot() throws Exception {
        List<AccessGrant> grants = List.of(
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu")),
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu2")),
                new AccessGrant(AccessScope.serverGroup("testing"), PlayerIdentity.offline("hanamuramiyu3")),
                new AccessGrant(AccessScope.serverGroup("staff"), PlayerIdentity.offline("hanamuramiyu4")),
                new AccessGrant(AccessScope.serverGroup("staff"), PlayerIdentity.offline("hanamuramiyu5")),
                new AccessGrant(AccessScope.server("lobby"), PlayerIdentity.offline("hanamuramiyu6")),
                new AccessGrant(AccessScope.server("private"), PlayerIdentity.offline("hanamuramiyu7")),
                new AccessGrant(AccessScope.server("private"), PlayerIdentity.offline("hanamuramiyu8")),
                new AccessGrant(AccessScope.server("private"), PlayerIdentity.offline("hanamuramiyu9"))
        );
        RecordingInventory inventory = new RecordingInventory(grants);
        ServerGroupCatalog groups = new ServerGroupCatalog(List.of(
                new ServerGroupDefinition("testing", List.of("lobby")),
                new ServerGroupDefinition("staff", List.of("private"))
        ));
        BackendAccessPolicyCatalog backendPolicies = new BackendAccessPolicyCatalog(
                BackendAccessMode.OPEN,
                Map.of("testing", BackendAccessMode.GRANT_REQUIRED),
                Map.of(
                        "lobby", BackendAccessMode.GRANT_REQUIRED,
                        "private", BackendAccessMode.GRANT_REQUIRED
                )
        );
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                inventory,
                groups,
                backendPolicies,
                false,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(1, harness.dispatcher.execute("status", source.source()));

        assertEquals(1, inventory.findAllCalls);
        assertTrue(source.messagesContain("monban " + MonbanVelocityPluginMetadata.VERSION + " — Velocity status"));
        assertTrue(source.messagesContain("Deployment: VELOCITY"));
        assertTrue(source.messagesContain("Access grants: 9 total — NETWORK 2, SERVER_GROUP 3, SERVER 4"));
        assertTrue(source.messagesContain("Server groups: 2"));
        assertTrue(source.messagesContain(
                "Backend access: default OPEN — 3 explicit policies (SERVER_GROUP 1, SERVER 2)"
        ));
    }

    @Test
    void inventoryIsReadExactlyOncePerStatusExecution() throws Exception {
        RecordingInventory inventory = new RecordingInventory(List.of(
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))
        ));
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                inventory,
                new ServerGroupCatalog(List.of()),
                policies(),
                false,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(1, harness.dispatcher.execute("status", source.source()));
        assertEquals(1, inventory.findAllCalls);
    }

    @Test
    void statusReadsCurrentRuntimeWhitelistPolicy() throws Exception {
        WhitelistPolicy policy = new WhitelistPolicy(false);
        RecordingInventory inventory = new RecordingInventory(List.of());
        VelocityStatusCommand command = new VelocityStatusCommand(
                config(false, false, HybridIdentityPreference.ONLINE),
                inventory,
                new ServerGroupCatalog(List.of()),
                policies(),
                policy::enabled,
                false,
                new RecordingLogger().logger()
        );
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build().build());
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(1, dispatcher.execute("status", source.source()));
        assertTrue(source.messagesContain("Whitelist: disabled"));

        policy.setEnabled(true);
        assertEquals(1, dispatcher.execute("status", source.source()));
        assertTrue(source.messagesContain("Whitelist: enabled"));
    }

    @Test
    void statusFailureIsLoggedAndOnlyGenericErrorIsSent() throws Exception {
        IllegalStateException failure = new IllegalStateException("failed at /secret/status/path");
        AccessGrantInventory inventory = () -> {
            throw failure;
        };
        RecordingLogger logger = new RecordingLogger();
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                inventory,
                new ServerGroupCatalog(List.of()),
                policies(),
                false,
                logger.logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertEquals(0, harness.dispatcher.execute("status", source.source()));

        assertEquals(1, logger.errorCalls);
        assertSame(failure, logger.lastThrowable);
        assertEquals(1, source.messages.size());
        assertTrue(source.messagesContain("Failed to read monban status. Check the proxy log."));
        assertFalse(source.messagesContain("failed at"));
        assertFalse(source.messagesContain("/secret/status/path"));
        assertFalse(source.messagesContain("Deployment:"));
    }

    @Test
    void statusRejectsUnexpectedArguments() {
        StatusHarness harness = harness(
                config(false, false, HybridIdentityPreference.ONLINE),
                new RecordingInventory(List.of()),
                new ServerGroupCatalog(List.of()),
                policies(),
                false,
                new RecordingLogger().logger()
        );
        RecordingCommandSource source = new RecordingCommandSource(VelocityStatusCommand.PERMISSION);

        assertThrows(CommandSyntaxException.class, () -> harness.dispatcher.execute("status extra", source.source()));
    }

    private static StatusHarness harness(
            MonbanConfig config,
            AccessGrantInventory inventory,
            ServerGroupCatalog serverGroups,
            BackendAccessPolicyCatalog backendPolicies,
            boolean velocityOnlineMode,
            Logger logger
    ) {
        VelocityStatusCommand command = new VelocityStatusCommand(
                config,
                inventory,
                serverGroups,
                backendPolicies,
                velocityOnlineMode,
                logger
        );
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build().build());
        return new StatusHarness(dispatcher);
    }

    private static MonbanConfig config(
            boolean whitelistEnabled,
            boolean hybridEnabled,
            HybridIdentityPreference preference
    ) {
        return new MonbanConfig(
                new DeploymentSettings(DeploymentMode.VELOCITY),
                new WhitelistSettings(whitelistEnabled),
                new IdentitySettings(
                        IdentityResolutionMode.AUTO,
                        new HybridIdentitySettings(hybridEnabled, preference)
                )
        );
    }

    private static BackendAccessPolicyCatalog policies() {
        return new BackendAccessPolicyCatalog(BackendAccessMode.OPEN, Map.of(), Map.of());
    }

    private record StatusHarness(CommandDispatcher<CommandSource> dispatcher) {
    }

    private static final class RecordingInventory implements AccessGrantInventory {
        private final List<AccessGrant> grants;
        private int findAllCalls;

        private RecordingInventory(List<AccessGrant> grants) {
            this.grants = grants;
        }

        @Override
        public List<AccessGrant> findAll() {
            findAllCalls++;
            return grants;
        }
    }

}
