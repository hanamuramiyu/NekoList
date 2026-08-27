package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.PlayerGroupAdministrationService;
import hanamuramiyu.monban.access.admin.PlayerGroupEntryResult;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PermissionGrantRemoveResult;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.OnlineProfileResolutionException;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public final class VelocityGroupCommand {
    public static final String PERMISSION = "monban.command.group";

    private final PlayerGroupAdministrationService administrationService;
    private final ServerGroupCatalog serverGroupCatalog;
    private final ProxyServer server;
    private final Executor mutationExecutor;
    private final Logger logger;
    private final OnlineProfileResolver profileResolver;
    private final Runnable stateChanged;

    public VelocityGroupCommand(
            PlayerGroupAdministrationService administrationService,
            ServerGroupCatalog serverGroupCatalog,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver
    ) {
        this(administrationService, serverGroupCatalog, server, mutationExecutor, logger, profileResolver, () -> {
        });
    }

    public VelocityGroupCommand(
            PlayerGroupAdministrationService administrationService,
            ServerGroupCatalog serverGroupCatalog,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver,
            Runnable stateChanged
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
        this.server = Objects.requireNonNull(server, "server");
        this.mutationExecutor = Objects.requireNonNull(mutationExecutor, "mutationExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
    }

    public LiteralArgumentBuilder<CommandSource> buildGroup() {
        return BrigadierCommand.literalArgumentBuilder("group")
                .executes(context -> usage(context.getSource()))
                .then(BrigadierCommand.literalArgumentBuilder("create")
                        .requires(source -> source.hasPermission(PERMISSION))
                        .then(BrigadierCommand.requiredArgumentBuilder("group-id", StringArgumentType.word())
                                .executes(this::createGroup)))
                .then(BrigadierCommand.literalArgumentBuilder("remove")
                        .requires(source -> source.hasPermission(PERMISSION))
                        .then(groupIdArgument().executes(this::removeGroup)))
                .then(groupIdArgument()
                        .then(BrigadierCommand.literalArgumentBuilder("access")
                                .then(accessMutation("grant", true))
                                .then(accessMutation("revoke", false)))
                        .then(BrigadierCommand.literalArgumentBuilder("permission")
                                .then(permissionMutation("add", true))
                                .then(permissionMutation("remove", false))));
    }

    public LiteralArgumentBuilder<CommandSource> buildUser() {
        return BrigadierCommand.literalArgumentBuilder("user")
                .executes(context -> usage(context.getSource()))
                .requires(source -> source.hasPermission(PERMISSION))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(userGroupMutation("add", true))
                        .then(userGroupMutation("remove", false))
                        .then(userPermissionMutation("add", true))
                        .then(userPermissionMutation("remove", false))
                        .then(userIdentityBranch("offline", IdentityType.OFFLINE))
                        .then(userIdentityBranch("online", IdentityType.ONLINE)));
    }

    private RequiredArgumentBuilder<CommandSource, String> groupIdArgument() {
        return BrigadierCommand.requiredArgumentBuilder("group-id", StringArgumentType.word())
                .requires(source -> source.hasPermission(PERMISSION))
                .suggests(groupSuggestions());
    }

    private LiteralArgumentBuilder<CommandSource> accessMutation(String literal, boolean grant) {
        LiteralArgumentBuilder<CommandSource> action = BrigadierCommand.literalArgumentBuilder(literal);
        action.then(scopeBranch("network", ignored -> AccessScope.network(), grant));
        action.then(scopeBranch("group", context -> AccessScope.serverGroup(
                StringArgumentType.getString(context, "scope-id")
        ), grant));
        action.then(scopeBranch("server", context -> AccessScope.server(
                StringArgumentType.getString(context, "scope-id")
        ), grant));
        return action;
    }

    private LiteralArgumentBuilder<CommandSource> permissionMutation(String literal, boolean grant) {
        LiteralArgumentBuilder<CommandSource> action = BrigadierCommand.literalArgumentBuilder(literal);
        action.then(permissionScopeBranch("network", ignored -> AccessScope.network(), grant));
        action.then(permissionScopeBranch("group", context -> AccessScope.serverGroup(
                StringArgumentType.getString(context, "scope-id")
        ), grant));
        action.then(permissionScopeBranch("server", context -> AccessScope.server(
                StringArgumentType.getString(context, "scope-id")
        ), grant));
        return action;
    }

    private LiteralArgumentBuilder<CommandSource> scopeBranch(
            String literal,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            boolean grant
    ) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal);
        if (literal.equals("network")) {
            return branch.executes(context -> executeGroupAccess(context, scopeFactory.apply(context), grant));
        }
        return branch.then(BrigadierCommand.requiredArgumentBuilder("scope-id", StringArgumentType.word())
                .suggests(literal.equals("group") ? serverGroupSuggestions() : serverSuggestions())
                .executes(context -> executeGroupAccess(context, scopeFactory.apply(context), grant)));
    }

    private LiteralArgumentBuilder<CommandSource> permissionScopeBranch(
            String literal,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            boolean grant
    ) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal);
        if (literal.equals("network")) {
            return branch.then(permissionNode("node")
                    .executes(context -> executeGroupPermission(context, scopeFactory.apply(context), grant)));
        }
        return branch.then(BrigadierCommand.requiredArgumentBuilder("scope-id", StringArgumentType.word())
                .suggests(literal.equals("group") ? serverGroupSuggestions() : serverSuggestions())
                .then(permissionNode("node")
                        .executes(context -> executeGroupPermission(context, scopeFactory.apply(context), grant))));
    }

    private RequiredArgumentBuilder<CommandSource, String> permissionNode(String name) {
        return BrigadierCommand.requiredArgumentBuilder(name, StringArgumentType.word());
    }

    private LiteralArgumentBuilder<CommandSource> userGroupMutation(String literal, boolean grant) {
        return userGroupMutation(literal, grant, null);
    }

    private LiteralArgumentBuilder<CommandSource> userGroupMutation(
            String literal,
            boolean grant,
            IdentityType identityType
    ) {
        return BrigadierCommand.literalArgumentBuilder("group")
                .then(BrigadierCommand.literalArgumentBuilder(literal)
                        .then(BrigadierCommand.requiredArgumentBuilder("group-id", StringArgumentType.word())
                                .suggests(groupSuggestions())
                                .executes(context -> executeUser(context, identityType, identity -> {
                                    String groupId = StringArgumentType.getString(context, "group-id");
                                    return grant
                                            ? administrationService.addAssignment(identity, groupId)
                                            : administrationService.removeAssignment(identity, groupId);
                                }))));
    }

    private LiteralArgumentBuilder<CommandSource> userIdentityBranch(
            String literal,
            IdentityType identityType
    ) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .then(userGroupMutation("add", true, identityType))
                .then(userGroupMutation("remove", false, identityType))
                .then(userPermissionMutation("add", true, identityType))
                .then(userPermissionMutation("remove", false, identityType));
    }

    private LiteralArgumentBuilder<CommandSource> userPermissionMutation(String literal, boolean grant) {
        return userPermissionMutation(literal, grant, null);
    }

    private LiteralArgumentBuilder<CommandSource> userPermissionMutation(
            String literal,
            boolean grant,
            IdentityType identityType
    ) {
        LiteralArgumentBuilder<CommandSource> mutation = BrigadierCommand.literalArgumentBuilder(literal)
                .then(permissionScopeBranchForUser("network", ignored -> AccessScope.network(), grant, identityType));
        mutation.then(permissionScopeBranchForUser("group", context -> AccessScope.serverGroup(
                StringArgumentType.getString(context, "scope-id")
        ), grant, identityType));
        mutation.then(permissionScopeBranchForUser("server", context -> AccessScope.server(
                StringArgumentType.getString(context, "scope-id")
        ), grant, identityType));
        return BrigadierCommand.literalArgumentBuilder("permission").then(mutation);
    }

    private LiteralArgumentBuilder<CommandSource> permissionScopeBranchForUser(
            String literal,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            boolean grant
    ) {
        return permissionScopeBranchForUser(literal, scopeFactory, grant, null);
    }

    private LiteralArgumentBuilder<CommandSource> permissionScopeBranchForUser(
            String literal,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            boolean grant,
            IdentityType identityType
    ) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal);
        if (literal.equals("network")) {
            return branch.then(permissionNode("node")
                    .executes(context -> executeUserPermission(
                            context,
                            scopeFactory.apply(context),
                            grant,
                            identityType
                    )));
        }
        return branch.then(BrigadierCommand.requiredArgumentBuilder("scope-id", StringArgumentType.word())
                .suggests(literal.equals("group") ? serverGroupSuggestions() : serverSuggestions())
                .then(permissionNode("node")
                        .executes(context -> executeUserPermission(
                                context,
                                scopeFactory.apply(context),
                                grant,
                                identityType
                        ))));
    }

    private int createGroup(CommandContext<CommandSource> context) {
        String groupId = StringArgumentType.getString(context, "group-id");
        return schedule(context, () -> {
            switch (administrationService.createGroup(groupId)) {
                case ADDED -> {
                    send(context, "Created player group " + groupId + ".");
                    notifyStateChanged();
                }
                case ALREADY_EXISTS -> send(context, "Player group already exists: " + groupId + ".");
            }
        });
    }

    private int removeGroup(CommandContext<CommandSource> context) {
        String groupId = StringArgumentType.getString(context, "group-id");
        return schedule(context, () -> {
            switch (administrationService.removeGroup(groupId)) {
                case REMOVED -> {
                    send(context, "Removed player group " + groupId + ".");
                    notifyStateChanged();
                }
                case NOT_FOUND -> send(context, "Player group not found: " + groupId + ".");
            }
        });
    }

    private int executeGroupAccess(CommandContext<CommandSource> context, AccessScope scope, boolean grant) {
        String groupId = StringArgumentType.getString(context, "group-id");
        return schedule(context, () -> {
            try {
                PlayerGroupEntryResult result = grant
                        ? administrationService.grantAccess(groupId, scope)
                        : administrationService.revokeAccess(groupId, scope);
                sendEntryResult(context, result, "access", groupId, scope.toString());
                notifyStateChangedIfChanged(result);
            } catch (RuntimeException exception) {
                sendFailure(context, exception);
            }
        });
    }

    private int executeGroupPermission(CommandContext<CommandSource> context, AccessScope scope, boolean grant) {
        String groupId = StringArgumentType.getString(context, "group-id");
        String node = StringArgumentType.getString(context, "node");
        return schedule(context, () -> {
            try {
                PermissionGrant permission = new PermissionGrant(scope, node);
                PlayerGroupEntryResult result = grant
                        ? administrationService.grantPermission(groupId, permission)
                        : administrationService.revokePermission(groupId, permission);
                sendEntryResult(context, result, "permission", groupId, node + " @ " + scope);
                notifyStateChangedIfChanged(result);
            } catch (RuntimeException exception) {
                sendFailure(context, exception);
            }
        });
    }

    private int executeUser(
            CommandContext<CommandSource> context,
            IdentityType identityType,
            Function<PlayerIdentity, PlayerGroupEntryResult> operation
    ) {
        String name = StringArgumentType.getString(context, "player");
        try {
            PlayerIdentity.offline(name);
        } catch (IllegalArgumentException exception) {
            send(context, "Invalid Minecraft player name: " + name + ".");
            return 0;
        }
        return schedule(context, () -> resolveIdentity(context, name, identityType, identity -> {
            try {
                PlayerGroupEntryResult result = operation.apply(identity);
                sendEntryResult(
                        context,
                        result,
                        "group assignment",
                        identity.name() + " [" + identity.type() + "]",
                        ""
                );
                notifyStateChangedIfChanged(result);
            } catch (RuntimeException exception) {
                sendFailure(context, exception);
            }
        }));
    }

    private int executeUserPermission(
            CommandContext<CommandSource> context,
            AccessScope scope,
            boolean grant,
            IdentityType identityType
    ) {
        String name = StringArgumentType.getString(context, "player");
        String node = StringArgumentType.getString(context, "node");
        try {
            PlayerIdentity.offline(name);
            new PermissionGrant(scope, node);
        } catch (IllegalArgumentException exception) {
            send(context, "Invalid command input: " + exception.getMessage());
            return 0;
        }
        return schedule(context, () -> resolveIdentity(context, name, identityType, identity -> {
            try {
                PermissionGrant permission = new PermissionGrant(scope, node);
                if (grant) {
                    PermissionGrantAddResult result = administrationService.addDirectPermission(identity, permission);
                    send(context, result == PermissionGrantAddResult.ADDED
                            ? "Added direct permission " + node + " to " + identity.name()
                                    + " [" + identity.type() + "]."
                            : "Direct permission already exists for " + identity.name()
                                    + " [" + identity.type() + "].");
                    if (result == PermissionGrantAddResult.ADDED) {
                        notifyStateChanged();
                    }
                } else {
                    PermissionGrantRemoveResult result = administrationService.removeDirectPermission(identity, permission);
                    send(context, result == PermissionGrantRemoveResult.REMOVED
                            ? "Removed direct permission " + node + " from " + identity.name()
                                    + " [" + identity.type() + "]."
                            : "Direct permission not found for " + identity.name()
                                    + " [" + identity.type() + "].");
                    if (result == PermissionGrantRemoveResult.REMOVED) {
                        notifyStateChanged();
                    }
                }
            } catch (RuntimeException exception) {
                sendFailure(context, exception);
            }
        }));
    }

    private int schedule(CommandContext<CommandSource> context, Runnable task) {
        try {
            mutationExecutor.execute(task);
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban player-group mutation.", exception);
            send(context, "Failed to update player groups. Check the proxy log.");
            return 0;
        }
    }

    private void resolveIdentity(
            CommandContext<CommandSource> context,
            String name,
            IdentityType identityType,
            Consumer<PlayerIdentity> consumer
    ) {
        if (identityType == IdentityType.OFFLINE) {
            consumer.accept(PlayerIdentity.offline(name));
            return;
        }
        try {
            consumer.accept(profileResolver.resolve(name).toCompletableFuture().join().identity());
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause() instanceof OnlineProfileResolutionException resolved
                    ? resolved
                    : exception;
            if (identityType == null && cause instanceof OnlineProfileResolutionException) {
                consumer.accept(PlayerIdentity.offline(name));
            } else if (identityType == IdentityType.ONLINE) {
                send(context, "Unable to resolve an online identity for " + name + ".");
            } else {
                logger.error("Failed to resolve player identity for {}.", name, exception);
            }
        }
    }

    private void sendEntryResult(
            CommandContext<CommandSource> context,
            PlayerGroupEntryResult result,
            String kind,
            String subject,
            String value
    ) {
        String details = value.isEmpty() ? "" : " " + value;
        send(context, switch (result) {
            case ADDED -> "Added " + kind + details + " to " + subject + ".";
            case REMOVED -> "Removed " + kind + details + " from " + subject + ".";
            case ALREADY_EXISTS -> "That " + kind + " already exists for " + subject + ".";
            case NOT_FOUND -> "That " + kind + " was not found for " + subject + ".";
        });
    }

    private void sendFailure(CommandContext<CommandSource> context, RuntimeException exception) {
        logger.error("Failed to update player groups.", exception);
        send(context, exception.getMessage() == null ? "Failed to update player groups." : exception.getMessage());
    }

    private void notifyStateChangedIfChanged(PlayerGroupEntryResult result) {
        if (result == PlayerGroupEntryResult.ADDED || result == PlayerGroupEntryResult.REMOVED) {
            notifyStateChanged();
        }
    }

    private void notifyStateChanged() {
        try {
            stateChanged.run();
        } catch (RuntimeException exception) {
            logger.error("Failed to broadcast updated monban state.", exception);
        }
    }

    private int usage(CommandSource source) {
        if (!source.hasPermission(PERMISSION)) {
            send(source, "Unknown command. Type \"/help\" for help.");
            return 0;
        }
        send(source, "/monban group create <id>");
        send(source, "/monban group <id> access grant|revoke ...");
        send(source, "/monban group <id> permission add|remove ...");
        send(source, "/monban user <player> [online|offline] group add|remove <id>");
        send(source, "/monban user <player> [online|offline] permission add|remove ...");
        return Command.SINGLE_SUCCESS;
    }

    private void send(CommandContext<CommandSource> context, String message) {
        send(context.getSource(), message);
    }

    private static void send(CommandSource source, String message) {
        source.sendMessage(net.kyori.adventure.text.Component.text(message));
    }

    private SuggestionProvider<CommandSource> groupSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            administrationService.findGroups().stream()
                    .map(group -> group.id())
                    .filter(id -> id.startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSource> serverGroupSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            serverGroupCatalog.findAll().stream()
                    .map(group -> group.id())
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSource> serverSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            server.getAllServers().stream()
                    .map(registered -> registered.getServerInfo().getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSource> playerSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }
}
