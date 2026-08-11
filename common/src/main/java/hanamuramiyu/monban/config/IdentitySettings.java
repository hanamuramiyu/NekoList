package hanamuramiyu.monban.config;

import hanamuramiyu.monban.identity.IdentityResolutionMode;

import java.util.Objects;

public record IdentitySettings(
        IdentityResolutionMode mode,
        HybridIdentitySettings hybrid
) {
    public IdentitySettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(hybrid, "hybrid");
    }

    public static IdentitySettings defaults() {
        return new IdentitySettings(
                IdentityResolutionMode.AUTO,
                HybridIdentitySettings.defaults()
        );
    }
}
