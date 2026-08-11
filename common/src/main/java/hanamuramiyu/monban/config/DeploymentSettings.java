package hanamuramiyu.monban.config;

import hanamuramiyu.monban.deployment.DeploymentMode;

import java.util.Objects;

public record DeploymentSettings(DeploymentMode mode) {
    public DeploymentSettings {
        Objects.requireNonNull(mode, "mode");
    }

    public static DeploymentSettings defaults() {
        return new DeploymentSettings(DeploymentMode.STANDALONE);
    }
}
