package hanamuramiyu.monban.config;

import java.util.Objects;

public record MonbanConfig(
        DeploymentSettings deployment,
        WhitelistSettings whitelist,
        IdentitySettings identity,
        BackendPermissionSettings backendPermissions
) {
    public MonbanConfig(
            DeploymentSettings deployment,
            WhitelistSettings whitelist,
            IdentitySettings identity
    ) {
        this(deployment, whitelist, identity, BackendPermissionSettings.defaults());
    }

    public MonbanConfig {
        Objects.requireNonNull(deployment, "deployment");
        Objects.requireNonNull(whitelist, "whitelist");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(backendPermissions, "backendPermissions");
    }

    public static MonbanConfig defaults() {
        return new MonbanConfig(
                DeploymentSettings.defaults(),
                WhitelistSettings.defaults(),
                IdentitySettings.defaults()
        );
    }
}
