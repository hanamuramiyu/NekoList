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
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.backend.BackendAccessPolicyCatalog;
import hanamuramiyu.monban.access.backend.BackendAdmissionService;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.ScopeRoutingAccessGrantRepository;
import hanamuramiyu.monban.access.grant.WhitelistAccessGrantRepository;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.config.DeploymentSettings;
import hanamuramiyu.monban.config.IdentitySettings;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.config.file.FileBackendAccessConfigLoader;
import hanamuramiyu.monban.config.file.FileMonbanConfigLoader;
import hanamuramiyu.monban.config.file.FileServerGroupsConfigLoader;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.storage.file.grant.FileScopedAccessGrantRepository;
import hanamuramiyu.monban.storage.file.whitelist.FileWhitelistRepository;
import hanamuramiyu.monban.velocity.backend.VelocityBackendAccessPolicyValidator;
import hanamuramiyu.monban.velocity.backend.VelocityBackendAdmissionListener;
import hanamuramiyu.monban.velocity.backend.VelocityBackendScopeValidator;
import hanamuramiyu.monban.velocity.command.VelocityAccessCommand;
import hanamuramiyu.monban.velocity.command.VelocityWhitelistCommand;
import hanamuramiyu.monban.velocity.command.VelocityMonbanCommand;
import hanamuramiyu.monban.velocity.command.VelocityStatusCommand;
import hanamuramiyu.monban.velocity.grant.VelocityAccessGrantScopeValidator;
import hanamuramiyu.monban.velocity.grant.VelocityScopedAccessGrantValidator;
import hanamuramiyu.monban.velocity.group.VelocityServerGroupCatalogResolver;
import hanamuramiyu.monban.velocity.hybrid.VelocityHybridIdentitySelector;
import hanamuramiyu.monban.velocity.hybrid.VelocityHybridPreLoginListener;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

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

    private MonbanConfig config;
    private PlayerIdentityResolver identityResolver;
    private WhitelistRepository whitelistRepository;
    private AccessGrantRepository scopedAccessGrantRepository;
    private AccessGrantRepository accessGrantRepository;
    private AccessGrantAdministrationService accessGrantAdministrationService;
    private ServerGroupCatalog serverGroupCatalog;
    private BackendAccessPolicyCatalog backendAccessPolicyCatalog;
    private BackendAdmissionService backendAdmissionService;
    private PlayerAccessService accessService;
    private VelocityConnectionIdentityRegistry connectionIdentityRegistry;
    private CommandMeta commandMeta;

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

        try {
            Files.createDirectories(dataDirectory);

            loadedConfig = new FileMonbanConfigLoader(
                    dataDirectory.resolve("config.yml"),
                    velocityDefaults()
            ).load();
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
            AccessGrantLookup grantLookup = grantRepository;
            AccessGrantInventory grantInventory = grantRepository;
            PlayerAccessService playerAccessService = new PlayerAccessService(loadedConfig, resolver, grantLookup);
            BackendAdmissionService backendAdmissionService = new BackendAdmissionService(backendPolicies, grantLookup);
            AccessGrantAdministrationService administrationService = new AccessGrantAdministrationService(
                    grantRepository,
                    new VelocityAccessGrantScopeValidator(backendScopeValidator)
            );
            VelocityConnectionIdentityRegistry identityRegistry = new VelocityConnectionIdentityRegistry();
            loadedEntries = repository.findAll().size();
            loadedServerGroups = resolvedServerGroups.findAll().size();
            loadedScopedGrants = scopedRepository.findAll().size();
            loadedBackendPolicies = backendPolicies.explicitPolicyCount();

            this.config = loadedConfig;
            this.identityResolver = resolver;
            this.whitelistRepository = repository;
            this.scopedAccessGrantRepository = scopedRepository;
            this.accessGrantRepository = grantRepository;
            this.accessGrantAdministrationService = administrationService;
            this.serverGroupCatalog = resolvedServerGroups;
            this.backendAccessPolicyCatalog = backendPolicies;
            this.backendAdmissionService = backendAdmissionService;
            this.accessService = playerAccessService;
            this.connectionIdentityRegistry = identityRegistry;

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

        this.commandMeta = null;
        this.connectionIdentityRegistry = null;
        this.accessGrantAdministrationService = null;
        this.accessGrantRepository = null;
        this.accessService = null;
        this.backendAdmissionService = null;
        this.backendAccessPolicyCatalog = null;
        this.serverGroupCatalog = null;
        this.scopedAccessGrantRepository = null;
        this.whitelistRepository = null;
        this.identityResolver = null;
        this.config = null;

        if (registeredCommandMeta != null) {
            try {
                server.getCommandManager().unregister(registeredCommandMeta);
            } catch (RuntimeException exception) {
                logger.error("Failed to unregister the monban management command cleanly.", exception);
            }
        }

        if (repository instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                logger.error("Failed to close monban whitelist storage cleanly.", exception);
            }
        }
    }

    private void registerManagementCommand() {
        MonbanConfig loadedConfig = this.config;
        AccessGrantRepository repository = this.accessGrantRepository;
        ServerGroupCatalog resolvedServerGroups = this.serverGroupCatalog;
        BackendAccessPolicyCatalog backendPolicies = this.backendAccessPolicyCatalog;
        AccessGrantAdministrationService administrationService = this.accessGrantAdministrationService;
        if (loadedConfig == null
                || repository == null
                || resolvedServerGroups == null
                || backendPolicies == null
                || administrationService == null) {
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
                logger
        );
        VelocityAccessCommand accessCommand = new VelocityAccessCommand(
                administrationService,
                resolvedServerGroups,
                server,
                mutationExecutor,
                logger
        );
        VelocityStatusCommand statusCommand = new VelocityStatusCommand(
                loadedConfig,
                grantInventory,
                resolvedServerGroups,
                backendPolicies,
                server.getConfiguration().isOnlineMode(),
                logger
        );
        var command = VelocityMonbanCommand.create(whitelistCommand, accessCommand, statusCommand);
        CommandMeta meta = server.getCommandManager()
                .metaBuilder("monban")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, command);
        this.commandMeta = meta;
        logger.info("Registered /monban management command.");
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
