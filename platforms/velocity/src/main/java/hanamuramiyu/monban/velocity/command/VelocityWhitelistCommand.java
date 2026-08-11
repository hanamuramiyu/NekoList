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
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class VelocityWhitelistCommand {
    public static final String PERMISSION = "monban.command.whitelist";

    private static final int PAGE_SIZE = 10;
    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private static final Component MUTATION_FAILURE_MESSAGE =
            Component.text("Failed to update the monban whitelist. Check the proxy log.");
    private static final Component READ_FAILURE_MESSAGE =
            Component.text("Failed to read the monban whitelist. Check the proxy log.");

    private static final Comparator<AccessGrant> WHITELIST_ORDER = Comparator
            .comparing((AccessGrant grant) -> grant.identity().type().name())
            .thenComparing(grant -> grant.identity().normalizedName())
            .thenComparing(grant -> grant.identity().verifiedUuid().map(UUID::toString).orElse(""));

    private final AccessGrantAdministrationService administrationService;
    private final ProxyServer server;
    private final Executor mutationExecutor;
    private final Logger logger;

    public VelocityWhitelistCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor mutationExecutor,
            Logger logger
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.server = Objects.requireNonNull(server, "server");
        this.mutationExecutor = Objects.requireNonNull(mutationExecutor, "mutationExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public LiteralArgumentBuilder<CommandSource> build() {
        return BrigadierCommand.literalArgumentBuilder("whitelist")
                .requires(source -> source.hasPermission(PERMISSION))
                .then(mutationBranch("add", Mutation.ADD))
                .then(mutationBranch("remove", Mutation.REMOVE))
                .then(listBranch());
    }

    private LiteralArgumentBuilder<CommandSource> mutationBranch(String literal, Mutation mutation) {
        LiteralArgumentBuilder<CommandSource> branch = BrigadierCommand.literalArgumentBuilder(literal);
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
                        .then(BrigadierCommand.requiredArgumentBuilder("uuid", StringArgumentType.word())
                                .executes(context -> executeMutation(context, mutation, IdentityType.ONLINE)))));
    }

    private LiteralArgumentBuilder<CommandSource> listBranch() {
        LiteralArgumentBuilder<CommandSource> list = BrigadierCommand.literalArgumentBuilder("list")
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

    private RequiredArgumentBuilder<CommandSource, Integer> pageArgument(IdentityType filter) {
        return BrigadierCommand.requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                .executes(context -> executeList(
                        context,
                        filter,
                        IntegerArgumentType.getInteger(context, "page")
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
            context.getSource().sendMessage(Component.text(exception.getMessage()));
            return 0;
        }

        try {
            mutationExecutor.execute(() -> runMutation(context.getSource(), mutation, identity));
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban whitelist mutation.", exception);
            context.getSource().sendMessage(MUTATION_FAILURE_MESSAGE);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSource> context, IdentityType filter, int page) {
        try {
            List<AccessGrant> grants = administrationService.findAll(NETWORK_SCOPE);
            if (filter != null) {
                grants = grants.stream()
                        .filter(grant -> grant.identity().type() == filter)
                        .toList();
            }
            return sendListing(context.getSource(), filter, grants, page)
                    ? Command.SINGLE_SUCCESS
                    : 0;
        } catch (AccessGrantScopeValidationException exception) {
            context.getSource().sendMessage(Component.text(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            logger.error("Failed to read the monban whitelist.", exception);
            context.getSource().sendMessage(READ_FAILURE_MESSAGE);
            return 0;
        }
    }

    private boolean sendListing(
            CommandSource source,
            IdentityType filter,
            List<AccessGrant> grants,
            int page
    ) {
        List<AccessGrant> sorted = new ArrayList<>(grants);
        sorted.sort(WHITELIST_ORDER);

        if (sorted.isEmpty()) {
            source.sendMessage(Component.text(
                    filter == null
                            ? "No whitelist entries found."
                            : "No " + filter + " whitelist entries found."
            ));
            return true;
        }

        int total = sorted.size();
        int totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > totalPages) {
            source.sendMessage(Component.text(
                    "Page " + page + " is out of range. Available pages: 1-" + totalPages + "."
            ));
            return false;
        }

        String header = filter == null
                ? "Whitelist — page " + page + "/" + totalPages
                : "Whitelist (" + filter + ") — page " + page + "/" + totalPages;
        source.sendMessage(Component.text(header));

        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        for (int index = fromIndex; index < toIndex; index++) {
            source.sendMessage(Component.text(formatIdentity(sorted.get(index).identity())));
        }
        source.sendMessage(Component.text(
                (toIndex - fromIndex) + " entries shown — " + total + " total"
        ));
        return true;
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

    private void runMutation(CommandSource source, Mutation mutation, PlayerIdentity identity) {
        try {
            switch (mutation) {
                case ADD -> sendAddResult(source, identity);
                case REMOVE -> sendRemoveResult(source, identity);
            }
        } catch (AccessGrantScopeValidationException exception) {
            source.sendMessage(Component.text(exception.getMessage()));
        } catch (RuntimeException exception) {
            logger.error("Failed to update the monban whitelist for {}.", identity, exception);
            source.sendMessage(MUTATION_FAILURE_MESSAGE);
        }
    }

    private void sendAddResult(CommandSource source, PlayerIdentity identity) {
        AccessGrantAddResult result = administrationService.grant(new AccessGrant(NETWORK_SCOPE, identity));
        switch (result) {
            case ADDED -> source.sendMessage(Component.text(
                    "Added " + formatIdentity(identity) + " to the monban whitelist."
            ));
            case ALREADY_EXISTS -> source.sendMessage(Component.text("Whitelist entry already exists."));
        }
    }

    private void sendRemoveResult(CommandSource source, PlayerIdentity identity) {
        AccessGrantRemoveResult result = administrationService.revoke(NETWORK_SCOPE, identity);
        switch (result) {
            case REMOVED -> source.sendMessage(Component.text(
                    "Removed " + formatIdentity(identity) + " from the monban whitelist."
            ));
            case NOT_FOUND -> source.sendMessage(Component.text("No matching whitelist entry exists."));
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

    private static String formatIdentity(PlayerIdentity identity) {
        return switch (identity.type()) {
            case OFFLINE -> "OFFLINE " + identity.name();
            case ONLINE -> "ONLINE " + identity.name() + " " + identity.verifiedUuid().orElseThrow();
        };
    }

    private enum Mutation {
        ADD,
        REMOVE
    }

    private static final class CommandInputException extends IllegalArgumentException {
        private CommandInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
