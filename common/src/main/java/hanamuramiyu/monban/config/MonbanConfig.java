package hanamuramiyu.monban.config;

import java.util.Objects;

public record MonbanConfig(
        DeploymentSettings deployment,
        WhitelistSettings whitelist,
        IdentitySettings identity
) {
    public MonbanConfig {
        Objects.requireNonNull(deployment, "deployment");
        Objects.requireNonNull(whitelist, "whitelist");
        Objects.requireNonNull(identity, "identity");
    }

    public static MonbanConfig defaults() {
        return new MonbanConfig(
                DeploymentSettings.defaults(),
                WhitelistSettings.defaults(),
                IdentitySettings.defaults()
        );
    }
}
