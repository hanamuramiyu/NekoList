package hanamuramiyu.monban.velocity.permission;

import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.effective.PlayerAccessSnapshot;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VelocityPermissionProvider implements PermissionProvider {
    private final PermissionProvider delegate;
    private final PlayerAccessResolver accessResolver;
    private final VelocityConnectionIdentityRegistry identityRegistry;
    private final ServerGroupCatalog serverGroupCatalog;

    public VelocityPermissionProvider(
            PermissionProvider delegate,
            PlayerAccessResolver accessResolver,
            VelocityConnectionIdentityRegistry identityRegistry,
            ServerGroupCatalog serverGroupCatalog
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
    }

    @Override
    public PermissionFunction createFunction(PermissionSubject subject) {
        Objects.requireNonNull(subject, "subject");
        PermissionFunction delegatedFunction = delegate.createFunction(subject);
        if (!(subject instanceof Player player)) {
            return delegatedFunction;
        }

        return node -> hasMonbanPermission(player, node)
                ? Tristate.TRUE
                : delegatedFunction.getPermissionValue(node);
    }

    private boolean hasMonbanPermission(Player player, String node) {
        if (node == null || node.isBlank()) {
            return false;
        }

        PlayerIdentity identity = identityRegistry.findActive(player).orElse(null);
        if (identity == null) {
            return false;
        }

        PlayerAccessSnapshot snapshot = accessResolver.resolve(identity);
        for (AccessScope scope : effectiveScopes(player)) {
            if (snapshot.hasPermission(scope, node)) {
                return true;
            }
        }
        return false;
    }

    private List<AccessScope> effectiveScopes(Player player) {
        List<AccessScope> scopes = new ArrayList<>();
        scopes.add(AccessScope.network());
        player.getCurrentServer().ifPresent(connection -> {
            String serverName = connection.getServerInfo().getName();
            scopes.add(AccessScope.server(serverName));
            serverGroupCatalog.findForServer(serverName)
                    .ifPresent(group -> scopes.add(AccessScope.serverGroup(group.id())));
        });
        return scopes;
    }
}
