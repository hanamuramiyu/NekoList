package hanamuramiyu.monban.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import hanamuramiyu.monban.access.PlayerAccessService;
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidator;
import hanamuramiyu.monban.access.admin.PlayerGroupAdministrationService;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.backend.BackendAdmissionService;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.ScopeRoutingAccessGrantRepository;
import hanamuramiyu.monban.access.grant.WhitelistAccessGrantRepository;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.config.file.FileBackendAccessConfigLoader;
import hanamuramiyu.monban.config.file.FileMonbanConfigLoader;
import hanamuramiyu.monban.config.file.FileSyncSecretLoader;
import hanamuramiyu.monban.config.file.FileServerGroupsConfigLoader;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.identity.OfficialOnlineProfileResolver;
import hanamuramiyu.monban.storage.file.grant.FileScopedAccessGrantRepository;
import hanamuramiyu.monban.storage.file.group.FilePlayerGroupAssignmentRepository;
import hanamuramiyu.monban.storage.file.group.FilePlayerGroupRepository;
import hanamuramiyu.monban.storage.file.permission.FilePlayerPermissionGrantRepository;
import hanamuramiyu.monban.storage.file.whitelist.FileWhitelistRepository;
import hanamuramiyu.monban.velocity.backend.VelocityBackendAccessPolicyValidator;
import hanamuramiyu.monban.velocity.backend.VelocityBackendAdmissionListener;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;
import hanamuramiyu.monban.velocity.command.VelocityAccessCommand;
import hanamuramiyu.monban.velocity.command.VelocityMonbanCommand;
import hanamuramiyu.monban.velocity.command.VelocityLookupCommand;
import hanamuramiyu.monban.velocity.command.VelocityGroupCommand;
import hanamuramiyu.monban.velocity.command.VelocityStatusCommand;
import hanamuramiyu.monban.velocity.command.VelocityNativeWhitelistCommand;
import hanamuramiyu.monban.velocity.command.VelocityWhitelistCommand;
import hanamuramiyu.monban.velocity.grant.VelocityAccessGrantScopeValidator;
import hanamuramiyu.monban.velocity.grant.VelocityScopedAccessGrantValidator;
import hanamuramiyu.monban.velocity.group.VelocityServerGroupCatalogResolver;
import hanamuramiyu.monban.velocity.hybrid.VelocityHybridIdentitySelector;
import hanamuramiyu.monban.velocity.hybrid.VelocityHybridPreLoginListener;
import hanamuramiyu.monban.velocity.permission.VelocityPermissionSetupListener;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import hanamuramiyu.monban.velocity.sync.VelocityStateSynchronizer;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import hanamuramiyu.monban.sync.SyncSecret;

@Plugin(
        id = "monban",
        name = "monban",
        version = MonbanVelocityPluginMetadata.VERSION,
        description = "Minecraft access control for Velocity networks.",
        authors = {"Hanamura Miyu"}
)
public final class MonbanVelocityPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final OfficialOnlineProfileResolver profileResolver = new OfficialOnlineProfileResolver();

    private MonbanConfig config;
    private FileMonbanConfigLoader configLoader;
    private PlayerIdentityResolver identityResolver;
    private WhitelistRepository whitelistRepository;
    private AccessGrantRepository scopedAccessGrantRepository;
    private AccessGrantRepository accessGrantRepository;
    private AccessGrantAdministrationService accessGrantAdministrationService;
    private AccessGrantScopeValidator accessGrantScopeValidator;
    private PlayerGroupRepository playerGroupRepository;
    private PlayerGroupAssignmentRepository playerGroupAssignmentRepository;
    private PlayerPermissionGrantRepository playerPermissionGrantRepository;
    private PlayerAccessResolver playerAccessResolver;
    private ServerGroupCatalog serverGroupCatalog;
    private BackendAccessPolicyCatalog backendAccessPolicyCatalog;
    private BackendAdmissionService backendAdmissionService;
    private PlayerAccessService accessService;
    private WhitelistPolicy whitelistPolicy;
    private VelocityConnectionIdentityRegistry connectionIdentityRegistry;
    private VelocityPermissionSetupListener permissionSetupListener;
    private VelocityStateSynchronizer stateSynchronizer;
    private CommandMeta commandMeta;
    private CommandMeta nativeWhitelistCommandMeta;

    @Inject
    public MonbanVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        VelocityStartupGuard startupGuard = new VelocityStartupGuard();
        server.getEventManager().register(this, startupGuard);

        MonbanConfig loadedConfig;
        int loadedEntries;
        int loadedServerGroups;
        int loadedScopedGrants;
        int loadedBackendPolicies;
        int loadedPlayerGroups;
        int loadedGroupAssignments;
        int loadedPlayerPermissions;

        try {
            Files.createDirectories(dataDirectory);

            FileMonbanConfigLoader loadedConfigLoader = new FileMonbanConfigLoader(
                    dataDirectory.resolve("config.yml"),
                    velocityDefaults()
            );
            loadedConfig = loadedConfigLoader.load();
            if (loadedConfig.deployment().mode() != DeploymentMode.VELOCITY) {
                throw new IllegalStateException(
                        "Standalone deployment is not supported by the monban Velocity build. "
                                + "Set deployment.mode to VELOCITY."
                );
            }
            requireHybridVelocityOnlineMode(
                    loadedConfig,
                    server.getConfiguration().isOnlineMode()
            );

            ServerGroupCatalog configuredServerGroups = new FileServerGroupsConfigLoader(
                    dataDirectory.resolve("server-groups.yml")
            ).load();
            ServerGroupCatalog resolvedServerGroups = new VelocityServerGroupCatalogResolver(server)
                    .resolve(configuredServerGroups);
            BackendAccessPolicyCatalog backendPolicies = new FileBackendAccessConfigLoader(
                    dataDirectory.resolve("backend-access.yml")
            ).load();
            VelocityBackendScopeValidator backendScopeValidator = new VelocityBackendScopeValidator(
                    server,
                    resolvedServerGroups
            );
            new VelocityBackendAccessPolicyValidator(backendScopeValidator).validate(backendPolicies);

            PlayerIdentityResolver resolver = new PlayerIdentityResolver(loadedConfig.identity().mode());
            WhitelistPolicy runtimeWhitelistPolicy = new WhitelistPolicy(loadedConfig.whitelist().enabled());
            WhitelistRepository repository = new FileWhitelistRepository(dataDirectory.resolve("whitelist.yml"));
            AccessGrantRepository scopedRepository = new FileScopedAccessGrantRepository(
                    dataDirectory.resolve("access-grants.yml")
            );
            new VelocityScopedAccessGrantValidator(backendScopeValidator)
                    .validate(scopedRepository.findAll());

            AccessGrantRepository networkRepository = new WhitelistAccessGrantRepository(repository);
            AccessGrantRepository grantRepository = new ScopeRoutingAccessGrantRepository(
                    networkRepository,
                    scopedRepository
            );
            PlayerGroupRepository playerGroupRepository = new FilePlayerGroupRepository(
                    dataDirectory.resolve("player-groups.yml")
            );
            PlayerGroupAssignmentRepository playerGroupAssignmentRepository = new FilePlayerGroupAssignmentRepository(
                    dataDirectory.resolve("group-assignments.yml")
            );
            PlayerPermissionGrantRepository playerPermissionGrantRepository = new FilePlayerPermissionGrantRepository(
                    dataDirectory.resolve("player-permissions.yml")
            );
            PlayerAccessResolver playerAccessResolver = new PlayerAccessResolver(
                    grantRepository,
                    playerGroupRepository,
                    playerGroupAssignmentRepository,
                    playerPermissionGrantRepository
            );
            AccessGrantLookup grantLookup = grantRepository;
            AccessGrantInventory grantInventory = grantRepository;
            PlayerAccessService playerAccessService = new PlayerAccessService(
                    loadedConfig, resolver, grantLookup, runtimeWhitelistPolicy, playerAccessResolver
            );
            BackendAdmissionService backendAdmissionService = new BackendAdmissionService(
                    backendPolicies,
                    grantLookup,
                    playerAccessResolver
            );
            AccessGrantScopeValidator accessGrantScopeValidator = new VelocityAccessGrantScopeValidator(backendScopeValidator);
            AccessGrantAdministrationService administrationService = new AccessGrantAdministrationService(
                    grantRepository,
                    accessGrantScopeValidator
            );
            VelocityConnectionIdentityRegistry identityRegistry = new VelocityConnectionIdentityRegistry();
            loadedEntries = repository.findAll().size();
            loadedServerGroups = resolvedServerGroups.findAll().size();
            loadedScopedGrants = scopedRepository.findAll().size();
            loadedBackendPolicies = backendPolicies.explicitPolicyCount();
            loadedPlayerGroups = playerGroupRepository.findAll().size();
            loadedGroupAssignments = playerGroupAssignmentRepository.findAll().size();
            loadedPlayerPermissions = playerPermissionGrantRepository.findAll().size();

            this.config = loadedConfig;
            this.configLoader = loadedConfigLoader;
            this.identityResolver = resolver;
            this.whitelistRepository = repository;
            this.scopedAccessGrantRepository = scopedRepository;
            this.accessGrantRepository = grantRepository;
            this.accessGrantAdministrationService = administrationService;
            this.accessGrantScopeValidator = accessGrantScopeValidator;
            this.playerGroupRepository = playerGroupRepository;
            this.playerGroupAssignmentRepository = playerGroupAssignmentRepository;
            this.playerPermissionGrantRepository = playerPermissionGrantRepository;
            this.playerAccessResolver = playerAccessResolver;
            this.serverGroupCatalog = resolvedServerGroups;
            this.backendAccessPolicyCatalog = backendPolicies;
            this.backendAdmissionService = backendAdmissionService;
            this.accessService = playerAccessService;
            this.whitelistPolicy = runtimeWhitelistPolicy;
            this.connectionIdentityRegistry = identityRegistry;
            VelocityPermissionSetupListener permissionListener = new VelocityPermissionSetupListener(
                    playerAccessResolver,
                    identityRegistry,
                    resolvedServerGroups
            );
            this.permissionSetupListener = permissionListener;

            FileSyncSecretLoader syncSecretLoader = new FileSyncSecretLoader(dataDirectory.resolve("sync.yml"));
            SyncSecret syncSecret = loadedConfig.backendPermissions().enabled()
                    ? syncSecretLoader.loadOrCreate()
                    : null;
            if (syncSecret != null) {
                VelocityStateSynchronizer synchronizer = new VelocityStateSynchronizer(
                        server,
                        grantRepository,
                        playerGroupRepository,
                        playerGroupAssignmentRepository,
                        playerPermissionGrantRepository,
                        syncSecret,
                        logger,
                        dataDirectory.resolve("state-revision"),
                        runtimeWhitelistPolicy::enabled
                );
                synchronizer.registerChannel();
                server.getEventManager().register(this, synchronizer);
                this.stateSynchronizer = synchronizer;
            }

            if (loadedConfig.identity().hybrid().enabled()) {
                VelocityHybridIdentitySelector hybridSelector = new VelocityHybridIdentitySelector(
                        grantInventory,
                        loadedConfig.identity().hybrid()
                );
                server.getEventManager().register(
                        this,
                        new VelocityHybridPreLoginListener(
                                hybridSelector,
                                loadedConfig.identity().hybrid(),
                                logger
                        )
                );
            }
            server.getEventManager().register(
                    this,
                    new VelocityPlayerLoginListener(playerAccessService, identityRegistry, logger)
            );
            server.getEventManager().register(
                    this,
                    new VelocityBackendAdmissionListener(
                            backendAdmissionService,
                            resolvedServerGroups,
                            identityRegistry,
                            logger
                    )
            );
            server.getEventManager().register(this, permissionListener);
            if (this.stateSynchronizer != null) {
                this.stateSynchronizer.broadcast();
            }
            server.getEventManager().unregisterListener(this, startupGuard);
        } catch (Exception exception) {
            logger.error("Failed to initialize monban on Velocity. Startup access guard remains active.", exception);
            throw new IllegalStateException("Failed to initialize monban on Velocity.", exception);
        }

        logger.info(
                "monban started. Deployment mode: {}, identity mode: {}, hybrid enabled: {}, whitelist enabled: {}, "
                        + "loaded entries: {}, server groups: {}, scoped grants: {}, backend policies: {}.",
                loadedConfig.deployment().mode(),
                loadedConfig.identity().mode(),
                loadedConfig.identity().hybrid().enabled(),
                loadedConfig.whitelist().enabled(),
                loadedEntries,
                loadedServerGroups,
                loadedScopedGrants,
                loadedBackendPolicies
        );
        logger.info(
                "Loaded player groups: {}, group assignments: {}, direct player permissions: {}.",
                loadedPlayerGroups,
                loadedGroupAssignments,
                loadedPlayerPermissions
        );

        try {
            registerManagementCommand();
        } catch (RuntimeException exception) {
            logger.error(
                    "monban access enforcement is ready, but /monban command registration failed. "
                            + "Management commands will be unavailable.",
                    exception
            );
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        WhitelistRepository repository = this.whitelistRepository;
        CommandMeta registeredCommandMeta = this.commandMeta;
        CommandMeta registeredNativeWhitelistCommandMeta = this.nativeWhitelistCommandMeta;
        VelocityPermissionSetupListener registeredPermissionSetupListener = this.permissionSetupListener;
        VelocityStateSynchronizer registeredStateSynchronizer = this.stateSynchronizer;

        this.commandMeta = null;
        this.nativeWhitelistCommandMeta = null;
        this.connectionIdentityRegistry = null;
        this.permissionSetupListener = null;
        this.stateSynchronizer = null;
        this.accessGrantAdministrationService = null;
        this.accessGrantScopeValidator = null;
        this.playerAccessResolver = null;
        this.playerPermissionGrantRepository = null;
        this.playerGroupAssignmentRepository = null;
        this.playerGroupRepository = null;
        this.accessGrantRepository = null;
        this.accessService = null;
        this.whitelistPolicy = null;
        this.backendAdmissionService = null;
        this.backendAccessPolicyCatalog = null;
        this.serverGroupCatalog = null;
        this.scopedAccessGrantRepository = null;
        this.whitelistRepository = null;
        this.identityResolver = null;
        this.config = null;
        this.configLoader = null;

        if (registeredCommandMeta != null) {
            try {
                server.getCommandManager().unregister(registeredCommandMeta);
            } catch (RuntimeException exception) {
                logger.error("Failed to unregister the monban management command cleanly.", exception);
            }
        }
        if (registeredNativeWhitelistCommandMeta != null) {
            try {
                server.getCommandManager().unregister(registeredNativeWhitelistCommandMeta);
            } catch (RuntimeException exception) {
                logger.error("Failed to unregister the Velocity whitelist command guard cleanly.", exception);
            }
        }
        if (registeredPermissionSetupListener != null) {
            try {
                server.getEventManager().unregisterListener(this, registeredPermissionSetupListener);
            } catch (RuntimeException exception) {
                logger.error("Failed to unregister the monban permission provider cleanly.", exception);
            }
        }
        if (registeredStateSynchronizer != null) {
            try {
                server.getEventManager().unregisterListener(this, registeredStateSynchronizer);
                registeredStateSynchronizer.unregisterChannel();
            } catch (RuntimeException exception) {
                logger.error("Failed to unregister the monban state synchronizer cleanly.", exception);
            }
        }

        if (repository instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                logger.error("Failed to close monban whitelist storage cleanly.", exception);
            }
        }
        profileResolver.close();
    }

    private void registerManagementCommand() {
        MonbanConfig loadedConfig = this.config;
        AccessGrantRepository repository = this.accessGrantRepository;
        ServerGroupCatalog resolvedServerGroups = this.serverGroupCatalog;
        BackendAccessPolicyCatalog backendPolicies = this.backendAccessPolicyCatalog;
        AccessGrantAdministrationService administrationService = this.accessGrantAdministrationService;
        AccessGrantScopeValidator scopeValidator = this.accessGrantScopeValidator;
        PlayerGroupRepository playerGroupRepository = this.playerGroupRepository;
        PlayerGroupAssignmentRepository playerGroupAssignmentRepository = this.playerGroupAssignmentRepository;
        PlayerPermissionGrantRepository playerPermissionGrantRepository = this.playerPermissionGrantRepository;
        PlayerAccessResolver playerAccessResolver = this.playerAccessResolver;
        FileMonbanConfigLoader loadedConfigLoader = this.configLoader;
        WhitelistPolicy runtimeWhitelistPolicy = this.whitelistPolicy;
        Runnable stateChanged = this::broadcastState;
        if (loadedConfig == null
                || repository == null
                || resolvedServerGroups == null
                || backendPolicies == null
                || administrationService == null
                || scopeValidator == null
                || playerGroupRepository == null
                || playerGroupAssignmentRepository == null
                || playerPermissionGrantRepository == null
                || playerAccessResolver == null
                || loadedConfigLoader == null
                || runtimeWhitelistPolicy == null) {
            throw new IllegalStateException("monban management services are not initialized.");
        }

        Executor mutationExecutor = task -> server.getScheduler()
                .buildTask(this, task)
                .schedule();
        AccessGrantInventory grantInventory = repository;
        VelocityWhitelistCommand whitelistCommand = new VelocityWhitelistCommand(
                administrationService,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                runtimeWhitelistPolicy,
                enabled -> loadedConfigLoader.save(new MonbanConfig(
                        loadedConfig.deployment(),
                        new WhitelistSettings(enabled),
                        loadedConfig.identity(),
                        loadedConfig.backendPermissions()
                )),
                stateChanged
        );
        VelocityLookupCommand lookupCommand = new VelocityLookupCommand(
                administrationService,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                playerAccessResolver
        );
        VelocityAccessCommand accessCommand = new VelocityAccessCommand(
                administrationService,
                resolvedServerGroups,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                stateChanged
        );
        VelocityStatusCommand statusCommand = new VelocityStatusCommand(
                loadedConfig,
                grantInventory,
                resolvedServerGroups,
                backendPolicies,
                runtimeWhitelistPolicy::enabled,
                server.getConfiguration().isOnlineMode(),
                logger
        );
        PlayerGroupAdministrationService playerGroupAdministrationService = new PlayerGroupAdministrationService(
                playerGroupRepository,
                playerGroupAssignmentRepository,
                playerPermissionGrantRepository,
                scopeValidator
        );
        VelocityGroupCommand groupCommand = new VelocityGroupCommand(
                playerGroupAdministrationService,
                resolvedServerGroups,
                server,
                mutationExecutor,
                logger,
                profileResolver,
                stateChanged
        );
        var command = VelocityMonbanCommand.create(
                whitelistCommand,
                lookupCommand,
                accessCommand,
                statusCommand,
                groupCommand
        );
        CommandMeta meta = server.getCommandManager()
                .metaBuilder("monban")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, command);
        this.commandMeta = meta;
        registerNativeWhitelistCommandGuard();
        logger.info("Registered /monban management command.");
    }

    private void broadcastState() {
        VelocityStateSynchronizer synchronizer = this.stateSynchronizer;
        if (synchronizer != null) {
            synchronizer.broadcast();
        }
    }

    private void registerNativeWhitelistCommandGuard() {
        String[] labels = {"whitelist", "minecraft:whitelist", "bukkit:whitelist"};
        for (String label : labels) {
            if (server.getCommandManager().hasCommand(label)) {
                logger.warn("Cannot intercept /{} because another proxy command already owns that label.", label);
                return;
            }
        }

        CommandMeta meta = server.getCommandManager()
                .metaBuilder("whitelist")
                .aliases("minecraft:whitelist", "bukkit:whitelist")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, new VelocityNativeWhitelistCommand());
        this.nativeWhitelistCommandMeta = meta;
    }


    static void requireHybridVelocityOnlineMode(MonbanConfig config, boolean velocityOnlineMode) {
        if (config.identity().hybrid().enabled() && velocityOnlineMode) {
            throw new IllegalStateException(
                    "Velocity hybrid identity selection currently requires global online-mode=false. "
                            + "Disable Velocity online-mode or set identity.hybrid.enabled=false."
            );
        }
    }

    private static MonbanConfig velocityDefaults() {
        return new MonbanConfig(
                new DeploymentSettings(DeploymentMode.VELOCITY),
                WhitelistSettings.defaults(),
                IdentitySettings.defaults()
        );
    }
}
