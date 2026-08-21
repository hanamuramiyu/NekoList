package hanamuramiyu.monban.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.WhitelistPolicy;
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
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperWhitelistCommand {
    public static final String PERMISSION = "monban.command.whitelist";

    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final AccessGrantAdministrationService administrationService;
    private final Executor mutationExecutor;
    private final Function<CommandSender, Executor> callbackExecutor;
    private final BiConsumer<CommandSender, Component> messageSender;
    private final Logger logger;
    private final OnlineProfileResolver profileResolver;
    private final WhitelistPolicy whitelistPolicy;
    private final WhitelistStateStore whitelistStateStore;
    private final WhitelistPresentation presentation = new WhitelistPresentation();

    public PaperWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            Executor mutationExecutor,
            Function<CommandSender, Executor> callbackExecutor,
            BiConsumer<CommandSender, Component> messageSender,
            Logger logger
    ) {
        this(administrationService, mutationExecutor, callbackExecutor, messageSender, logger,
                OnlineProfileResolver.unavailable(), new WhitelistPolicy(false), enabled -> {
                });
    }

    public PaperWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            Executor mutationExecutor,
            Function<CommandSender, Executor> callbackExecutor,
            BiConsumer<CommandSender, Component> messageSender,
            Logger logger,
            OnlineProfileResolver profileResolver
    ) {
        this(administrationService, mutationExecutor, callbackExecutor, messageSender, logger,
                profileResolver, new WhitelistPolicy(false), enabled -> {
                });
    }

    public PaperWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            Executor mutationExecutor,
            Function<CommandSender, Executor> callbackExecutor,
            BiConsumer<CommandSender, Component> messageSender,
            Logger logger,
            OnlineProfileResolver profileResolver,
            WhitelistPolicy whitelistPolicy,
            WhitelistStateStore whitelistStateStore
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.mutationExecutor = Objects.requireNonNull(mutationExecutor, "mutationExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver");
        this.whitelistPolicy = Objects.requireNonNull(whitelistPolicy, "whitelistPolicy");
        this.whitelistStateStore = Objects.requireNonNull(whitelistStateStore, "whitelistStateStore");
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("whitelist")
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!sender.hasPermission(PERMISSION)) {
                        send(sender, new MonbanUi().unknownCommand());
                    } else {
                        sendAll(sender, presentation.usage());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(mutationBranch("add", Mutation.ADD))
                .then(mutationBranch("remove", Mutation.REMOVE))
                .then(policyBranch("enable", true))
                .then(policyBranch("disable", false))
                .then(listBranch());
    }

    private LiteralArgumentBuilder<CommandSourceStack> policyBranch(String literal, boolean enabled) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> executePolicy(context.getSource().getSender(), enabled));
    }

    private int executePolicy(CommandSender sender, boolean enabled) {
        try {
            mutationExecutor.execute(() -> runPolicyUpdate(sender, enabled));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist policy update.", exception);
            send(sender, presentation.policyUpdateFailure(WhitelistPresentation.LogTarget.SERVER));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private void runPolicyUpdate(CommandSender sender, boolean enabled) {
        synchronized (whitelistPolicy) {
            if (whitelistPolicy.enabled() == enabled) {
                dispatchCallback(sender, () -> send(sender,
                        enabled ? presentation.alreadyEnabled() : presentation.alreadyDisabled()));
                return;
            }
            try {
                whitelistStateStore.save(enabled);
                whitelistPolicy.setEnabled(enabled);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed to persist monban whitelist policy.", exception);
                dispatchCallback(sender, () -> send(sender,
                        presentation.policyUpdateFailure(WhitelistPresentation.LogTarget.SERVER)));
                return;
            }
            dispatchCallback(sender, () -> send(sender,
                    enabled ? presentation.enabled() : presentation.disabled()));
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> mutationBranch(String literal, Mutation mutation) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(PERMISSION));
        addIdentityBranches(branch, mutation);
        return branch;
    }

    private void addIdentityBranches(ArgumentBuilder<CommandSourceStack, ?> parent, Mutation mutation) {
        parent.then(Commands.literal("offline")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> executeMutation(context, mutation, IdentityType.OFFLINE))));

        parent.then(Commands.literal("online")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> executeAutomaticMutation(context, mutation))
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .executes(context -> executeMutation(context, mutation, IdentityType.ONLINE)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> listBranch() {
        LiteralArgumentBuilder<CommandSourceStack> list = Commands.literal("list")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> executeList(context, null, 1));
        list.then(pageArgument(null));
        list.then(identityListBranch("offline", IdentityType.OFFLINE));
        list.then(identityListBranch("online", IdentityType.ONLINE));
        return list;
    }

    private LiteralArgumentBuilder<CommandSourceStack> identityListBranch(String literal, IdentityType type) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(literal)
                .executes(context -> executeList(context, type, 1));
        branch.then(pageArgument(type));
        return branch;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> pageArgument(IdentityType filter) {
        return Commands.argument("page", StringArgumentType.word())
                .executes(context -> executeList(
                        context,
                        filter,
                        StringArgumentType.getString(context, "page")
                ));
    }

    private int executeMutation(
            CommandContext<CommandSourceStack> context,
            Mutation mutation,
            IdentityType identityType
    ) {
        CommandSender sender = context.getSource().getSender();
        PlayerIdentity identity;
        try {
            identity = parseIdentity(context, identityType);
        } catch (CommandInputException exception) {
            send(sender, exception.component());
            return 0;
        }

        try {
            mutationExecutor.execute(() -> runMutation(sender, mutation, identity));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist mutation.", exception);
            send(sender, presentation.mutationFailure(WhitelistPresentation.LogTarget.SERVER));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeAutomaticMutation(
            CommandContext<CommandSourceStack> context,
            Mutation mutation
    ) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "name");
        try {
            PlayerIdentity.offline(name);
        } catch (IllegalArgumentException exception) {
            send(sender, presentation.invalidPlayerName(name));
            return 0;
        }
        try {
            mutationExecutor.execute(() -> {
                try {
                    OnlineProfile profile = profileResolver.resolve(name).toCompletableFuture().join();
                    runMutation(sender, mutation, profile.identity());
                } catch (RuntimeException exception) {
                    Throwable cause = exception.getCause() instanceof OnlineProfileResolutionException resolved
                            ? resolved
                            : exception;
                    dispatchCallback(sender, () -> send(sender,
                            cause instanceof OnlineProfileResolutionException resolved
                                    && resolved.kind() == OnlineProfileResolutionException.Kind.NOT_FOUND
                                    ? presentation.onlineProfileNotFound()
                                    : cause instanceof OnlineProfileResolutionException
                                    ? presentation.onlineProfileUnavailable()
                                    : presentation.mutationFailure(WhitelistPresentation.LogTarget.SERVER)));
                    if (!(cause instanceof OnlineProfileResolutionException)) {
                        logger.log(Level.SEVERE, "Failed to resolve online profile for " + name + ".", exception);
                    }
                }
            });
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule online profile lookup.", exception);
            send(sender, presentation.onlineProfileUnavailable());
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> context, IdentityType filter, String pageValue) {
        CommandSender sender = context.getSource().getSender();
        int page;
        try {
            page = parsePage(pageValue);
        } catch (CommandInputException exception) {
            send(sender, exception.component());
            return 0;
        }
        return executeList(context, filter, page);
    }

    private int executeList(CommandContext<CommandSourceStack> context, IdentityType filter, int page) {
        CommandSender sender = context.getSource().getSender();
        try {
            WhitelistListView view = presentation.listing(
                    administrationService.findAll(NETWORK_SCOPE),
                    filter,
                    page
            );
            sendAll(sender, view.lines());
            return view.successful() ? Command.SINGLE_SUCCESS : 0;
        } catch (AccessGrantScopeValidationException exception) {
            send(sender, presentation.validationFailure(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to read the monban whitelist.", exception);
            send(sender, presentation.readFailure(WhitelistPresentation.LogTarget.SERVER));
            return 0;
        }
    }

    private PlayerIdentity parseIdentity(
            CommandContext<CommandSourceStack> context,
            IdentityType identityType
    ) {
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

    private UUID parseUuid(CommandContext<CommandSourceStack> context) {
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

    private void runMutation(CommandSender sender, Mutation mutation, PlayerIdentity identity) {
        try {
            MutationOutcome outcome = switch (mutation) {
                case ADD -> new MutationOutcome(
                        administrationService.grant(new AccessGrant(NETWORK_SCOPE, identity)),
                        null
                );
                case REMOVE -> new MutationOutcome(
                        null,
                        administrationService.revoke(NETWORK_SCOPE, identity)
                );
            };
            dispatchCallback(sender, () -> sendMutationOutcome(sender, mutation, identity, outcome));
        } catch (AccessGrantScopeValidationException exception) {
            dispatchCallback(sender, () -> send(sender, presentation.validationFailure(exception.getMessage())));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to update the monban whitelist for " + identity + ".", exception);
            dispatchCallback(sender, () -> send(
                    sender,
                    presentation.mutationFailure(WhitelistPresentation.LogTarget.SERVER)
            ));
        }
    }

    private void sendMutationOutcome(
            CommandSender sender,
            Mutation mutation,
            PlayerIdentity identity,
            MutationOutcome outcome
    ) {
        switch (mutation) {
            case ADD -> send(
                    sender,
                    outcome.addResult() == AccessGrantAddResult.ADDED
                            ? presentation.added(identity)
                            : presentation.alreadyExists()
            );
            case REMOVE -> send(
                    sender,
                    outcome.removeResult() == AccessGrantRemoveResult.REMOVED
                            ? presentation.removed(identity)
                            : presentation.notFound()
            );
        }
    }

    private void dispatchCallback(CommandSender sender, Runnable callback) {
        try {
            callbackExecutor.apply(sender).execute(callback);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist command response.", exception);
        }
    }

    private void send(CommandSender sender, Component component) {
        messageSender.accept(sender, component);
    }

    private void sendAll(CommandSender sender, List<Component> components) {
        components.forEach(component -> messageSender.accept(sender, component));
    }

    private enum Mutation {
        ADD,
        REMOVE
    }

    @FunctionalInterface
    public interface WhitelistStateStore {
        void save(boolean enabled) throws Exception;
    }

    private record MutationOutcome(AccessGrantAddResult addResult, AccessGrantRemoveResult removeResult) {
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
}
