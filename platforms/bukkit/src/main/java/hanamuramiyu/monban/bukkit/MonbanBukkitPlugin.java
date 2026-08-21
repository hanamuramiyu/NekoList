package hanamuramiyu.monban.bukkit;

import hanamuramiyu.monban.access.PlayerAccessService;
import hanamuramiyu.monban.access.WhitelistPolicy;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.admin.AccessGrantScopeValidationException;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.grant.WhitelistAccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.bukkit.command.BukkitMonbanCommand;
import hanamuramiyu.monban.bukkit.command.BukkitWhitelistCommand;
import hanamuramiyu.monban.bukkit.presentation.BukkitAdventureSender;
import hanamuramiyu.monban.bukkit.whitelist.BukkitNativeWhitelistGuard;
import hanamuramiyu.monban.config.MonbanConfig;
import hanamuramiyu.monban.config.WhitelistSettings;
import hanamuramiyu.monban.config.file.FileMonbanConfigLoader;
import hanamuramiyu.monban.deployment.DeploymentMode;
import hanamuramiyu.monban.identity.PlayerIdentityResolver;
import hanamuramiyu.monban.identity.OfficialOnlineProfileResolver;
import hanamuramiyu.monban.storage.file.whitelist.FileWhitelistRepository;
import hanamuramiyu.monban.whitelist.WhitelistRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;

public final class MonbanBukkitPlugin extends JavaPlugin {
    private MonbanConfig config;
    private FileMonbanConfigLoader configLoader;
    private PlayerIdentityResolver identityResolver;
    private WhitelistRepository whitelistRepository;
    private AccessGrantAdministrationService accessGrantAdministrationService;
    private PlayerAccessService playerAccessService;
    private WhitelistPolicy whitelistPolicy;
    private OfficialOnlineProfileResolver profileResolver;

    @Override
    public void onEnable() {
        try {
            BukkitNativeWhitelistGuard.requireDisabledAtStartup(getServer());
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Failed to initialize monban. The native Minecraft whitelist remains enabled; disabling monban.",
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BukkitStartupAccessGuard startupGuard = new BukkitStartupAccessGuard();
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
            PlayerAccessService accessService = new PlayerAccessService(loadedConfig, resolver, grantLookup, runtimeWhitelistPolicy);
            loadedEntries = repository.findAll().size();

            this.config = loadedConfig;
            this.configLoader = loadedConfigLoader;
            this.identityResolver = resolver;
            this.whitelistRepository = repository;
            this.playerAccessService = accessService;
            this.profileResolver = onlineProfileResolver;
            this.whitelistPolicy = runtimeWhitelistPolicy;

            getServer().getPluginManager().registerEvents(new BukkitPlayerLoginListener(this, accessService), this);
            getServer().getPluginManager().registerEvents(new BukkitNativeWhitelistGuard(), this);

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
                    MonbanBukkitPlugin::validateNetworkAdministrativeScope
            );
            registerManagementCommand(administrationService);
            this.accessGrantAdministrationService = administrationService;
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "monban access enforcement is ready, but /monban command registration failed. "
                            + "Management commands will be unavailable.",
                    exception
            );
        }
    }

    @Override
    public void onDisable() {
        WhitelistRepository repository = this.whitelistRepository;

        this.playerAccessService = null;
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
        PluginCommand command = getCommand("monban");
        if (command == null) {
            throw new IllegalStateException("plugin.yml did not register the monban command.");
        }

        Executor mutationExecutor = task -> getServer().getScheduler().runTaskAsynchronously(this, task);
        Function<CommandSender, Executor> callbackExecutor = ignored -> task ->
                getServer().getScheduler().runTask(this, task);
        MonbanConfig loadedConfig = this.config;
        FileMonbanConfigLoader loadedConfigLoader = this.configLoader;
        WhitelistPolicy runtimeWhitelistPolicy = this.whitelistPolicy;
        if (loadedConfig == null || loadedConfigLoader == null || runtimeWhitelistPolicy == null) {
            throw new IllegalStateException("monban whitelist policy services are not initialized.");
        }
        BukkitWhitelistCommand whitelistCommand = new BukkitWhitelistCommand(
                administrationService,
                mutationExecutor,
                callbackExecutor,
                BukkitAdventureSender::send,
                getLogger(),
                profileResolver,
                runtimeWhitelistPolicy,
                enabled -> loadedConfigLoader.save(new MonbanConfig(
                        loadedConfig.deployment(),
                        new WhitelistSettings(enabled),
                        loadedConfig.identity()
                ))
        );
        BukkitMonbanCommand rootCommand = new BukkitMonbanCommand(whitelistCommand);
        command.setExecutor(rootCommand);
        command.setTabCompleter(rootCommand);
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
                    "Velocity deployment is not supported by the Bukkit/Spigot compatibility build. "
                            + "Install monban-velocity on the proxy and use /monban there; "
                            + "do not use the backend plugin as the network authority."
            );
        }
    }
}
