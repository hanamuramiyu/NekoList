package hanamuramiyu.monban.config;

import java.util.Objects;

public record HybridIdentitySettings(
        boolean enabled,
        HybridIdentityPreference dualEntryPreference
) {
    public HybridIdentitySettings {
        Objects.requireNonNull(dualEntryPreference, "dualEntryPreference");
    }

    public static HybridIdentitySettings defaults() {
        return new HybridIdentitySettings(false, HybridIdentityPreference.ONLINE);
    }
}
