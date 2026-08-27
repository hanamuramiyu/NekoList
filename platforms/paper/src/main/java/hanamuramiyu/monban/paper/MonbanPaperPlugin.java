package hanamuramiyu.monban.paper;

import hanamuramiyu.monban.access.PlayerAccessService;
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.backend.BackendPermissionConfiguration;
import hanamuramiyu.monban.access.backend.BackendPermissionService;
import hanamuramiyu.monban.config.BackendPermissionSettings;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.WhitelistAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.config.file.FileMonbanConfigLoader;
import hanamuramiyu.monban.config.file.FileSyncSecretLoader;
import hanamuramiyu.monban.config.file.FileServerGroupsConfigLoader;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.identity.OfficialOnlineProfileResolver;
import hanamuramiyu.monban.paper.command.PaperMonbanCommand;
import hanamuramiyu.monban.paper.command.PaperWhitelistCommand;
import hanamuramiyu.monban.paper.whitelist.PaperNativeWhitelistGuard;
import hanamuramiyu.monban.storage.file.whitelist.FileWhitelistRepository;
import hanamuramiyu.monban.storage.file.group.FilePlayerGroupAssignmentRepository;
import hanamuramiyu.monban.storage.file.group.FilePlayerGroupRepository;
import hanamuramiyu.monban.storage.file.permission.FilePlayerPermissionGrantRepository;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import hanamuramiyu.monban.paper.permission.PaperPermissionAttachmentListener;
import hanamuramiyu.monban.paper.permission.PaperPermissionAttachmentManager;
import hanamuramiyu.monban.paper.sync.PaperStateSyncListener;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;
import hanamuramiyu.monban.sync.PlayerAccessStateCodec;
import hanamuramiyu.monban.sync.SyncSecret;
import hanamuramiyu.monban.sync.SyncChannel;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;

public final class MonbanPaperPlugin extends JavaPlugin {
    private MonbanConfig config;
    private FileMonbanConfigLoader configLoader;
    private PlayerIdentityResolver identityResolver;
    private WhitelistRepository whitelistRepository;
    private AccessGrantAdministrationService accessGrantAdministrationService;
    private PlayerAccessService playerAccessService;
    private WhitelistPolicy whitelistPolicy;
    private OfficialOnlineProfileResolver profileResolver;
    private PaperPermissionAttachmentManager permissionAttachmentManager;
    private PaperStateSyncListener stateSyncListener;
    private boolean backendPermissionsEnabled;

    @Override
    public void onEnable() {
        try {
            PaperNativeWhitelistGuard.requireDisabledAtStartup(getServer());
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Failed to initialize monban. The native Minecraft whitelist remains enabled; disabling monban.",
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PaperStartupAccessGuard startupGuard = new PaperStartupAccessGuard();
        getServer().getPluginManager().registerEvents(startupGuard, this);

        MonbanConfig loadedConfig;
        AccessGrantRepository networkRepository;
        WhitelistRepository repository;
        int loadedEntries;

        try {
            Path dataDirectory = getDataFolder().toPath();
            Files.createDirectories(dataDirectory);

            FileMonbanConfigLoader loadedConfigLoader = new FileMonbanConfigLoader(dataDirectory.resolve("config.yml"));
            loadedConfig = loadedConfigLoader.load();
            requireSupportedDeployment(loadedConfig);

            PlayerIdentityResolver resolver = new PlayerIdentityResolver(loadedConfig.identity().mode());
            OfficialOnlineProfileResolver onlineProfileResolver = new OfficialOnlineProfileResolver();
            WhitelistPolicy runtimeWhitelistPolicy = new WhitelistPolicy(loadedConfig.whitelist().enabled());
            repository = new FileWhitelistRepository(dataDirectory.resolve("whitelist.yml"));
            networkRepository = new WhitelistAccessGrantRepository(repository);
            AccessGrantLookup grantLookup = networkRepository;
            PlayerAccessResolver accessResolver = loadPermissionResolver(dataDirectory, networkRepository);
            BackendPermissionSettings backendSettings = loadedConfig.backendPermissions();
            boolean backendPermissionsEnabled = backendSettings.enabled();
            this.backendPermissionsEnabled = backendPermissionsEnabled;
            var backendServerName = backendSettings.serverName();
            if (backendPermissionsEnabled && !PaperVelocityConnectionDetector.isVelocityEnabled(getServer())) {
                throw new IllegalStateException(
                        "backend-permissions.enabled requires Paper Velocity forwarding to be enabled."
                );
            }
            Path syncFile = dataDirectory.resolve("sync.yml");
            SyncSecret syncSecret = backendPermissionsEnabled
                    ? new FileSyncSecretLoader(syncFile).load().orElse(null)
                    : null;
            if (backendPermissionsEnabled && syncSecret == null) {
                throw new IllegalStateException(
                        "backend-permissions.enabled requires sync.yml at " + syncFile.toAbsolutePath()
                );
            }
            BackendPermissionConfiguration.requireSynchronization(
                    backendPermissionsEnabled,
                    backendServerName,
                    syncSecret
            );
            PlayerAccessStateReceiver stateReceiver = syncSecret == null
                    ? null
                    : new PlayerAccessStateReceiver(new PlayerAccessStateCodec(), syncSecret);
            PlayerAccessService accessService = new PlayerAccessService(
                    loadedConfig,
                    resolver,
                    grantLookup,
                    runtimeWhitelistPolicy,
                    accessResolver,
                    stateReceiver
            );
            loadedEntries = repository.findAll().size();

            this.config = loadedConfig;
            this.configLoader = loadedConfigLoader;
            this.identityResolver = resolver;
            this.whitelistRepository = repository;
            this.playerAccessService = accessService;
            this.profileResolver = onlineProfileResolver;
            this.whitelistPolicy = runtimeWhitelistPolicy;

            if (backendServerName.isPresent()) {
                String serverName = backendServerName.orElseThrow();
                try {
                    boolean platformAuthenticated = backendPermissionsEnabled
                            ? PaperVelocityConnectionDetector.isVelocityOnlineMode(getServer())
                            : getServer().getOnlineMode();
                    PaperPermissionAttachmentManager manager = new PaperPermissionAttachmentManager(
                            this,
                            new BackendPermissionService(
                                    accessResolver,
                                    new FileServerGroupsConfigLoader(dataDirectory.resolve("server-groups.yml")).load(),
                                    serverName,
                                    stateReceiver
                            )
                    );
                    this.permissionAttachmentManager = manager;
                    getServer().getPluginManager().registerEvents(
                            new PaperPermissionAttachmentListener(
                                    resolver,
                                    platformAuthenticated,
                                    manager
                            ),
                            this
                    );
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to initialize backend permissions.", exception);
                }
            }

            if (stateReceiver != null) {
                PaperStateSyncListener listener = new PaperStateSyncListener(
                        stateReceiver,
                        permissionAttachmentManager == null
                                ? () -> {
                                }
                                : permissionAttachmentManager::refreshAll
                );
                getServer().getMessenger().registerIncomingPluginChannel(this, SyncChannel.ID, listener);
                this.stateSyncListener = listener;
            }

            if (!backendPermissionsEnabled) {
                getServer().getPluginManager().registerEvents(new PaperPlayerLoginListener(this, accessService), this);
            }
            getServer().getPluginManager().registerEvents(
                    new PaperNativeWhitelistGuard(backendPermissionsEnabled),
                    this
            );

            AsyncPlayerPreLoginEvent.getHandlerList().unregister(startupGuard);
        } catch (Exception exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Failed to initialize monban access enforcement. Startup access guard remains active.",
                    exception
            );
            return;
        }

        getLogger().info(
                "monban started. Deployment mode: " + loadedConfig.deployment().mode()
                        + ", identity mode: " + loadedConfig.identity().mode()
                        + ", whitelist enabled: " + loadedConfig.whitelist().enabled()
                        + ", loaded entries: " + loadedEntries + "."
        );

        try {
            AccessGrantAdministrationService administrationService = new AccessGrantAdministrationService(
                    networkRepository,
                    MonbanPaperPlugin::validateNetworkAdministrativeScope
            );
            if (!this.backendPermissionsEnabled) {
                registerManagementCommand(administrationService);
            }
            this.accessGrantAdministrationService = administrationService;
        } catch (RuntimeException exception) {
            logManagementRegistrationFailure(exception);
        }
    }

    @Override
    public void onDisable() {
        WhitelistRepository repository = this.whitelistRepository;

        this.playerAccessService = null;
        this.backendPermissionsEnabled = false;
        PaperPermissionAttachmentManager attachmentManager = this.permissionAttachmentManager;
        this.permissionAttachmentManager = null;
        if (attachmentManager != null) {
            attachmentManager.clearAll();
        }
        PaperStateSyncListener stateListener = this.stateSyncListener;
        this.stateSyncListener = null;
        if (stateListener != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, SyncChannel.ID, stateListener);
        }
        this.whitelistPolicy = null;
        this.accessGrantAdministrationService = null;
        this.whitelistRepository = null;
        this.identityResolver = null;
        this.config = null;
        this.configLoader = null;
        OfficialOnlineProfileResolver onlineProfileResolver = this.profileResolver;
        this.profileResolver = null;
        if (onlineProfileResolver != null) {
            onlineProfileResolver.close();
        }

        if (repository instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Failed to close monban whitelist storage cleanly.", exception);
            }
        }
    }

    private void registerManagementCommand(AccessGrantAdministrationService administrationService) {
        Executor mutationExecutor = task -> getServer().getAsyncScheduler()
                .runNow(this, ignored -> task.run());
        Function<CommandSender, Executor> callbackExecutor = sender -> task -> {
            if (sender instanceof Player player) {
                player.getScheduler().execute(this, task, null, 1);
            } else {
                getServer().getGlobalRegionScheduler().execute(this, task);
            }
        };
        MonbanConfig loadedConfig = this.config;
        FileMonbanConfigLoader loadedConfigLoader = this.configLoader;
        WhitelistPolicy runtimeWhitelistPolicy = this.whitelistPolicy;
        if (loadedConfig == null || loadedConfigLoader == null || runtimeWhitelistPolicy == null) {
            throw new IllegalStateException("monban whitelist policy services are not initialized.");
        }
        PaperWhitelistCommand whitelistCommand = new PaperWhitelistCommand(
                administrationService,
                mutationExecutor,
                callbackExecutor,
                (sender, component) -> sender.sendMessage(component),
                getLogger(),
                profileResolver,
                runtimeWhitelistPolicy,
                enabled -> loadedConfigLoader.save(new MonbanConfig(
                        loadedConfig.deployment(),
                        new WhitelistSettings(enabled),
                        loadedConfig.identity(),
                        loadedConfig.backendPermissions()
                ))
        );
        getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    try {
                        event.registrar().register(
                                PaperMonbanCommand.create(whitelistCommand),
                                "monban administration"
                        );
                    } catch (RuntimeException exception) {
                        logManagementRegistrationFailure(exception);
                    }
                }
        );
    }

    private void logManagementRegistrationFailure(RuntimeException exception) {
        getLogger().log(
                Level.SEVERE,
                "monban access enforcement is ready, but /monban command registration failed. "
                        + "Management commands will be unavailable.",
                exception
        );
    }

    private static void validateNetworkAdministrativeScope(AccessScope scope) {
        if (!AccessScope.network().equals(scope)) {
            throw new AccessGrantScopeValidationException(
                    "Standalone whitelist administration supports only NETWORK access scope."
            );
        }
    }

    private static void requireSupportedDeployment(MonbanConfig config) {
        if (config.deployment().mode() == DeploymentMode.VELOCITY) {
            throw new IllegalStateException(
                    "Velocity deployment is not supported by the Paper/Folia plugin. "
                            + "Install monban-velocity on the proxy and use /monban there; "
                            + "do not use the backend plugin as the network authority."
            );
        }
    }

    private static PlayerAccessResolver loadPermissionResolver(
            Path dataDirectory,
            AccessGrantInventory networkRepository
    ) throws Exception {
        PlayerGroupRepository groups = new FilePlayerGroupRepository(dataDirectory.resolve("player-groups.yml"));
        PlayerGroupAssignmentRepository assignments = new FilePlayerGroupAssignmentRepository(
                dataDirectory.resolve("group-assignments.yml")
        );
        FilePlayerPermissionGrantRepository permissions = new FilePlayerPermissionGrantRepository(
                dataDirectory.resolve("player-permissions.yml")
        );
        return new PlayerAccessResolver(networkRepository, groups, assignments, permissions);
    }
}
