package hanamuramiyu.monban.velocity.permission;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.permission.PermissionProvider;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.ServerGroupCatalog;
import hanamuramiyu.monban.velocity.session.VelocityConnectionIdentityRegistry;

import java.util.Objects;

public final class VelocityPermissionSetupListener {
    private final PlayerAccessResolver accessResolver;
    private final VelocityConnectionIdentityRegistry identityRegistry;
    private final ServerGroupCatalog serverGroupCatalog;

    public VelocityPermissionSetupListener(
            PlayerAccessResolver accessResolver,
            VelocityConnectionIdentityRegistry identityRegistry,
            ServerGroupCatalog serverGroupCatalog
    ) {
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.serverGroupCatalog = Objects.requireNonNull(serverGroupCatalog, "serverGroupCatalog");
    }

    @Subscribe
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        PermissionProvider delegate = event.getProvider();
        event.setProvider(new VelocityPermissionProvider(
                delegate,
                accessResolver,
                identityRegistry,
                serverGroupCatalog
        ));
    }
}
