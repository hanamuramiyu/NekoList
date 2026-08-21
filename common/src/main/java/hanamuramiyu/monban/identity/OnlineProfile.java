package hanamuramiyu.monban.identity;

import java.util.Objects;
import java.util.UUID;

public record OnlineProfile(String name, UUID verifiedUuid) {
    public OnlineProfile {
        if (!PlayerIdentity.isValidName(name)) {
            throw new IllegalArgumentException("Invalid Minecraft player name: " + name);
        }
        Objects.requireNonNull(verifiedUuid, "verifiedUuid");
    }

    public PlayerIdentity identity() {
        return PlayerIdentity.online(name, verifiedUuid);
    }
}
