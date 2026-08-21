package hanamuramiyu.monban.bukkit.command;

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
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitWhitelistCommand {
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

    public BukkitWhitelistCommand(
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

    public BukkitWhitelistCommand(
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

    public BukkitWhitelistCommand(
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

    public boolean execute(CommandSender sender, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!sender.hasPermission(PERMISSION)) {
            send(sender, new MonbanUi().unknownCommand());
            return true;
        }
        if (args.length == 0) {
            sendAll(sender, presentation.usage());
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> executeMutation(sender, args, Mutation.ADD);
            case "remove" -> executeMutation(sender, args, Mutation.REMOVE);
            case "enable" -> executePolicy(sender, true);
            case "disable" -> executePolicy(sender, false);
            case "list" -> executeList(sender, args);
            default -> {
                sendAll(sender, presentation.usage());
                yield true;
            }
        };
    }

    public List<String> suggestions(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(args[0], List.of("add", "remove", "enable", "disable", "list"));
        }
        if ((equalsIgnoreCase(args[0], "add") || equalsIgnoreCase(args[0], "remove")) && args.length == 2) {
            return matching(args[1], List.of("offline", "online"));
        }
        if (equalsIgnoreCase(args[0], "list") && args.length == 2) {
            return matching(args[1], List.of("offline", "online"));
        }
        return List.of();
    }

    private boolean executePolicy(CommandSender sender, boolean enabled) {
        try {
            mutationExecutor.execute(() -> runPolicyUpdate(sender, enabled));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist policy update.", exception);
            send(sender, presentation.policyUpdateFailure(WhitelistPresentation.LogTarget.SERVER));
        }
        return true;
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

    private boolean executeMutation(CommandSender sender, String[] args, Mutation mutation) {
        if (args.length == 3 && equalsIgnoreCase(args[1], "online")) {
            return executeAutomaticMutation(sender, args[2], mutation);
        }
        PlayerIdentity identity;
        try {
            identity = parseMutationIdentity(args);
        } catch (CommandInputException exception) {
            send(sender, exception.component());
            return true;
        }

        try {
            mutationExecutor.execute(() -> runMutation(sender, mutation, identity));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist mutation.", exception);
            send(sender, presentation.mutationFailure(WhitelistPresentation.LogTarget.SERVER));
        }
        return true;
    }

    private boolean executeAutomaticMutation(CommandSender sender, String name, Mutation mutation) {
        try {
            PlayerIdentity.offline(name);
        } catch (IllegalArgumentException exception) {
            send(sender, presentation.invalidPlayerName(name));
            return true;
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
        }
        return true;
    }

    private boolean executeList(CommandSender sender, String[] args) {
        ListRequest request;
        try {
            request = parseListRequest(args);
        } catch (CommandInputException exception) {
            send(sender, exception.component());
            return true;
        }

        try {
            WhitelistListView view = presentation.listing(
                    administrationService.findAll(NETWORK_SCOPE),
                    request.filter(),
                    request.page()
            );
            sendAll(sender, view.lines());
        } catch (AccessGrantScopeValidationException exception) {
            send(sender, presentation.validationFailure(exception.getMessage()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to read the monban whitelist.", exception);
            send(sender, presentation.readFailure(WhitelistPresentation.LogTarget.SERVER));
        }
        return true;
    }

    private PlayerIdentity parseMutationIdentity(String[] args) {
        if (args.length < 3) {
            throw new CommandInputException(presentation.invalidUsage(args[0]));
        }

        IdentityType type = parseIdentityType(args[1]);
        String name = args[2];
        try {
            return switch (type) {
                case OFFLINE -> {
                    if (args.length != 3) {
                        throw new CommandInputException(presentation.invalidUsage(args[0]));
                    }
                    yield PlayerIdentity.offline(name);
                }
                case ONLINE -> {
                    if (args.length != 4) {
                        throw new CommandInputException(presentation.invalidUsage(args[0]));
                    }
                    yield PlayerIdentity.online(name, parseUuid(args[3]));
                }
            };
        } catch (CommandInputException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException(presentation.invalidPlayerName(name), exception);
        }
    }

    private ListRequest parseListRequest(String[] args) {
        if (args.length == 1) {
            return new ListRequest(null, 1);
        }
        if (args.length == 2) {
            IdentityType type = tryParseIdentityType(args[1]);
            return type == null
                    ? new ListRequest(null, parsePage(args[1]))
                    : new ListRequest(type, 1);
        }
        if (args.length == 3) {
            return new ListRequest(parseIdentityType(args[1]), parsePage(args[2]));
        }
        throw new CommandInputException(presentation.invalidUsage("list"));
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

    private IdentityType parseIdentityType(String value) {
        IdentityType type = tryParseIdentityType(value);
        if (type == null) {
            throw new CommandInputException(presentation.invalidIdentityType(value));
        }
        return type;
    }

    private static IdentityType tryParseIdentityType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "online" -> IdentityType.ONLINE;
            case "offline" -> IdentityType.OFFLINE;
            default -> null;
        };
    }

    private UUID parseUuid(String value) {
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

    private void send(CommandSender sender, Component component) {
        messageSender.accept(sender, component);
    }

    private void sendAll(CommandSender sender, List<Component> components) {
        components.forEach(component -> messageSender.accept(sender, component));
    }

    private static List<String> matching(String input, List<String> values) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.startsWith(normalized))
                .toList();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left.equalsIgnoreCase(right);
    }

    private enum Mutation {
        ADD,
        REMOVE
    }

    @FunctionalInterface
    public interface WhitelistStateStore {
        void save(boolean enabled) throws Exception;
    }

    private record ListRequest(IdentityType filter, int page) {
    }

    private record MutationOutcome(AccessGrantAddResult addResult, AccessGrantRemoveResult removeResult) {
    }

    private static final class CommandInputException extends IllegalArgumentException {
        private final Component component;

        private CommandInputException(Component component) {
            this.component = Objects.requireNonNull(component, "component");
        }

        private CommandInputException(Component component, Throwable cause) {
            super(cause);
            this.component = Objects.requireNonNull(component, "component");
        }

        private Component component() {
            return component;
        }
    }
}
