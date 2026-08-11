package hanamuramiyu.monban.velocity.hybrid;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.config.HybridIdentityPreference;
import hanamuramiyu.monban.config.HybridIdentitySettings;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;
import java.util.UUID;

public final class VelocityHybridIdentitySelector {
    private final AccessGrantInventory inventory;
    private final HybridIdentitySettings settings;

    public VelocityHybridIdentitySelector(
            AccessGrantInventory inventory,
            HybridIdentitySettings settings
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public HybridIdentitySelection select(String username, UUID preLoginUuid) {
        Objects.requireNonNull(username, "username");

        boolean online = false;
        boolean offline = false;

        for (AccessGrant grant : inventory.findAll()) {
            PlayerIdentity identity = grant.identity();

            if (identity.type() == IdentityType.OFFLINE && identity.matchesName(username)) {
                offline = true;
            }

            if (identity.type() == IdentityType.ONLINE
                    && (identity.matchesName(username) || matchesPreLoginUuid(identity, preLoginUuid))) {
                online = true;
            }

            if (online && offline) {
                return preferenceSelection();
            }
        }

        if (online) {
            return HybridIdentitySelection.ONLINE;
        }
        return HybridIdentitySelection.OFFLINE;
    }

    private HybridIdentitySelection preferenceSelection() {
        return settings.dualEntryPreference() == HybridIdentityPreference.ONLINE
                ? HybridIdentitySelection.ONLINE
                : HybridIdentitySelection.OFFLINE;
    }

    private static boolean matchesPreLoginUuid(PlayerIdentity identity, UUID preLoginUuid) {
        return preLoginUuid != null
                && identity.verifiedUuid().filter(preLoginUuid::equals).isPresent();
    }
}
