package hanamuramiyu.monban.velocity.group;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.group.ServerGroupDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VelocityServerGroupCatalogResolver {
    private final ProxyServer server;

    public VelocityServerGroupCatalogResolver(ProxyServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public ServerGroupCatalog resolve(ServerGroupCatalog configuredCatalog) {
        Objects.requireNonNull(configuredCatalog, "configuredCatalog");

        List<ServerGroupDefinition> canonicalGroups = new ArrayList<>(configuredCatalog.findAll().size());
        for (ServerGroupDefinition group : configuredCatalog.findAll()) {
            List<String> canonicalServers = new ArrayList<>(group.servers().size());
            for (String configuredServerName : group.servers()) {
                RegisteredServer registeredServer = server.getServer(configuredServerName)
                        .orElseThrow(() -> new IllegalStateException(
                                "Unknown Velocity backend in server-groups.yml: " + configuredServerName
                        ));
                canonicalServers.add(registeredServer.getServerInfo().getName());
            }

            try {
                canonicalGroups.add(new ServerGroupDefinition(group.id(), canonicalServers));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Invalid canonical server membership for group " + group.id() + ": " + exception.getMessage(),
                        exception
                );
            }
        }

        try {
            return new ServerGroupCatalog(canonicalGroups);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid canonical server-group topology: " + exception.getMessage(),
                    exception
            );
        }
    }
}
