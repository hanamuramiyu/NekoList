package hanamuramiyu.monban.velocity.backend;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;

import java.util.Objects;

public final class VelocityBackendScopeValidator {
    private final ProxyServer server;
    private final ServerGroupCatalog serverGroupCatalog;

    public VelocityBackendScopeValidator(ProxyServer server, ServerGroupCatalog serverGroupCatalog) {
        this.server = Objects.requireNonNull(server, "server");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
    }

    public void validate(AccessScope scope, String subject) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(subject, "subject");

        switch (scope.type()) {
            case NETWORK -> throw new IllegalStateException(subject + " unexpectedly uses NETWORK scope.");
            case SERVER_GROUP -> validateServerGroup(scope.id().orElseThrow(), subject);
            case SERVER -> validateServer(scope.id().orElseThrow(), subject);
        }
    }

    private void validateServerGroup(String groupId, String subject) {
        if (serverGroupCatalog.findById(groupId).isEmpty()) {
            throw new VelocityBackendScopeValidationException(
                    subject + " references unknown server group: " + groupId + "."
            );
        }
    }

    private void validateServer(String configuredName, String subject) {
        RegisteredServer registeredServer = server.getServer(configuredName).orElseThrow(
                () -> new VelocityBackendScopeValidationException(
                        subject + " references backend that is not registered in Velocity: " + configuredName + "."
                )
        );
        String canonicalName = registeredServer.getServerInfo().getName();
        if (!configuredName.equals(canonicalName)) {
            throw new VelocityBackendScopeValidationException(
                    subject + " uses non-canonical backend name "
                            + configuredName + "; expected " + canonicalName + "."
            );
        }
    }
}
