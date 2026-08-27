package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.Command;
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
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.OnlineProfile;
import hanamuramiyu.monban.identity.OnlineProfileResolutionException;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.presentation.WhitelistListView;
import hanamuramiyu.monban.presentation.WhitelistPresentation;
import hanamuramiyu.monban.presentation.MonbanUi;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class VelocityWhitelistCommand {
    public static final String PERMISSION = "monban.command.whitelist";

    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final AccessGrantAdministrationService administrationService;
    private final ProxyServer server;
    private final Executor mutationExecutor;
    private final Logger logger;
    private final OnlineProfileResolver profileResolver;
    private final WhitelistPolicy whitelistPolicy;
    private final WhitelistStateStore whitelistStateStore;
    private final Runnable stateChanged;
    private final WhitelistPresentation presentation = new WhitelistPresentation();

    public VelocityWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger
    ) {
        this(
                administrationService,
                server,
                mutationExecutor,
                logger,
                OnlineProfileResolver.unavailable(),
                new WhitelistPolicy(false),
                enabled -> {
                },
                () -> {
                }
        );
    }

    public VelocityWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver
    ) {
        this(
                administrationService,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                new WhitelistPolicy(false),
                enabled -> {
                },
                () -> {
                }
        );
    }

    public VelocityWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver,
            WhitelistPolicy whitelistPolicy,
            WhitelistStateStore whitelistStateStore
    ) {
        this(
                administrationService,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                whitelistPolicy,
                whitelistStateStore,
                () -> {
                }
        );
    }

    public VelocityWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver,
            WhitelistPolicy whitelistPolicy,
            WhitelistStateStore whitelistStateStore,
            Runnable stateChanged
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.server = Objects.requireNonNull(server, "server");
        this.mutationExecutor = Objects.requireNonNull(mutationExecutor, "mutationExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver");
        this.whitelistPolicy = Objects.requireNonNull(whitelistPolicy, "whitelistPolicy");
        this.whitelistStateStore = Objects.requireNonNull(whitelistStateStore, "whitelistStateStore");
        this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
    }

    public LiteralArgumentBuilder<CommandSource> build() {
        LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder("whitelist")
                .executes(context -> {
                    if (!context.getSource().hasPermission(PERMISSION)) {
                        context.getSource().sendMessage(new MonbanUi().unknownCommand());
                    } else {
                        presentation.usage().forEach(context.getSource()::sendMessage);
                    }
                    return Command.SINGLE_SUCCESS;
                });
        root.then(mutationBranch("add", Mutation.ADD));
        root.then(mutationBranch("remove", Mutation.REMOVE));
        root.then(policyBranch("enable", true));
        root.then(policyBranch("disable", false));
        root.then(listBranch());
        return root;
    }

    private LiteralArgumentBuilder<CommandSource> policyBranch(String literal, boolean enabled) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .requires(source -> source.hasPermission(PERMISSION))
                .executes(context -> executePolicy(context.getSource(), enabled));
    }

    private int executePolicy(CommandSource source, boolean enabled) {
        try {
            mutationExecutor.execute(() -> runPolicyUpdate(source, enabled));
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban whitelist policy update.", exception);
            source.sendMessage(presentation.policyUpdateFailure(WhitelistPresentation.LogTarget.PROXY));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private void runPolicyUpdate(CommandSource source, boolean enabled) {
        synchronized (whitelistPolicy) {
            if (whitelistPolicy.enabled() == enabled) {
                source.sendMessage(enabled ? presentation.alreadyEnabled() : presentation.alreadyDisabled());
                return;
            }
            try {
                whitelistStateStore.save(enabled);
                whitelistPolicy.setEnabled(enabled);
            } catch (Exception exception) {
                logger.error("Failed to persist monban whitelist policy.", exception);
                source.sendMessage(presentation.policyUpdateFailure(WhitelistPresentation.LogTarget.PROXY));
                return;
            }
            source.sendMessage(enabled ? presentation.enabled() : presentation.disabled());
        }
    }

    private LiteralArgumentBuilder<CommandSource> mutationBranch(String literal, Mutation mutation) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal)
                .requires(source -> source.hasPermission(PERMISSION));
        addIdentityBranches(branch, mutation);
        return branch;
    }

    private void addIdentityBranches(ArgumentBuilder<CommandSource, ?> parent, Mutation mutation) {
        parent.then(BrigadierCommand.literalArgumentBuilder("offline")
                .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .executes(context -> executeMutation(context, mutation, IdentityType.OFFLINE))));

        parent.then(BrigadierCommand.literalArgumentBuilder("online")
                .then(BrigadierCommand.requiredArgumentBuilder("name", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .executes(context -> executeAutomaticMutation(context, mutation))
                        .then(BrigadierCommand.requiredArgumentBuilder("uuid", StringArgumentType.word())
                                .executes(context -> executeMutation(context, mutation, IdentityType.ONLINE)))));
    }

    private LiteralArgumentBuilder<CommandSource> listBranch() {
        LiteralArgumentBuilder<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
                .requires(source -> source.hasPermission(PERMISSION))
                .executes(context -> executeList(context, null, 1));
        list.then(pageArgument(null));
        list.then(identityListBranch("offline", IdentityType.OFFLINE));
        list.then(identityListBranch("online", IdentityType.ONLINE));
        return list;
    }

    private LiteralArgumentBuilder<CommandSource> identityListBranch(String literal, IdentityType type) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal)
                .executes(context -> executeList(context, type, 1));
        branch.then(pageArgument(type));
        return branch;
    }

    private RequiredArgumentBuilder<CommandSource, String> pageArgument(IdentityType filter) {
        return BrigadierCommand.requiredArgumentBuilder("page", StringArgumentType.word())
                .executes(context -> executeList(
                        context,
                        filter,
                        StringArgumentType.getString(context, "page")
                ));
    }

    private int executeMutation(
            CommandContext<CommandSource> context,
            Mutation mutation,
            IdentityType identityType
    ) {
        PlayerIdentity identity;
        try {
            identity = parseIdentity(context, identityType);
        } catch (CommandInputException exception) {
            context.getSource().sendMessage(exception.component());
            return 0;
        }

        try {
            mutationExecutor.execute(() -> runMutation(context.getSource(), mutation, identity));
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban whitelist mutation.", exception);
            context.getSource().sendMessage(presentation.mutationFailure(WhitelistPresentation.LogTarget.PROXY));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeAutomaticMutation(CommandContext<CommandSource> context, Mutation mutation) {
        String name = StringArgumentType.getString(context, "name");
        try {
            PlayerIdentity.offline(name);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendMessage(presentation.invalidPlayerName(name));
            return 0;
        }
        try {
            mutationExecutor.execute(() -> {
                try {
                    OnlineProfile profile = profileResolver.resolve(name).toCompletableFuture().join();
                    runMutation(context.getSource(), mutation, profile.identity());
                } catch (RuntimeException exception) {
                    Throwable cause = exception.getCause() instanceof OnlineProfileResolutionException resolved
                            ? resolved
                            : exception;
                    if (cause instanceof OnlineProfileResolutionException resolved
                            && resolved.kind() == OnlineProfileResolutionException.Kind.NOT_FOUND) {
                        context.getSource().sendMessage(presentation.onlineProfileNotFound());
                    } else if (cause instanceof OnlineProfileResolutionException) {
                        context.getSource().sendMessage(presentation.onlineProfileUnavailable());
                    } else {
                        logger.error("Failed to resolve online profile for {}.", name, exception);
                        context.getSource().sendMessage(presentation.mutationFailure(WhitelistPresentation.LogTarget.PROXY));
                    }
                }
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule online profile lookup.", exception);
            context.getSource().sendMessage(presentation.onlineProfileUnavailable());
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSource> context, IdentityType filter, String pageValue) {
        int page;
        try {
            page = parsePage(pageValue);
        } catch (CommandInputException exception) {
            context.getSource().sendMessage(exception.component());
            return 0;
        }
        return executeList(context, filter, page);
    }

    private int executeList(CommandContext<CommandSource> context, IdentityType filter, int page) {
        try {
            WhitelistListView view = presentation.listing(
                    administrationService.findAll(NETWORK_SCOPE),
                    filter,
                    page
            );
            view.lines().forEach(context.getSource()::sendMessage);
            return view.successful() ? Command.SINGLE_SUCCESS : 0;
        } catch (AccessGrantScopeValidationException exception) {
            context.getSource().sendMessage(presentation.validationFailure(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            logger.error("Failed to read the monban whitelist.", exception);
            context.getSource().sendMessage(presentation.readFailure(WhitelistPresentation.LogTarget.PROXY));
            return 0;
        }
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
            throw new CommandInputException(presentation.invalidPlayerName(name), exception);
        }
    }

    private UUID parseUuid(CommandContext<CommandSource> context) {
        String value = StringArgumentType.getString(context, "uuid");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException(presentation.invalidUuid(value), exception);
        }
    }

    private int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            if (page < 1) {
                throw new NumberFormatException("page must be positive");
            }
            return page;
        } catch (NumberFormatException exception) {
            throw new CommandInputException(presentation.invalidPage(value), exception);
        }
    }

    private void runMutation(CommandSource source, Mutation mutation, PlayerIdentity identity) {
        try {
            switch (mutation) {
                case ADD -> sendAddResult(source, identity);
                case REMOVE -> sendRemoveResult(source, identity);
            }
        } catch (AccessGrantScopeValidationException exception) {
            source.sendMessage(presentation.validationFailure(exception.getMessage()));
        } catch (RuntimeException exception) {
            logger.error("Failed to update the monban whitelist for {}.", identity, exception);
            source.sendMessage(presentation.mutationFailure(WhitelistPresentation.LogTarget.PROXY));
        }
    }

    private void sendAddResult(CommandSource source, PlayerIdentity identity) {
        AccessGrantAddResult result = administrationService.grant(new AccessGrant(NETWORK_SCOPE, identity));
        if (result == AccessGrantAddResult.ADDED) {
            source.sendMessage(presentation.added(identity));
            notifyStateChanged();
        } else {
            source.sendMessage(presentation.alreadyExists());
        }
    }

    private void sendRemoveResult(CommandSource source, PlayerIdentity identity) {
        AccessGrantRemoveResult result = administrationService.revoke(NETWORK_SCOPE, identity);
        if (result == AccessGrantRemoveResult.REMOVED) {
            source.sendMessage(presentation.removed(identity));
            notifyStateChanged();
        } else {
            source.sendMessage(presentation.notFound());
        }
    }

    private void notifyStateChanged() {
        try {
            stateChanged.run();
        } catch (RuntimeException exception) {
            logger.error("Failed to broadcast updated monban state.", exception);
        }
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

    private enum Mutation {
        ADD,
        REMOVE
    }

    private static final class CommandInputException extends IllegalArgumentException {
        private final Component component;

        private CommandInputException(Component component, Throwable cause) {
            super(cause);
            this.component = Objects.requireNonNull(component, "component");
        }

        private Component component() {
            return component;
        }
    }

    @FunctionalInterface
    public interface WhitelistStateStore {
        void save(boolean enabled) throws Exception;
    }
}
