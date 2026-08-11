package hanamuramiyu.monban.identity;

import java.util.Objects;
import java.util.UUID;

public final class PlayerIdentityResolver {
    private final IdentityResolutionMode mode;

    public PlayerIdentityResolver(IdentityResolutionMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public IdentityResolutionMode mode() {
        return mode;
    }

    public PlayerIdentity resolve(String name, UUID technicalUuid, boolean platformAuthenticated) {
        Objects.requireNonNull(technicalUuid, "technicalUuid");

        return switch (mode) {
            case AUTO -> platformAuthenticated
                    ? PlayerIdentity.online(name, technicalUuid)
                    : PlayerIdentity.offline(name, technicalUuid);
            case OFFLINE -> PlayerIdentity.offline(name, technicalUuid);
        };
    }
}
