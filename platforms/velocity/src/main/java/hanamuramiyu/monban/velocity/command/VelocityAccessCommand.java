package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.OnlineProfile;
import hanamuramiyu.monban.identity.OnlineProfileResolutionException;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.presentation.AccessListView;
import hanamuramiyu.monban.presentation.AccessPresentation;
import hanamuramiyu.monban.presentation.MonbanUi;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class VelocityAccessCommand {
    public static final String PERMISSION = "monban.command.access";

    private final AccessGrantAdministrationService administrationService;
    private final ServerGroupCatalog serverGroupCatalog;
    private final ProxyServer server;
    private final Executor mutationExecutor;
    private final Logger logger;
    private final AccessPresentation presentation;
    private final OnlineProfileResolver profileResolver;
    private final Runnable stateChanged;

    public VelocityAccessCommand(
            AccessGrantAdministrationService administrationService,
            ServerGroupCatalog serverGroupCatalog,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger
    ) {
        this(
                administrationService,
                serverGroupCatalog,
                server,
                mutationExecutor,
                logger,
                OnlineProfileResolver.unavailable(),
                () -> {
                }
        );
    }

    public VelocityAccessCommand(
            AccessGrantAdministrationService administrationService,
            ServerGroupCatalog serverGroupCatalog,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver
    ) {
        this(administrationService, serverGroupCatalog, server, mutationExecutor, logger, profileResolver, () -> {
        });
    }

    public VelocityAccessCommand(
            AccessGrantAdministrationService administrationService,
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
        this.presentation = new AccessPresentation();
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
    }

    public LiteralArgumentBuilder<CommandSource> build() {
        return BrigadierCommand.literalArgumentBuilder("access")
                .executes(context -> {
                    if (!context.getSource().hasPermission(PERMISSION)) {
                        context.getSource().sendMessage(new MonbanUi().unknownCommand());
                    } else {
                        presentation.usage().forEach(context.getSource()::sendMessage);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(mutationBranch("grant", Mutation.GRANT))
                .then(mutationBranch("revoke", Mutation.REVOKE))
                .then(listBranch());
    }

    private LiteralArgumentBuilder<CommandSource> mutationBranch(String literal, Mutation mutation) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .requires(source -> source.hasPermission(PERMISSION))
                .then(networkBranch(mutation))
                .then(groupBranch(mutation))
                .then(serverBranch(mutation));
    }

    private LiteralArgumentBuilder<CommandSource> networkBranch(Mutation mutation) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder("network");
        addIdentityBranches(branch, mutation, ignored -> AccessScope.network());
        return branch;
    }

    private LiteralArgumentBuilder<CommandSource> groupBranch(Mutation mutation) {
        RequiredArgumentBuilder<CommandSource, String> group = BrigadierCommand.requiredArgumentBuilder(
                        "group-id",
                        StringArgumentType.word()
                )
                .suggests(groupSuggestions());
        addIdentityBranches(
                group,
                mutation,
                context -> AccessScope.serverGroup(StringArgumentType.getString(context, "group-id"))
        );
        return BrigadierCommand.literalArgumentBuilder("group").then(group);
    }

    private LiteralArgumentBuilder<CommandSource> serverBranch(Mutation mutation) {
        RequiredArgumentBuilder<CommandSource, String> serverName = BrigadierCommand.requiredArgumentBuilder(
                        "server-name",
                        StringArgumentType.string()
                )
                .suggests(serverSuggestions());
        addIdentityBranches(
                serverName,
                mutation,
                context -> AccessScope.server(StringArgumentType.getString(context, "server-name"))
        );
        return BrigadierCommand.literalArgumentBuilder("server").then(serverName);
    }

    private LiteralArgumentBuilder<CommandSource> listBranch() {
        LiteralArgumentBuilder<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
                .requires(source -> source.hasPermission(PERMISSION))
                .executes(context -> executeList(context, null, 1));

        list.then(pageArgument(null));
        list.then(listNetworkBranch());
        list.then(listGroupBranch());
        list.then(listServerBranch());
        return list;
    }

    private LiteralArgumentBuilder<CommandSource> listNetworkBranch() {
        LiteralArgumentBuilder<CommandSource> network = BrigadierCommand.literalArgumentBuilder("network")
                .executes(context -> executeList(context, ignored -> AccessScope.network(), 1));
        network.then(pageArgument(ignored -> AccessScope.network()));
        return network;
    }

    private LiteralArgumentBuilder<CommandSource> listGroupBranch() {
        RequiredArgumentBuilder<CommandSource, String> group = BrigadierCommand.requiredArgumentBuilder(
                        "group-id",
                        StringArgumentType.word()
                )
                .suggests(groupSuggestions())
                .executes(context -> executeList(
                        context,
                        value -> AccessScope.serverGroup(StringArgumentType.getString(value, "group-id")),
                        1
                ));
        group.then(pageArgument(
                context -> AccessScope.serverGroup(StringArgumentType.getString(context, "group-id"))
        ));
        return BrigadierCommand.literalArgumentBuilder("group").then(group);
    }

    private LiteralArgumentBuilder<CommandSource> listServerBranch() {
        RequiredArgumentBuilder<CommandSource, String> serverName = BrigadierCommand.requiredArgumentBuilder(
                        "server-name",
                        StringArgumentType.string()
                )
                .suggests(serverSuggestions())
                .executes(context -> executeList(
                        context,
                        value -> AccessScope.server(StringArgumentType.getString(value, "server-name")),
                        1
                ));
        serverName.then(pageArgument(
                context -> AccessScope.server(StringArgumentType.getString(context, "server-name"))
        ));
        return BrigadierCommand.literalArgumentBuilder("server").then(serverName);
    }

    private RequiredArgumentBuilder<CommandSource, Integer> pageArgument(
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory
    ) {
        return BrigadierCommand.requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                .executes(context -> executeList(
                        context,
                        scopeFactory,
                        IntegerArgumentType.getInteger(context, "page")
                ));
    }

    private void addIdentityBranches(
            ArgumentBuilder<CommandSource, ?> parent,
            Mutation mutation,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory
    ) {
        parent.then(offlineBranch(mutation, scopeFactory));
        parent.then(onlineBranch(mutation, scopeFactory));
    }

    private LiteralArgumentBuilder<CommandSource> offlineBranch(
            Mutation mutation,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory
    ) {
        return BrigadierCommand.literalArgumentBuilder("offline")
                .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .executes(context -> executeMutation(context, mutation, scopeFactory, IdentityType.OFFLINE)));
    }

    private LiteralArgumentBuilder<CommandSource> onlineBranch(
            Mutation mutation,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory
    ) {
        return BrigadierCommand.literalArgumentBuilder("online")
                .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .executes(context -> executeAutomaticMutation(context, mutation, scopeFactory))
                        .then(BrigadierCommand.requiredArgumentBuilder("uuid", StringArgumentType.word())
                                .executes(context -> executeMutation(
                                        context,
                                        mutation,
                                        scopeFactory,
                                        IdentityType.ONLINE
                                ))));
    }

    private int executeMutation(
            CommandContext<CommandSource> context,
            Mutation mutation,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            IdentityType identityType
    ) {
        AccessScope scope;
        PlayerIdentity identity;
        try {
            scope = scopeFactory.apply(context);
            identity = parseIdentity(context, identityType);
        } catch (CommandInputException exception) {
            context.getSource().sendMessage(presentation.invalidInput(exception.getMessage()));
            return 0;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendMessage(presentation.invalidInput("Invalid command input: " + exception.getMessage()));
            return 0;
        }

        try {
            mutationExecutor.execute(() -> runMutation(context.getSource(), mutation, scope, identity));
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban access-grant mutation.", exception);
            context.getSource().sendMessage(presentation.mutationFailure());
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeAutomaticMutation(
            CommandContext<CommandSource> context,
            Mutation mutation,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory
    ) {
        String name = StringArgumentType.getString(context, "name");
        AccessScope scope;
        try {
            PlayerIdentity.offline(name);
            scope = scopeFactory.apply(context);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendMessage(presentation.invalidInput("Invalid command input: " + exception.getMessage()));
            return 0;
        }
        try {
            mutationExecutor.execute(() -> {
                try {
                    OnlineProfile profile = profileResolver.resolve(name).toCompletableFuture().join();
                    runMutation(context.getSource(), mutation, scope, profile.identity());
                } catch (RuntimeException exception) {
                    Throwable cause = exception.getCause() instanceof OnlineProfileResolutionException resolved
                            ? resolved
                            : exception;
                    if (cause instanceof OnlineProfileResolutionException resolved
                            && resolved.kind() == OnlineProfileResolutionException.Kind.NOT_FOUND) {
                        context.getSource().sendMessage(presentation.invalidInput("Online profile not found."));
                    } else if (cause instanceof OnlineProfileResolutionException) {
                        context.getSource().sendMessage(presentation.invalidInput(
                                "Online profile lookup is temporarily unavailable. Try again later."
                        ));
                    } else {
                        logger.error("Failed to resolve online profile for {}.", name, exception);
                        context.getSource().sendMessage(presentation.mutationFailure());
                    }
                }
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule online profile lookup.", exception);
            context.getSource().sendMessage(presentation.invalidInput(
                    "Online profile lookup is temporarily unavailable. Try again later."
            ));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(
            CommandContext<CommandSource> context,
            Function<CommandContext<CommandSource>, AccessScope> scopeFactory,
            int page
    ) {
        AccessScope scope = null;
        if (scopeFactory != null) {
            try {
                scope = scopeFactory.apply(context);
            } catch (IllegalArgumentException exception) {
                context.getSource().sendMessage(presentation.invalidInput("Invalid command input: " + exception.getMessage()));
                return 0;
            }
        }

        try {
            List<AccessGrant> grants = scope == null
                    ? administrationService.findAll()
                    : administrationService.findAll(scope);

            return sendListing(context.getSource(), scope, grants, page)
                    ? Command.SINGLE_SUCCESS
                    : 0;
        } catch (AccessGrantScopeValidationException exception) {
            context.getSource().sendMessage(presentation.invalidInput(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            logger.error("Failed to read access grants for scope {}.", scope, exception);
            context.getSource().sendMessage(presentation.readFailure());
            return 0;
        }
    }

    private boolean sendListing(CommandSource source, AccessScope scope, List<AccessGrant> grants, int page) {
        AccessListView view = presentation.listing(grants, scope, page);
        view.lines().forEach(source::sendMessage);
        return view.successful();
    }

    private PlayerIdentity parseIdentity(CommandContext<CommandSource> context, IdentityType identityType) {
        String name = StringArgumentType.getString(context, "name");
        try {
            return switch (identityType) {
                case OFFLINE -> PlayerIdentity.offline(name);
                case ONLINE -> PlayerIdentity.online(name, parseUuid(context));
            };
        } catch (CommandInputException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException("Invalid Minecraft player name: " + name + ".", exception);
        }
    }

    private UUID parseUuid(CommandContext<CommandSource> context) {
        String value = StringArgumentType.getString(context, "uuid");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException("Invalid UUID: " + value + ".", exception);
        }
    }

    private void runMutation(
            CommandSource source,
            Mutation mutation,
            AccessScope scope,
            PlayerIdentity identity
    ) {
        try {
            switch (mutation) {
                case GRANT -> sendGrantResult(source, scope, identity);
                case REVOKE -> sendRevokeResult(source, scope, identity);
            }
        } catch (AccessGrantScopeValidationException exception) {
            source.sendMessage(presentation.invalidInput(exception.getMessage()));
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to update access grants for {} {}.",
                    scope,
                    identity,
                    exception
            );
            source.sendMessage(presentation.mutationFailure());
        }
    }

    private void sendGrantResult(CommandSource source, AccessScope scope, PlayerIdentity identity) {
        AccessGrantAddResult result = administrationService.grant(new AccessGrant(scope, identity));
        switch (result) {
            case ADDED -> {
                source.sendMessage(presentation.added(scope, identity));
                notifyStateChanged();
            }
            case ALREADY_EXISTS -> source.sendMessage(presentation.alreadyExists());
        }
    }

    private void sendRevokeResult(CommandSource source, AccessScope scope, PlayerIdentity identity) {
        AccessGrantRemoveResult result = administrationService.revoke(scope, identity);
        switch (result) {
            case REMOVED -> {
                source.sendMessage(presentation.removed(scope, identity));
                notifyStateChanged();
            }
            case NOT_FOUND -> source.sendMessage(presentation.notFound());
        }
    }

    private void notifyStateChanged() {
        try {
            stateChanged.run();
        } catch (RuntimeException exception) {
            logger.error("Failed to broadcast updated monban state.", exception);
        }
    }

    private SuggestionProvider<CommandSource> groupSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            serverGroupCatalog.findAll().stream()
                    .map(group -> group.id())
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSource> serverSuggestions() {
        return (context, builder) -> {
            String remaining = unquote(builder.getRemaining()).toLowerCase(Locale.ROOT);
            server.getAllServers().stream()
                    .map(RegisteredServer::getServerInfo)
                    .map(info -> info.getName())
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .map(VelocityAccessCommand::quoteIfRequired)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSource> playerSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemainingLowerCase();
            server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static String quoteIfRequired(String value) {
        boolean needsQuotes = value.isEmpty();
        for (int index = 0; index < value.length() && !needsQuotes; index++) {
            char character = value.charAt(index);
            needsQuotes = Character.isWhitespace(character) || character == '"' || character == '\\';
        }
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        if (!value.isEmpty() && value.charAt(0) == '"') {
            return value.substring(1);
        }
        return value;
    }

    private enum Mutation {
        GRANT,
        REVOKE
    }

    private static final class CommandInputException extends IllegalArgumentException {
        private CommandInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
