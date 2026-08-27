package hanamuramiyu.monban.velocity.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.effective.PlayerAccessSnapshot;
import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.OnlineProfile;
import hanamuramiyu.monban.identity.OnlineProfileResolutionException;
import hanamuramiyu.monban.identity.OnlineProfileResolver;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.presentation.LookupPresentation;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class VelocityLookupCommand {
    public static final String PERMISSION = "monban.command.lookup";

    private static final AccessScope NETWORK_SCOPE = AccessScope.network();

    private final AccessGrantAdministrationService administrationService;
    private final ProxyServer server;
    private final Executor lookupExecutor;
    private final Logger logger;
    private final OnlineProfileResolver profileResolver;
    private final PlayerAccessResolver accessResolver;
    private final LookupPresentation presentation = new LookupPresentation();

    public VelocityLookupCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor lookupExecutor,
            Logger logger
    ) {
        this(
                administrationService,
                server,
                lookupExecutor,
                logger,
                OnlineProfileResolver.unavailable(),
                null
        );
    }

    public VelocityLookupCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor lookupExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver
    ) {
        this(administrationService, server, lookupExecutor, logger, profileResolver, null);
    }

    public VelocityLookupCommand(
            AccessGrantAdministrationService administrationService,
            ProxyServer server,
            Executor lookupExecutor,
            Logger logger,
            OnlineProfileResolver profileResolver,
            PlayerAccessResolver accessResolver
    ) {
        this.administrationService = Objects.requireNonNull(administrationService, "administrationService");
        this.server = Objects.requireNonNull(server, "server");
        this.lookupExecutor = Objects.requireNonNull(lookupExecutor, "lookupExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver");
        this.accessResolver = accessResolver;
    }

    public LiteralArgumentBuilder<CommandSource> build() {
        return BrigadierCommand.literalArgumentBuilder("lookup")
                .requires(source -> source.hasPermission(PERMISSION))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemainingLowerCase();
                            server.getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::execute));
    }

    private int execute(CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "player");
        PlayerIdentity offlineIdentity;
        try {
            offlineIdentity = PlayerIdentity.offline(name);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendMessage(presentation.invalidPlayerName(name));
            return 0;
        }

        try {
            lookupExecutor.execute(() -> runLookup(context.getSource(), offlineIdentity));
        } catch (RuntimeException exception) {
            logger.error("Failed to schedule monban player lookup.", exception);
            context.getSource().sendMessage(presentation.lookupFailure());
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private void runLookup(CommandSource source, PlayerIdentity offlineIdentity) {
        Optional<PlayerIdentity> onlineIdentity = resolveOnlineIdentity(source, offlineIdentity.name());
        if (onlineIdentity == null) {
            return;
        }

        List<AccessGrant> grants;
        try {
            grants = administrationService.findAll(NETWORK_SCOPE);
        } catch (AccessGrantScopeValidationException exception) {
            source.sendMessage(presentation.lookupFailure());
            return;
        } catch (RuntimeException exception) {
            logger.error("Failed to read the monban whitelist for player lookup.", exception);
            source.sendMessage(presentation.readFailure());
            return;
        }

        PlayerAccessSnapshot snapshot = null;
        if (accessResolver != null) {
            try {
                PlayerIdentity effectiveIdentity = onlineIdentity.orElse(offlineIdentity);
                snapshot = accessResolver.resolve(effectiveIdentity);
            } catch (RuntimeException exception) {
                logger.error("Failed to resolve effective access for player {}.", offlineIdentity.name(), exception);
                source.sendMessage(presentation.readFailure());
                return;
            }
        }
        presentation.result(offlineIdentity, onlineIdentity, grants, snapshot)
                .forEach(source::sendMessage);
    }

    private Optional<PlayerIdentity> resolveOnlineIdentity(CommandSource source, String name) {
        try {
            OnlineProfile profile = profileResolver.resolve(name).toCompletableFuture().join();
            return Optional.of(profile.identity());
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause() instanceof OnlineProfileResolutionException resolved
                    ? resolved
                    : exception;
            if (cause instanceof OnlineProfileResolutionException resolved
                    && resolved.kind() == OnlineProfileResolutionException.Kind.NOT_FOUND) {
                return Optional.empty();
            }
            if (cause instanceof OnlineProfileResolutionException) {
                source.sendMessage(presentation.onlineProfileUnavailable());
                return Optional.empty();
            }
            logger.error("Failed to resolve online profile for {}.", name, exception);
            source.sendMessage(presentation.lookupFailure());
            return null;
        }
    }
}
