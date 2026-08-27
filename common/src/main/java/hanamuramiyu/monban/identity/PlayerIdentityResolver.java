package hanamuramiyu.monban.identity;

import java.nio.charset.StandardCharsets;
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

    public PlayerIdentity resolveBackend(String name, UUID technicalUuid, boolean platformAuthenticated) {
        Objects.requireNonNull(technicalUuid, "technicalUuid");
        if (mode == IdentityResolutionMode.AUTO) {
            return isOfflineUuid(name, technicalUuid)
                    ? PlayerIdentity.offline(name, technicalUuid)
                    : PlayerIdentity.online(name, technicalUuid);
        }
        return resolve(name, technicalUuid, platformAuthenticated);
    }

    private static boolean isOfflineUuid(String name, UUID technicalUuid) {
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)
        );
        return offlineUuid.equals(technicalUuid);
    }
}
