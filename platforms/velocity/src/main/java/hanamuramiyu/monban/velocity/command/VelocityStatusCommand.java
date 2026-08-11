package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScopeType;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.velocity.MonbanVelocityPluginMetadata;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VelocityStatusCommand {
    public static final String PERMISSION = "monban.command.status";

    private static final Component READ_FAILURE_MESSAGE =
            Component.text("Failed to read monban status. Check the proxy log.");

    private final MonbanConfig config;
    private final AccessGrantInventory grantInventory;
    private final ServerGroupCatalog serverGroupCatalog;
    private final BackendAccessPolicyCatalog backendAccessPolicyCatalog;
    private final boolean velocityOnlineMode;
    private final Logger logger;

    public VelocityStatusCommand(
            MonbanConfig config,
            AccessGrantInventory grantInventory,
            ServerGroupCatalog serverGroupCatalog,
            BackendAccessPolicyCatalog backendAccessPolicyCatalog,
            boolean velocityOnlineMode,
            Logger logger
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.grantInventory = Objects.requireNonNull(grantInventory, "grantInventory");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
        this.backendAccessPolicyCatalog = Objects.requireNonNull(
                backendAccessPolicyCatalog,
                "backendAccessPolicyCatalog"
        );
        this.velocityOnlineMode = velocityOnlineMode;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public LiteralArgumentBuilder<CommandSource> build() {
        return BrigadierCommand.literalArgumentBuilder("status")
                .requires(source -> source.hasPermission(PERMISSION))
                .executes(this::execute);
    }

    private int execute(CommandContext<CommandSource> context) {
        final List<Component> lines;
        try {
            lines = buildStatusLines();
        } catch (RuntimeException exception) {
            logger.error("Failed to read monban status.", exception);
            context.getSource().sendMessage(READ_FAILURE_MESSAGE);
            return 0;
        }

        for (Component line : lines) {
            context.getSource().sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private List<Component> buildStatusLines() {
        List<AccessGrant> grants = grantInventory.findAll();

        int networkGrants = 0;
        int serverGroupGrants = 0;
        int serverGrants = 0;
        for (AccessGrant grant : grants) {
            AccessScopeType type = grant.scope().type();
            switch (type) {
                case NETWORK -> networkGrants++;
                case SERVER_GROUP -> serverGroupGrants++;
                case SERVER -> serverGrants++;
            }
        }

        int serverGroups = serverGroupCatalog.findAll().size();
        int serverGroupPolicies = backendAccessPolicyCatalog.serverGroupPolicies().size();
        int serverPolicies = backendAccessPolicyCatalog.serverPolicies().size();
        int explicitPolicies = backendAccessPolicyCatalog.explicitPolicyCount();

        List<Component> lines = new ArrayList<>(9);
        lines.add(Component.text("monban " + MonbanVelocityPluginMetadata.VERSION + " — Velocity status"));
        lines.add(Component.text("Deployment: " + config.deployment().mode()));
        lines.add(Component.text("Whitelist: " + enabled(config.whitelist().enabled())));
        lines.add(Component.text("Identity mode: " + config.identity().mode()));
        if (config.identity().hybrid().enabled()) {
            lines.add(Component.text(
                    "Hybrid: enabled (preference: "
                            + config.identity().hybrid().dualEntryPreference()
                            + ")"
            ));
        } else {
            lines.add(Component.text("Hybrid: disabled"));
        }
        lines.add(Component.text("Velocity online-mode: " + enabled(velocityOnlineMode)));
        lines.add(Component.text(
                "Access grants: " + grants.size() + " total — NETWORK " + networkGrants
                        + ", SERVER_GROUP " + serverGroupGrants
                        + ", SERVER " + serverGrants
        ));
        lines.add(Component.text("Server groups: " + serverGroups));
        lines.add(Component.text(
                "Backend access: default " + backendAccessPolicyCatalog.defaultMode()
                        + " — " + explicitPolicies + " explicit policies"
                        + " (SERVER_GROUP " + serverGroupPolicies + ", SERVER " + serverPolicies + ")"
        ));
        return List.copyOf(lines);
    }

    private static String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }
}
