package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.effective.PlayerAccessSnapshot;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.PlayerAccessStateReceiver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BackendPermissionService {
    private final PlayerAccessResolver accessResolver;
    private final ServerGroupCatalog serverGroupCatalog;
    private final String serverName;
    private final PlayerAccessStateReceiver stateReceiver;

    public BackendPermissionService(
            PlayerAccessResolver accessResolver,
            ServerGroupCatalog serverGroupCatalog,
            String serverName
    ) {
        this(accessResolver, serverGroupCatalog, serverName, null);
    }

    public BackendPermissionService(
            PlayerAccessResolver accessResolver,
            ServerGroupCatalog serverGroupCatalog,
            String serverName,
            PlayerAccessStateReceiver stateReceiver
    ) {
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
        this.serverName = requireServerName(serverName);
        this.stateReceiver = stateReceiver;
    }

    public List<String> resolveGrantedNodes(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        PlayerAccessSnapshot snapshot = stateReceiver == null
                ? accessResolver.resolve(identity)
                : stateReceiver.current()
                        .map(state -> accessResolver.resolve(state, identity))
                        .orElse(null);
        if (snapshot == null) {
            return List.of();
        }
        Set<String> nodes = new LinkedHashSet<>();
        for (AccessScope scope : effectiveScopes()) {
            snapshot.permissions(scope).forEach(permission -> nodes.add(permission.grant().node()));
        }
        return List.copyOf(nodes);
    }

    public boolean hasPermission(PlayerIdentity identity, String node) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(node, "node");
        return resolveGrantedNodes(identity).contains(node);
    }

    public String serverName() {
        return serverName;
    }

    private List<AccessScope> effectiveScopes() {
        List<AccessScope> scopes = new ArrayList<>();
        scopes.add(AccessScope.network());
        serverGroupCatalog.findForServer(serverName)
                .ifPresent(group -> scopes.add(AccessScope.serverGroup(group.id())));
        scopes.add(AccessScope.server(serverName));
        return scopes;
    }

    private static String requireServerName(String serverName) {
        Objects.requireNonNull(serverName, "serverName");
        if (serverName.isBlank() || !serverName.equals(serverName.strip())) {
            throw new IllegalArgumentException("Server name must be clean text: " + serverName);
        }
        return serverName;
    }
}
