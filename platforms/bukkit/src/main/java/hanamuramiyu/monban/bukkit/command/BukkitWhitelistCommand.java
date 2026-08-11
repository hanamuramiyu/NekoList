package hanamuramiyu.monban.bukkit.command;

import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitWhitelistCommand {
    public static final String PERMISSION = "monban.command.whitelist";

    private static final int PAGE_SIZE = 10;
    private static final AccessScope NETWORK_SCOPE = AccessScope.network();
    private static final String MUTATION_FAILURE_MESSAGE =
            "Failed to update the monban whitelist. Check the server log.";
    private static final String READ_FAILURE_MESSAGE =
            "Failed to read the monban whitelist. Check the server log.";

    private static final Comparator<AccessGrant> WHITELIST_ORDER = Comparator
            .comparing((AccessGrant grant) -> grant.identity().type().name())
            .thenComparing(grant -> grant.identity().normalizedName())
            .thenComparing(grant -> grant.identity().verifiedUuid().map(UUID::toString).orElse(""));

    private final AccessGrantAdministrationService administrationService;
    private final Executor mutationExecutor;
    private final Function<CommandSender, Executor> callbackExecutor;
    private final Logger logger;

    public BukkitWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            Executor mutationExecutor,
            Function<CommandSender, Executor> callbackExecutor,
            Logger logger
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.mutationExecutor = Objects.requireNonNull(mutationExecutor, "mutationExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean execute(CommandSender sender, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(args, "args");

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("You do not have permission to use /monban whitelist.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> executeMutation(sender, args, Mutation.ADD);
            case "remove" -> executeMutation(sender, args, Mutation.REMOVE);
            case "list" -> executeList(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    public List<String> suggestions(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(args[0], List.of("add", "remove", "list"));
        }
        if ((equalsIgnoreCase(args[0], "add") || equalsIgnoreCase(args[0], "remove")) && args.length == 2) {
            return matching(args[1], List.of("offline", "online"));
        }
        if (equalsIgnoreCase(args[0], "list") && args.length == 2) {
            return matching(args[1], List.of("offline", "online"));
        }
        return List.of();
    }

    private boolean executeMutation(CommandSender sender, String[] args, Mutation mutation) {
        PlayerIdentity identity;
        try {
            identity = parseMutationIdentity(args);
        } catch (CommandInputException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }

        try {
            mutationExecutor.execute(() -> runMutation(sender, mutation, identity));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist mutation.", exception);
            sender.sendMessage(MUTATION_FAILURE_MESSAGE);
        }
        return true;
    }

    private boolean executeList(CommandSender sender, String[] args) {
        ListRequest request;
        try {
            request = parseListRequest(args);
        } catch (CommandInputException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }

        try {
            List<AccessGrant> grants = administrationService.findAll(NETWORK_SCOPE);
            if (request.filter() != null) {
                grants = grants.stream()
                        .filter(grant -> grant.identity().type() == request.filter())
                        .toList();
            }
            sendListing(sender, request.filter(), grants, request.page());
        } catch (AccessGrantScopeValidationException exception) {
            sender.sendMessage(exception.getMessage());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to read the monban whitelist.", exception);
            sender.sendMessage(READ_FAILURE_MESSAGE);
        }
        return true;
    }

    private PlayerIdentity parseMutationIdentity(String[] args) {
        if (args.length < 3) {
            throw new CommandInputException("Usage: /monban whitelist " + args[0]
                    + " offline <name> | online <name> <uuid>");
        }

        IdentityType type = parseIdentityType(args[1]);
        String name = args[2];
        try {
            return switch (type) {
                case OFFLINE -> {
                    if (args.length != 3) {
                        throw new CommandInputException(
                                "Usage: /monban whitelist " + args[0] + " offline <name>"
                        );
                    }
                    yield PlayerIdentity.offline(name);
                }
                case ONLINE -> {
                    if (args.length != 4) {
                        throw new CommandInputException(
                                "Usage: /monban whitelist " + args[0] + " online <name> <uuid>"
                        );
                    }
                    yield PlayerIdentity.online(name, parseUuid(args[3]));
                }
            };
        } catch (CommandInputException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException("Invalid Minecraft player name: " + name + ".", exception);
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
        throw new CommandInputException(
                "Usage: /monban whitelist list [page] | list <offline|online> [page]"
        );
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
            dispatchCallback(sender, () -> sender.sendMessage(exception.getMessage()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to update the monban whitelist for " + identity + ".", exception);
            dispatchCallback(sender, () -> sender.sendMessage(MUTATION_FAILURE_MESSAGE));
        }
    }

    private void sendMutationOutcome(
            CommandSender sender,
            Mutation mutation,
            PlayerIdentity identity,
            MutationOutcome outcome
    ) {
        switch (mutation) {
            case ADD -> {
                if (outcome.addResult() == AccessGrantAddResult.ADDED) {
                    sender.sendMessage("Added " + formatIdentity(identity) + " to the monban whitelist.");
                } else {
                    sender.sendMessage("Whitelist entry already exists.");
                }
            }
            case REMOVE -> {
                if (outcome.removeResult() == AccessGrantRemoveResult.REMOVED) {
                    sender.sendMessage("Removed " + formatIdentity(identity) + " from the monban whitelist.");
                } else {
                    sender.sendMessage("No matching whitelist entry exists.");
                }
            }
        }
    }

    private void dispatchCallback(CommandSender sender, Runnable callback) {
        try {
            callbackExecutor.apply(sender).execute(callback);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to schedule monban whitelist command response.", exception);
        }
    }

    private void sendListing(CommandSender sender, IdentityType filter, List<AccessGrant> grants, int page) {
        List<AccessGrant> sorted = new ArrayList<>(grants);
        sorted.sort(WHITELIST_ORDER);

        if (sorted.isEmpty()) {
            sender.sendMessage(filter == null
                    ? "No whitelist entries found."
                    : "No " + filter + " whitelist entries found.");
            return;
        }

        int total = sorted.size();
        int totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > totalPages) {
            sender.sendMessage("Page " + page + " is out of range. Available pages: 1-" + totalPages + ".");
            return;
        }

        sender.sendMessage(filter == null
                ? "Whitelist — page " + page + "/" + totalPages
                : "Whitelist (" + filter + ") — page " + page + "/" + totalPages);

        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        for (int index = fromIndex; index < toIndex; index++) {
            sender.sendMessage(formatIdentity(sorted.get(index).identity()));
        }
        sender.sendMessage((toIndex - fromIndex) + " entries shown — " + total + " total");
    }

    private static IdentityType parseIdentityType(String value) {
        IdentityType type = tryParseIdentityType(value);
        if (type == null) {
            throw new CommandInputException("Identity type must be ONLINE or OFFLINE: " + value + ".");
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

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new CommandInputException("Invalid UUID: " + value + ".", exception);
        }
    }

    private static int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            if (page < 1) {
                throw new NumberFormatException("page must be positive");
            }
            return page;
        } catch (NumberFormatException exception) {
            throw new CommandInputException("Invalid page: " + value + ". Page must be at least 1.", exception);
        }
    }

    private static String formatIdentity(PlayerIdentity identity) {
        return switch (identity.type()) {
            case OFFLINE -> "OFFLINE " + identity.name();
            case ONLINE -> "ONLINE " + identity.name() + " " + identity.verifiedUuid().orElseThrow();
        };
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

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage:");
        sender.sendMessage("/monban whitelist add offline <name>");
        sender.sendMessage("/monban whitelist add online <name> <uuid>");
        sender.sendMessage("/monban whitelist remove offline <name>");
        sender.sendMessage("/monban whitelist remove online <name> <uuid>");
        sender.sendMessage("/monban whitelist list [page]");
        sender.sendMessage("/monban whitelist list <offline|online> [page]");
    }

    private enum Mutation {
        ADD,
        REMOVE
    }

    private record ListRequest(IdentityType filter, int page) {
    }

    private record MutationOutcome(AccessGrantAddResult addResult, AccessGrantRemoveResult removeResult) {
    }

    private static final class CommandInputException extends IllegalArgumentException {
        private CommandInputException(String message) {
            super(message);
        }

        private CommandInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
